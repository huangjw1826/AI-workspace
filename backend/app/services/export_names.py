import re
from datetime import datetime
from pathlib import Path


INVALID_FILENAME_RE = re.compile(r'[<>:"/\\|?*\x00-\x1f]+')
WHITESPACE_RE = re.compile(r"\s+")


def safe_filename_part(value: str | None, fallback: str = "录音") -> str:
    cleaned = INVALID_FILENAME_RE.sub("_", value or "")
    cleaned = WHITESPACE_RE.sub(" ", cleaned).strip(" ._")
    return cleaned or fallback


def recording_stem(filename: str | None, fallback: str = "录音") -> str:
    return safe_filename_part(Path(filename or fallback).stem, fallback)


def filename_timestamp(value: datetime | None) -> str:
    timestamp = value or datetime.now().astimezone()
    return timestamp.astimezone().strftime("%Y%m%d-%H%M%S")


def transcript_filename(recording_filename: str | None, created_at: datetime | None, extension: str) -> str:
    stem = recording_stem(recording_filename)
    return f"{stem}_转写_{filename_timestamp(created_at)}.{extension}"


def summary_filename(
    recording_filename: str | None,
    template_name: str | None,
    created_at: datetime | None,
    extension: str,
    unique_suffix: str | None = None,
) -> str:
    stem = recording_stem(recording_filename)
    template = safe_filename_part(template_name, "摘要")
    suffix = f"_{safe_filename_part(unique_suffix, '')}" if unique_suffix else ""
    return f"{stem}_摘要_{template}_{filename_timestamp(created_at)}{suffix}.{extension}"
