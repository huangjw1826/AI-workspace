"""
Recording CRUD — list, upload, get, delete, batch-delete, tags, segment editing.
"""

import json
import os
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from pydantic import BaseModel, Field
from sqlmodel import Session, delete, select

from app.config import get_settings
from app.db.database import get_session
from app.models import Recording, Summary, Task, TranscriptSegment
from app.services.export_names import safe_filename_part
from app.services.file_service import content_hash, file_creation_time
from app.services.audio_service import AudioService

router = APIRouter(prefix="/api/recordings", tags=["recordings"])

MAX_UPLOAD_SIZE = 500 * 1024 * 1024  # 500 MB
STREAM_CHUNK_SIZE = 1024 * 1024      # 1 MB
MATCH_SNIPPET_LENGTH = 80


# ---------------------------------------------------------------------------
# Request models
# ---------------------------------------------------------------------------

class RecordingTagsUpdate(BaseModel):
    tags: list[str] = Field(default_factory=list, max_length=20)


class SearchResult(BaseModel):
    recordings: list[Recording] = Field(default_factory=list)
    match_previews: dict[str, list[str]] = Field(default_factory=dict)


class TranscriptSegmentUpdate(BaseModel):
    text: str = Field(min_length=1, max_length=20000)


class BatchRecordingRequest(BaseModel):
    recording_ids: list[str] = Field(min_length=1, max_length=200)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

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


def _library_dir() -> Path:
    settings = get_settings()
    watch_dir = settings.resolved_watch_dir
    if watch_dir is None or not watch_dir.is_dir():
        raise HTTPException(status_code=400, detail='请先在"目录监控"中设置可用的录音目录，再上传录音')
    return watch_dir


def _unique_path(directory: Path, filename: str, suffix: str) -> Path:
    stem = safe_filename_part(Path(filename or "recording").stem, "recording")
    target = directory / f"{stem}.{suffix}"
    idx = 1
    while target.exists():
        target = directory / f"{stem}-{idx}.{suffix}"
        idx += 1
    return target


def _is_managed_original(path: Path) -> bool:
    data_dir = (get_settings().resolved_data_dir / "recordings").resolve()
    try:
        return path.resolve().is_relative_to(data_dir)
    except Exception:
        return False


def _segments_payload(segments: list[TranscriptSegment]) -> list[dict[str, object]]:
    return [
        {"start_time": s.start_time, "end_time": s.end_time, "speaker": s.speaker, "text": s.text, "sequence": s.sequence}
        for s in segments
    ]


def _write_transcript_json(session: Session, recording_id: str) -> Path:
    segments = session.exec(
        select(TranscriptSegment)
        .where(TranscriptSegment.recording_id == recording_id)
        .order_by(TranscriptSegment.sequence)
    ).all()
    path = get_settings().resolved_transcript_dir / f"{recording_id}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(_segments_payload(segments), ensure_ascii=False, indent=2)

    # Atomic write with retries — handles cloud sync file locks
    tmp = path.with_suffix(f".__tmp_{uuid4().hex[:8]}__")
    for attempt in range(8):
        try:
            tmp.write_text(content, encoding="utf-8")
            break
        except OSError:
            if attempt == 7:
                raise
            time.sleep(0.75)
    for attempt in range(8):
        try:
            os.replace(str(tmp), str(path))
            return path
        except OSError:
            if attempt == 7:
                raise
            time.sleep(0.75)
    return path


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.get("")
def list_recordings(
    query: str = Query("", max_length=120),
    tag: str = Query("", max_length=40),
    session: Session = Depends(get_session),
) -> SearchResult:
    """List recordings with optional full-text search and tag filter."""
    recordings = session.exec(select(Recording).order_by(Recording.created_at.desc())).all()
    q = query.strip().lower()
    t = tag.strip().lower()
    if not q and not t:
        return SearchResult(recordings=list(recordings), match_previews={})

    # Preload segments and summaries for full-text search
    seg_map: dict[str, list[str]] = {}
    sum_map: dict[str, list[str]] = {}
    if q:
        for seg in session.exec(select(TranscriptSegment)).all():
            seg_map.setdefault(seg.recording_id, []).append(seg.text)
        for s in session.exec(select(Summary)).all():
            sum_map.setdefault(s.recording_id, []).append(s.content)

    matched: list[Recording] = []
    previews: dict[str, list[str]] = {}
    for r in recordings:
        tags = [x.strip().lower() for x in r.tags.split(",") if x.strip()]
        if t and t not in tags:
            continue
        if q:
            fields: list[tuple[str, str]] = [("filename", r.filename), ("tags", r.tags)]
            for text in seg_map.get(r.id, []):
                fields.append(("transcript", text))
            for text in sum_map.get(r.id, []):
                fields.append(("summary", text))
            haystack = "\n".join(v for _, v in fields).lower()
            if q not in haystack:
                continue
            # Build snippet previews
            snippets: list[str] = []
            for field_name, text in fields:
                pos = text.lower().find(q)
                if pos >= 0:
                    start = max(0, pos - MATCH_SNIPPET_LENGTH)
                    end = min(len(text), pos + len(q) + MATCH_SNIPPET_LENGTH)
                    snippet = text[start:end].strip()
                    if start > 0:
                        snippet = "..." + snippet
                    if end < len(text):
                        snippet += "..."
                    snippets.append(f"[{field_name}] {snippet}")
            if snippets:
                previews[r.id] = snippets[:5]
        matched.append(r)

    return SearchResult(recordings=matched, match_previews=previews)


