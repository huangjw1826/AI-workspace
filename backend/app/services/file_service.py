from hashlib import sha256
from pathlib import Path


SUPPORTED_AUDIO_EXTENSIONS = {"wav", "mp3", "m4a", "flac", "aac", "ogg"}


def audio_suffix(path: Path) -> str:
    return path.suffix.lower().lstrip(".")


def is_supported_audio(path: Path) -> bool:
    return audio_suffix(path) in SUPPORTED_AUDIO_EXTENSIONS


def content_hash(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
