import json
import mimetypes
import shutil
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Header, HTTPException, Query, Response, UploadFile
from pydantic import BaseModel, Field
from starlette.responses import StreamingResponse
from sqlmodel import Session, delete, select

from app.config import get_settings
from app.db.database import get_session
from app.models import Recording, Summary, Task, TranscriptSegment
from app.services.docx_export import build_docx
from app.services.export_names import safe_filename_part, transcript_filename
from app.services.file_service import content_hash

router = APIRouter(prefix="/api/recordings", tags=["recordings"])

# 单个上传文件最大 500 MB
MAX_UPLOAD_SIZE = 500 * 1024 * 1024
STREAM_CHUNK_SIZE = 1024 * 1024


class RecordingTagsUpdate(BaseModel):
    tags: list[str] = Field(default_factory=list, max_length=20)


class TranscriptSegmentUpdate(BaseModel):
    text: str = Field(min_length=1, max_length=20000)


class BatchRecordingRequest(BaseModel):
    recording_ids: list[str] = Field(min_length=1, max_length=200)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _normalize_tags(tags: list[str]) -> list[str]:
    cleaned: list[str] = []
    seen: set[str] = set()
    for tag in tags:
        value = tag.strip().strip("#")
        key = value.lower()
        if value and key not in seen:
            cleaned.append(value[:40])
            seen.add(key)
    return cleaned[:20]


def _tag_text(tags: list[str]) -> str:
    return ",".join(_normalize_tags(tags))


def _recording_library_dir() -> Path:
    settings = get_settings()
    watch_dir = settings.resolved_watch_dir
    if watch_dir is None or not watch_dir.is_dir():
        raise HTTPException(status_code=400, detail="请先在“目录监控”中设置可用的录音目录，再上传录音")
    return watch_dir


def _unique_target_path(directory: Path, filename: str, suffix: str) -> Path:
    raw_name = Path(filename or "recording").name
    stem = safe_filename_part(Path(raw_name).stem, "recording")
    target = directory / f"{stem}.{suffix}"
    index = 1
    while target.exists():
        target = directory / f"{stem}-{index}.{suffix}"
        index += 1
    return target


def _is_app_managed_original(path: Path) -> bool:
    data_recordings_dir = (get_settings().resolved_data_dir / "recordings").resolve()
    try:
        return path.resolve().is_relative_to(data_recordings_dir)
    except Exception:
        return False


@router.get("")
def list_recordings(
    query: str = Query("", max_length=120),
    tag: str = Query("", max_length=40),
    session: Session = Depends(get_session),
) -> list[Recording]:
    if not isinstance(query, str):
        query = ""
    if not isinstance(tag, str):
        tag = ""
    recordings = session.exec(select(Recording).order_by(Recording.created_at.desc())).all()
    normalized_query = query.strip().lower()
    normalized_tag = tag.strip().lower()
    if not normalized_query and not normalized_tag:
        return recordings

    segments_by_recording: dict[str, list[str]] = {}
    summaries_by_recording: dict[str, list[str]] = {}
    if normalized_query:
        for segment in session.exec(select(TranscriptSegment)).all():
            segments_by_recording.setdefault(segment.recording_id, []).append(segment.text)
        for summary in session.exec(select(Summary)).all():
            summaries_by_recording.setdefault(summary.recording_id, []).append(summary.content)

    matched: list[Recording] = []
    for recording in recordings:
        tags = [item.strip().lower() for item in recording.tags.split(",") if item.strip()]
        if normalized_tag and normalized_tag not in tags:
            continue
        if normalized_query:
            searchable = "\n".join(
                [
                    recording.filename,
                    recording.source_path or "",
                    recording.content_hash or "",
                    recording.tags,
                    *segments_by_recording.get(recording.id, []),
                    *summaries_by_recording.get(recording.id, []),
                ]
            ).lower()
            if normalized_query not in searchable:
                continue
        matched.append(recording)
    return matched


