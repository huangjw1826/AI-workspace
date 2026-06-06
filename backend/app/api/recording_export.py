"""
Transcript export endpoint — generates Markdown, TXT, JSON, SRT, and DOCX formats.

All formats read segments from the database ordered by sequence.
"""

import json
from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlmodel import Session, select

from app.db.database import get_session
from app.models import Recording, TranscriptSegment
from app.services.docx_export import build_docx
from app.services.export_names import transcript_filename

router = APIRouter(prefix="/api/recordings", tags=["export"])


# ---------------------------------------------------------------------------
# Format helpers
# ---------------------------------------------------------------------------

def _fmt_time(value: float | None) -> str:
    if value is None:
        return "--:--"
    total = max(0, int(round(value)))
    return f"{total // 60:02d}:{total % 60:02d}"


def _srt_timestamp(value: float | None) -> str:
    ms = max(0, int(round((value or 0) * 1000)))
    s = ms // 1000
    return f"{s // 3600:02d}:{(s // 60) % 60:02d}:{s % 60:02d},{ms % 1000:03d}"


def _segments_payload(segments: list[TranscriptSegment]) -> list[dict[str, object]]:
    return [
        {"start_time": s.start_time, "end_time": s.end_time, "speaker": s.speaker, "text": s.text, "sequence": s.sequence}
        for s in segments
    ]


def _download(content: str, filename: str, media_type: str) -> Response:
    return Response(
        content=content, media_type=f"{media_type}; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"},
    )


def _download_bytes(content: bytes, filename: str, media_type: str) -> Response:
    return Response(
        content=content, media_type=media_type,
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"},
    )


# ---------------------------------------------------------------------------
# Format renderers
# ---------------------------------------------------------------------------

def _render_json(segments: list[TranscriptSegment]) -> str:
    return json.dumps(_segments_payload(segments), ensure_ascii=False, indent=2)


def _render_srt(segments: list[TranscriptSegment]) -> str:
    lines: list[str] = []
    for i, s in enumerate(segments, 1):
        lines.extend([str(i), f"{_srt_timestamp(s.start_time)} --> {_srt_timestamp(s.end_time)}", s.text, ""])
    return "\n".join(lines)


def _render_md(recording: Recording, segments: list[TranscriptSegment]) -> str:
    lines = [
        f"# {recording.filename} 转写", "",
        f"- 状态：{recording.status}",
        f"- 时长：{_fmt_time(recording.duration_seconds)}",
        f"- 大小：{recording.file_size_bytes or 0} bytes", "",
        "## 转写内容", "",
    ]
    for s in segments:
        lines.extend([f"### {_fmt_time(s.start_time)} - {_fmt_time(s.end_time)}", "", s.text, ""])
    return "\n".join(lines)


def _render_txt(recording: Recording, segments: list[TranscriptSegment]) -> str:
    lines = [f"{recording.filename} 转写", f"时长：{_fmt_time(recording.duration_seconds)}", ""]
    for s in segments:
        lines.append(f"[{_fmt_time(s.start_time)} - {_fmt_time(s.end_time)}] {s.text}")
    return "\n".join(lines)


def _render_docx(recording: Recording, segments: list[TranscriptSegment]) -> bytes:
    lines = [f"Status: {recording.status}", f"Duration: {_fmt_time(recording.duration_seconds)}", ""]
    for s in segments:
        lines.append(f"{_fmt_time(s.start_time)} - {_fmt_time(s.end_time)}")
        lines.append(s.text)
        lines.append("")
    return build_docx(f"{recording.filename} Transcript", lines)


# ---------------------------------------------------------------------------
# Export dispatch
# ---------------------------------------------------------------------------

FORMAT_HANDLERS: dict[str, str] = {
    "json": "application/json",
    "srt":  "application/x-subrip",
    "md":   "text/markdown",
    "txt":  "text/plain",
    "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
}


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

    timestamp = recording.updated_at or recording.created_at

    if format == "json":
        return _download(_render_json(segments), transcript_filename(recording.filename, timestamp, "json"), "application/json")
    if format == "srt":
        return _download(_render_srt(segments), transcript_filename(recording.filename, timestamp, "srt"), "application/x-subrip")
    if format == "docx":
        return _download_bytes(_render_docx(recording, segments), transcript_filename(recording.filename, timestamp, "docx"), FORMAT_HANDLERS["docx"])
    if format == "md":
        return _download(_render_md(recording, segments), transcript_filename(recording.filename, timestamp, "md"), "text/markdown")
    # txt (default fallback)
    return _download(_render_txt(recording, segments), transcript_filename(recording.filename, timestamp, "txt"), "text/plain")
