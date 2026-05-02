from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any

from app.config import get_settings


@dataclass
class Segment:
    start_time: float
    end_time: float
    text: str
    speaker: str = "speaker_1"


class ASRService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self._model: Any | None = None

    def package_available(self) -> bool:
        try:
            import funasr  # noqa: F401

            return True
        except Exception:
            return False

    def _load_model(self) -> Any:
        if self._model is not None:
            return self._model
        from funasr import AutoModel

        self._model = AutoModel(
            model=self.settings.asr_model,
            vad_model=self.settings.asr_vad_model,
            punc_model=self.settings.asr_punc_model,
            device=self.settings.asr_device,
            model_revision="master",
            vad_model_revision="master",
            punc_model_revision="master",
        )
        return self._model

    def transcribe(self, audio_path: Path) -> list[Segment]:
        if not self.package_available():
            raise RuntimeError("FunASR is not installed. Run backend dependency setup first.")

        model = self._load_model()
        result = model.generate(input=str(audio_path), batch_size_s=300)
        if not result:
            return []

        first = result[0] if isinstance(result, list) else result
        text = first.get("text", "") if isinstance(first, dict) else str(first)
        timestamps = first.get("timestamp", []) if isinstance(first, dict) else []

        if timestamps and isinstance(timestamps, list):
            segments = self._segments_from_text_and_timestamps(text, timestamps)
            if segments:
                return segments

        return [Segment(start_time=0, end_time=0, text=text)]

    def _segments_from_text_and_timestamps(
        self,
        text: str,
        timestamps: list[Any],
    ) -> list[Segment]:
        valid_timestamps = [
            item for item in timestamps if isinstance(item, (list, tuple)) and len(item) >= 2
        ]
        if not text.strip() or not valid_timestamps:
            return []

        chunks = self._split_text(text)
        segments: list[Segment] = []
        cursor = 0
        last_end = 0.0

        for chunk in chunks:
            timed_count = self._timed_char_count(chunk)
            if timed_count <= 0:
                if segments:
                    segments[-1].text += chunk
                continue

            start_index = min(cursor, len(valid_timestamps) - 1)
            end_index = min(cursor + timed_count - 1, len(valid_timestamps) - 1)
            start_ms = float(valid_timestamps[start_index][0])
            end_ms = float(valid_timestamps[end_index][1])
            start_time = max(last_end, start_ms / 1000)
            end_time = max(start_time, end_ms / 1000)

            segments.append(
                Segment(
                    start_time=round(start_time, 3),
                    end_time=round(end_time, 3),
                    text=chunk.strip(),
                )
            )
            cursor += timed_count
            last_end = end_time

        if cursor < len(valid_timestamps) and segments:
            segments[-1].end_time = round(float(valid_timestamps[-1][1]) / 1000, 3)

        return [segment for segment in segments if segment.text]

    def _split_text(self, text: str, max_chars: int = 120) -> list[str]:
        sentences = [
            item.strip()
            for item in re.findall(r".+?(?:[。！？!?；;]|$)", text)
            if item.strip()
        ]
        chunks: list[str] = []
        for sentence in sentences:
            if len(sentence) <= max_chars:
                chunks.append(sentence)
                continue
            for start in range(0, len(sentence), max_chars):
                chunk = sentence[start : start + max_chars].strip()
                if chunk:
                    chunks.append(chunk)
        return chunks

    def _timed_char_count(self, text: str) -> int:
        return sum(1 for char in text if not re.match(r"[\s，。！？!?；;：:、,.…—-]", char))