@router.post("")
async def upload_recording(
    file: UploadFile = File(...),
    session: Session = Depends(get_session),
) -> Recording:
    settings = get_settings()
    suffix = Path(file.filename or "").suffix.lower().lstrip(".")
    if suffix not in {"wav", "mp3", "m4a", "flac", "aac", "ogg"}:
        raise HTTPException(status_code=400, detail="不支持的音频格式，请上传 wav、mp3、m4a、flac、aac 或 ogg 文件")

    # 读取并检查文件大小
    content = await file.read()
    if len(content) > MAX_UPLOAD_SIZE:
        raise HTTPException(status_code=413, detail=f"文件过大，最大支持 {MAX_UPLOAD_SIZE // (1024 * 1024)} MB")

    library_dir = _recording_library_dir()
    temp_path = settings.resolved_data_dir / "recordings" / f".upload-{uuid4().hex}.tmp"
    temp_path.write_bytes(content)
    digest = content_hash(temp_path)
    existing = session.exec(select(Recording).where(Recording.content_hash == digest)).first()
    if existing is not None:
        temp_path.unlink(missing_ok=True)
        return existing

    recording = Recording(
        filename=file.filename or "recording",
        original_path="",
        format=suffix,
        content_hash=digest,
        file_size_bytes=len(content),
        source_type="upload",
        source_path="",
    )
    target = _unique_target_path(library_dir, file.filename or f"{recording.id}.{suffix}", suffix)
    shutil.move(str(temp_path), str(target))
    recording.original_path = str(target)
    recording.source_path = str(target)
    recording.source_mtime = target.stat().st_mtime
    session.add(recording)
    session.commit()
    session.refresh(recording)
    return recording


@router.patch("/{recording_id}/tags")
def update_recording_tags(
    recording_id: str,
    payload: RecordingTagsUpdate,
    session: Session = Depends(get_session),
) -> Recording:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    recording.tags = _tag_text(payload.tags)
    recording.updated_at = _now()
    session.add(recording)
    session.commit()
    session.refresh(recording)
    return recording


@router.patch("/{recording_id}/segments/{segment_id}")
def update_transcript_segment(
    recording_id: str,
    segment_id: str,
    payload: TranscriptSegmentUpdate,
    session: Session = Depends(get_session),
) -> TranscriptSegment:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    segment = session.get(TranscriptSegment, segment_id)
    if segment is None or segment.recording_id != recording_id:
        raise HTTPException(status_code=404, detail="Transcript segment not found")
    text = payload.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Transcript text cannot be empty")
    segment.text = text
    recording.updated_at = _now()
    session.add(segment)
    session.add(recording)
    try:
        session.flush()
        _write_transcript_json(session, recording_id)
        session.commit()
    except Exception:
        session.rollback()
        raise
    session.refresh(segment)
    return segment


@router.post("/batch-delete")
def delete_recordings_batch(
    payload: BatchRecordingRequest,
    session: Session = Depends(get_session),
) -> dict[str, object]:
    deleted: list[str] = []
    missing: list[str] = []
    for recording_id in payload.recording_ids:
        recording = session.get(Recording, recording_id)
        if recording is None:
            missing.append(recording_id)
            continue
        delete_recording(recording_id, session)
        deleted.append(recording_id)
    return {"deleted": deleted, "missing": missing}


@router.delete("/{recording_id}")
def delete_recording(recording_id: str, session: Session = Depends(get_session)) -> dict[str, str]:
    settings = get_settings()
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="录音不存在")

    # 删除关联数据
    session.exec(delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording_id))
    session.exec(delete(Summary).where(Summary.recording_id == recording_id))
    session.exec(delete(Task).where(Task.recording_id == recording_id))

    # 删除硬盘上的文件
    try:
        if recording.original_path:
            original_path = Path(recording.original_path)
            if _is_app_managed_original(original_path):
                original_path.unlink(missing_ok=True)
        if recording.normalized_path:
            Path(recording.normalized_path).unlink(missing_ok=True)
        for transcript_dir in {settings.resolved_transcript_dir, settings.resolved_data_dir / "transcripts"}:
            (transcript_dir / f"{recording_id}.json").unlink(missing_ok=True)
        for summary_dir in {settings.resolved_summary_dir, settings.resolved_data_dir / "summaries"}:
            for summary_file in summary_dir.glob(f"{recording_id}-*.md"):
                summary_file.unlink(missing_ok=True)
            for summary_file in summary_dir.glob(f"*{recording_id}.md"):
                summary_file.unlink(missing_ok=True)
    except Exception:
        pass  # 文件删除失败不影响数据库操作

    session.delete(recording)
    session.commit()
    return {"message": "删除成功"}


