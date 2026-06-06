"""
Audio streaming endpoint with HTTP Range request support.

Supports seeking within audio files, used by the HTML5 audio player
to enable arbitrary-position playback without downloading the entire file.
"""

import mimetypes
from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response
from starlette.responses import StreamingResponse
from sqlmodel import Session, select

from app.db.database import get_session
from app.models import Recording

router = APIRouter(prefix="/api/recordings", tags=["audio"])

STREAM_CHUNK_SIZE = 1024 * 1024  # 1 MB


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _media_type(path: Path) -> str:
    guessed, _ = mimetypes.guess_type(path.name)
    return guessed or "application/octet-stream"


def _parse_range(range_header: str | None, file_size: int) -> tuple[int, int, int]:
    """Parse HTTP Range header, return (start, end, status_code)."""
    if not range_header or not range_header.startswith("bytes=") or file_size <= 0:
        return 0, max(0, file_size - 1), 200

    value = range_header.removeprefix("bytes=").strip()
    if "," in value or "-" not in value:
        raise HTTPException(status_code=416, detail="Invalid range")

    start_raw, end_raw = value.split("-", 1)
    try:
        if start_raw == "":
            suffix = int(end_raw)
            if suffix <= 0:
                raise ValueError
            start = max(0, file_size - suffix)
            end = file_size - 1
        else:
            start = int(start_raw)
            end = int(end_raw) if end_raw else file_size - 1
    except ValueError as exc:
        raise HTTPException(status_code=416, detail="Invalid range") from exc

    if start < 0 or end < start or start >= file_size:
        raise HTTPException(status_code=416, detail="Invalid range")
    return start, min(end, file_size - 1), 206


def _iter_range(path: Path, start: int, end: int):
    """Yield file chunks within [start, end] byte range."""
    with path.open("rb") as f:
        f.seek(start)
        remaining = end - start + 1
        while remaining > 0:
            chunk = f.read(min(STREAM_CHUNK_SIZE, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            yield chunk


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------

@router.get("/{recording_id}/audio")
def get_recording_audio(
    recording_id: str,
    range_header: str | None = Header(default=None, alias="Range"),
    session: Session = Depends(get_session),
) -> StreamingResponse:
    """Stream audio file with HTTP Range support for seeking."""
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="录音不存在")

    path = Path(recording.original_path)
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="音频文件不存在")

    file_size = path.stat().st_size
    start, end, status = _parse_range(range_header, file_size)

    headers: dict[str, str] = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1 if file_size else 0),
        "Content-Disposition": f"inline; filename*=UTF-8''{quote(path.name)}",
    }
    if status == 206:
        headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"

    return StreamingResponse(
        _iter_range(path, start, end),
        status_code=status,
        media_type=_media_type(path),
        headers=headers,
    )