@router.post("")
async def upload_recording(
    file: UploadFile = File(...),
    session: Session = Depends(get_session),
) -> Recording:
    """Upload an audio file (max 500 MB, 6 supported formats)."""
    suffix = Path(file.filename or "").suffix.lower().lstrip(".")
    if suffix not in {"wav", "mp3", "m4a", "flac", "aac", "ogg"}:
        raise HTTPException(status_code=400, detail="不支持的音频格式，请上传 wav、mp3、m4a、flac、aac 或 ogg 文件")

    settings = get_settings()
    library_dir = _library_dir()
    temp_path = settings.resolved_data_dir / "recordings" / f".upload-{uuid4().hex}.tmp"
    temp_path.parent.mkdir(parents=True, exist_ok=True)
    bytes_written = 0
    try:
        with temp_path.open("wb") as out:
            while True:
                chunk = await file.read(STREAM_CHUNK_SIZE)
                if not chunk:
                    break
                bytes_written += len(chunk)
                if bytes_written > MAX_UPLOAD_SIZE:
                    raise HTTPException(status_code=413, detail=f"文件过大，最大支持 {MAX_UPLOAD_SIZE // (1024 * 1024)} MB")
                out.write(chunk)

        digest = content_hash(temp_path)
        existing = session.exec(select(Recording).where(Recording.content_hash == digest)).first()
        if existing is not None:
            temp_path.unlink(missing_ok=True)
            return existing
    except Exception:
        temp_path.unlink(missing_ok=True)
        raise

    target = _unique_path(library_dir, file.filename or f"upload.{suffix}", suffix)
    shutil.move(str(temp_path), str(target))

    # Extract audio duration at ingest time
    duration = AudioService().duration_seconds(target)

    recording = Recording(
        filename=file.filename or "recording", original_path=str(target), format=suffix,
        content_hash=digest, file_size_bytes=bytes_written, source_type="upload",
        source_path=str(target), source_mtime=target.stat().st_mtime,
        duration_seconds=duration,
        created_at=file_creation_time(target),
    )
    session.add(recording)
    session.commit()
    session.refresh(recording)

    # Notify connected clients of new recording
    from app.services.sse_service import get_sse_service
    sse = await get_sse_service()
    await sse.emit_recording_created(recording.id, recording.filename)

    return recording


@router.patch("/{recording_id}/tags")
def update_recording_tags(
    recording_id: str, payload: RecordingTagsUpdate, session: Session = Depends(get_session),
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
    recording_id: str, segment_id: str, payload: TranscriptSegmentUpdate, session: Session = Depends(get_session),
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
    payload: BatchRecordingRequest, session: Session = Depends(get_session),
) -> dict[str, object]:
    deleted: list[str] = []
    missing: list[str] = []
    for rid in payload.recording_ids:
        rec = session.get(Recording, rid)
        if rec is None:
            missing.append(rid)
            continue
        _delete_one(rid, session)
        deleted.append(rid)
    return {"deleted": deleted, "missing": missing}


@router.delete("/{recording_id}")
def delete_recording(recording_id: str, session: Session = Depends(get_session)) -> dict[str, str]:
    rec = session.get(Recording, recording_id)
    if rec is None:
        raise HTTPException(status_code=404, detail="录音不存在")
    _delete_one(recording_id, session)
    return {"message": "删除成功"}


def _delete_one(recording_id: str, session: Session) -> None:
    """Delete a recording and all associated data (segments, summaries, tasks, files)."""
    settings = get_settings()
    recording = session.get(Recording, recording_id)
    if recording is None:
        return

    # Cascade delete DB records
    session.exec(delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording_id))
    session.exec(delete(Summary).where(Summary.recording_id == recording_id))
    session.exec(delete(Task).where(Task.recording_id == recording_id))

    # Clean up disk files (best-effort)
    try:
        if recording.original_path:
            p = Path(recording.original_path)
            if _is_managed_original(p):
                p.unlink(missing_ok=True)
        if recording.normalized_path:
            Path(recording.normalized_path).unlink(missing_ok=True)
        for d in {settings.resolved_transcript_dir, settings.resolved_data_dir / "transcripts"}:
            (d / f"{recording_id}.json").unlink(missing_ok=True)
        for d in {settings.resolved_summary_dir, settings.resolved_data_dir / "summaries"}:
            for f in list(d.glob(f"{recording_id}-*.md")) + list(d.glob(f"*{recording_id}.md")):
                f.unlink(missing_ok=True)
    except Exception:
        pass

    session.delete(recording)
    session.commit()