def _format_time(value: float | None) -> str:
    if value is None:
        return "--:--"
    total_seconds = max(0, int(round(value)))
    minutes = total_seconds // 60
    seconds = total_seconds % 60
    return f"{minutes:02d}:{seconds:02d}"


def _download_response(content: str, filename: str, media_type: str) -> Response:
    encoded = quote(filename)
    return Response(
        content=content,
        media_type=f"{media_type}; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{encoded}"},
    )


def _download_bytes_response(content: bytes, filename: str, media_type: str) -> Response:
    encoded = quote(filename)
    return Response(
        content=content,
        media_type=media_type,
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{encoded}"},
    )


def _transcript_payload(segments: list[TranscriptSegment]) -> list[dict[str, object]]:
    return [
        {
            "start_time": segment.start_time,
            "end_time": segment.end_time,
            "speaker": segment.speaker,
            "text": segment.text,
            "sequence": segment.sequence,
        }
        for segment in segments
    ]


def _write_transcript_json(session: Session, recording_id: str) -> Path:
    segments = session.exec(
        select(TranscriptSegment)
        .where(TranscriptSegment.recording_id == recording_id)
        .order_by(TranscriptSegment.sequence)
    ).all()
    transcript_path = get_settings().resolved_transcript_dir / f"{recording_id}.json"
    transcript_path.write_text(json.dumps(_transcript_payload(segments), ensure_ascii=False, indent=2), encoding="utf-8")
    return transcript_path


def _srt_timestamp(value: float | None) -> str:
    total_milliseconds = max(0, int(round((value or 0) * 1000)))
    milliseconds = total_milliseconds % 1000
    total_seconds = total_milliseconds // 1000
    seconds = total_seconds % 60
    total_minutes = total_seconds // 60
    minutes = total_minutes % 60
    hours = total_minutes // 60
    return f"{hours:02d}:{minutes:02d}:{seconds:02d},{milliseconds:03d}"


def _audio_media_type(path: Path) -> str:
    guessed_type, _ = mimetypes.guess_type(path.name)
    return guessed_type or "application/octet-stream"


def _parse_range_header(range_header: str | None, file_size: int) -> tuple[int, int, int]:
    if not isinstance(range_header, str):
        range_header = None
    if not range_header:
        return 0, max(0, file_size - 1), 200
    if not range_header.startswith("bytes=") or file_size <= 0:
        raise HTTPException(status_code=416, detail="Invalid range")

    range_value = range_header.removeprefix("bytes=").strip()
    if "," in range_value or "-" not in range_value:
        raise HTTPException(status_code=416, detail="Invalid range")

    start_raw, end_raw = range_value.split("-", 1)
    try:
        if start_raw == "":
            suffix_length = int(end_raw)
            if suffix_length <= 0:
                raise ValueError
            start = max(0, file_size - suffix_length)
            end = file_size - 1
        else:
            start = int(start_raw)
            end = int(end_raw) if end_raw else file_size - 1
    except ValueError as exc:
        raise HTTPException(status_code=416, detail="Invalid range") from exc

    if start < 0 or end < start or start >= file_size:
        raise HTTPException(status_code=416, detail="Invalid range")
    return start, min(end, file_size - 1), 206


