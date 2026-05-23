import json
import subprocess
from pathlib import Path

from app.config import get_settings


class AudioService:
    def __init__(self) -> None:
        self.settings = get_settings()

    def normalize(self, source: Path, recording_id: str) -> Path:
        output = self.settings.resolved_data_dir / "normalized" / f"{recording_id}.wav"
        command = [
            self.settings.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-ac",
            "1",
            "-ar",
            "16000",
            str(output),
        ]
        subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
            timeout=self.settings.ffmpeg_timeout_seconds,
        )
        return output

    def duration_seconds(self, source: Path) -> float | None:
        try:
            import soundfile as sf

            return float(sf.info(str(source)).duration)
        except Exception:
            pass

        command = [
            self.settings.ffmpeg_bin,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "json",
            str(source),
        ]
        try:
            result = subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                timeout=self.settings.ffmpeg_timeout_seconds,
            )
            payload = json.loads(result.stdout)
            return float(payload["format"]["duration"])
        except Exception:
            return None

    def ffmpeg_available(self) -> bool:
        try:
            subprocess.run(
                [self.settings.ffmpeg_bin, "-version"],
                check=True,
                capture_output=True,
                text=True,
                timeout=10,
            )
            return True
        except Exception:
            return False