class ResyncResult(BaseModel):
    total: int = 0
    updated: int = 0
    missing: int = 0
    errors: int = 0
    relocated: int = 0
    details: list[str] = Field(default_factory=list)


def _find_file_by_hash(parent_dirs: list[Path], stored_hash: str | None) -> Path | None:
    """Search candidate directories for a file matching the given content_hash.

    Scans each directory recursively (files only) and computes SHA-256 until a match
    is found. Returns the first matching path, or None.
    """
    if not stored_hash:
        return None
    for d in parent_dirs:
        if not d.exists() or not d.is_dir():
            continue
        for f in d.rglob("*"):  # noqa: F821
            if not f.is_file():
                continue
            try:
                if content_hash(f) == stored_hash:
                    return f
            except Exception:
                continue
    return None


@router.post("/resync")
def resync_all_recordings(
    session: Session = Depends(get_session),
) -> ResyncResult:
    """Re-sync all recordings' file metadata from disk.

    For each recording, tries to locate the original file (handles renames):
    1. Try the stored original_path first
    2. If not found, scan parent directories and watch_dir for matching content_hash

    Updates: filename, format, file_size_bytes, source_mtime, content_hash,
             duration_seconds, original_path (if relocated).
    """
    settings = get_settings()
    recordings = session.exec(select(Recording)).all()
    result = ResyncResult(total=len(recordings))
    audio = AudioService()

    # Collect candidate search directories: watch_dir + all parent dirs of original_paths
    candidate_dirs: list[Path] = []
    watch_dir = settings.resolved_watch_dir
    if watch_dir and watch_dir.is_dir():
        candidate_dirs.append(watch_dir)
    for rec in recordings:
        if rec.original_path:
            parent = Path(rec.original_path).parent
            if parent.exists() and parent.is_dir() and parent not in candidate_dirs:
                candidate_dirs.append(parent)

    for rec in recordings:
        if not rec.original_path:
            result.missing += 1
            result.details.append(f"❌ {rec.filename}: 缺少原始路径")
            continue

        path = Path(rec.original_path)
        relocated = False

        # -- Step 1: try stored path --
        if not path.exists() or not path.is_file():
            # -- Step 2: search candidate dirs by content_hash --
            found = _find_file_by_hash(candidate_dirs, rec.content_hash)
            if found is not None:
                path = found
                relocated = True
            else:
                result.missing += 1
                result.details.append(f"❌ {rec.filename}: 文件不存在且无法定位 (曾位于 {rec.original_path})")
                continue

        # -- Step 3: sync metadata --
        try:
            stat = path.stat()
            digest = content_hash(path)
            duration = audio.duration_seconds(path)

            rec.filename = path.name
            rec.format = path.suffix.lower().lstrip(".")
            rec.file_size_bytes = stat.st_size
            rec.source_mtime = stat.st_mtime
            rec.content_hash = digest
            rec.duration_seconds = duration
            rec.original_path = str(path)
            rec.source_path = str(path)
            rec.updated_at = datetime.now(timezone.utc)
            rec.created_at = file_creation_time(path)

            session.add(rec)
            result.updated += 1
            if relocated:
                result.relocated += 1
                result.details.append(f"🔁 {rec.filename}: 已重定位 → {path.name}")
            elif rec.filename != path.name:
                result.details.append(f"✅ {rec.filename}: 已同步 (原名: {path.name})")
            else:
                dur_str = f"{duration:.0f}s" if duration else "未知"
                result.details.append(f"✅ {rec.filename}: 已同步 · 时长 {dur_str}")
        except Exception as exc:
            result.errors += 1
            result.details.append(f"⚠️ {rec.filename}: {exc}")

    session.commit()
    return result


@router.get("/{recording_id}")
def get_recording(recording_id: str, session: Session = Depends(get_session)) -> dict[str, object]:
    """Get recording detail with segments, summaries, and tasks."""
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")

    segments = session.exec(
        select(TranscriptSegment).where(TranscriptSegment.recording_id == recording_id).order_by(TranscriptSegment.sequence)
    ).all()
    summaries = session.exec(
        select(Summary).where(Summary.recording_id == recording_id).order_by(Summary.created_at.desc())
    ).all()
    tasks = session.exec(select(Task).where(Task.recording_id == recording_id)).all()

    # Auto-promote status if summaries exist
    if summaries and recording.status != "completed":
        recording.status = "completed"
        recording.updated_at = datetime.now(timezone.utc)
        session.add(recording)
        session.commit()
        session.refresh(recording)

    return {"recording": recording, "segments": segments, "summaries": summaries, "tasks": tasks}
