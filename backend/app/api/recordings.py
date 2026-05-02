import shutil
from pathlib import Path
from urllib.parse import quote
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Query, Response, UploadFile
from sqlmodel import Session, delete, select

from app.config import get_settings
from app.db.database import get_session
from app.models import Recording, Summary, Task, TranscriptSegment
from app.services.export_names import safe_filename_part, transcript_filename
from app.services.file_service import content_hash

router = APIRouter(prefix="/api/recordings", tags=["recordings"])

# 单个上传文件最大 500 MB
MAX_UPLOAD_SIZE = 500 * 1024 * 1024


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
def list_recordings(session: Session = Depends(get_session)) -> list[Recording]:
    return session.exec(select(Recording).order_by(Recording.created_at.desc())).all()


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


@router.get("/{recording_id}/exports/transcript")
def export_transcript(
    recording_id: str,
    format: str = Query("md", pattern="^(md|txt)$"),
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