def _iter_file_range(path: Path, start: int, end: int):
    with path.open("rb") as handle:
        handle.seek(start)
        remaining = end - start + 1
        while remaining > 0:
            chunk = handle.read(min(STREAM_CHUNK_SIZE, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            yield chunk


@router.get("/{recording_id}/audio")
def get_recording_audio(
    recording_id: str,
    range_header: str | None = Header(default=None, alias="Range"),
    session: Session = Depends(get_session),
) -> StreamingResponse:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="录音不存在")

    path = Path(recording.original_path)
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="音频文件不存在")

    file_size = path.stat().st_size
    start, end, status_code = _parse_range_header(range_header, file_size)
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1 if file_size else 0),
        "Content-Disposition": f"inline; filename*=UTF-8''{quote(path.name)}",
    }
    if status_code == 206:
        headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"

    return StreamingResponse(
        _iter_file_range(path, start, end),
        status_code=status_code,
        media_type=_audio_media_type(path),
        headers=headers,
    )


@router.get("/{recording_id}/exports/transcript")
def export_transcript(
    recording_id: str,
    format: str = Query("md", pattern="^(md|txt|json|srt|docx)$"),
    session: Session = Depends(get_session),
) -> Response:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="录音不存在")
    segments = session.exec(
        select(TranscriptSegment)
        .where(TranscriptSegment.recording_id == recording_id)
        .order_by(TranscriptSegment.sequence)
    ).all()
    if not segments:
        raise HTTPException(status_code=400, detail="还没有转写内容可导出")

    if format == "json":
        filename = transcript_filename(recording.filename, recording.updated_at or recording.created_at, "json")
        return _download_response(json.dumps(_transcript_payload(segments), ensure_ascii=False, indent=2), filename, "application/json")

    if format == "srt":
        lines: list[str] = []
        for index, segment in enumerate(segments, start=1):
            lines.extend(
                [
                    str(index),
                    f"{_srt_timestamp(segment.start_time)} --> {_srt_timestamp(segment.end_time)}",
                    segment.text,
                    "",
                ]
            )
        filename = transcript_filename(recording.filename, recording.updated_at or recording.created_at, "srt")
        return _download_response("\n".join(lines), filename, "application/x-subrip")

    if format == "docx":
        lines = [
            f"Status: {recording.status}",
            f"Duration: {_format_time(recording.duration_seconds)}",
            "",
        ]
        for segment in segments:
            lines.append(f"{_format_time(segment.start_time)} - {_format_time(segment.end_time)}")
            lines.append(segment.text)
            lines.append("")
        filename = transcript_filename(recording.filename, recording.updated_at or recording.created_at, "docx")
        return _download_bytes_response(
            build_docx(f"{recording.filename} Transcript", lines),
            filename,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )

    if format == "md":
        lines = [
            f"# {recording.filename} 转写",
            "",
            f"- 状态：{recording.status}",
            f"- 时长：{_format_time(recording.duration_seconds)}",
            f"- 大小：{recording.file_size_bytes or 0} bytes",
            "",
            "## 转写内容",
            "",
        ]
        for segment in segments:
            lines.extend(
                [
                    f"### {_format_time(segment.start_time)} - {_format_time(segment.end_time)}",
                    "",
                    segment.text,
                    "",
                ]
            )
        filename = transcript_filename(recording.filename, recording.updated_at or recording.created_at, "md")
        return _download_response("\n".join(lines), filename, "text/markdown")

    lines = [f"{recording.filename} 转写", f"时长：{_format_time(recording.duration_seconds)}", ""]
    for segment in segments:
        lines.append(f"[{_format_time(segment.start_time)} - {_format_time(segment.end_time)}] {segment.text}")
    filename = transcript_filename(recording.filename, recording.updated_at or recording.created_at, "txt")
    return _download_response("\n".join(lines), filename, "text/plain")


@router.get("/{recording_id}")
def get_recording(recording_id: str, session: Session = Depends(get_session)) -> dict[str, object]:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    segments = session.exec(
        select(TranscriptSegment)
        .where(TranscriptSegment.recording_id == recording_id)
        .order_by(TranscriptSegment.sequence)
    ).all()
    summaries = session.exec(select(Summary).where(Summary.recording_id == recording_id)).all()
    tasks = session.exec(select(Task).where(Task.recording_id == recording_id)).all()
    return {
        "recording": recording,
        "segments": segments,
        "summaries": summaries,
        "tasks": tasks,
    }
