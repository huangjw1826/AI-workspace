import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException
from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine

from app.api.recordings import get_recording_audio
from app.models import Recording


def create_test_engine():
    return create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )


class RecordingAudioTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_test_engine()
        SQLModel.metadata.create_all(self.engine)
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)

    def test_get_recording_audio_returns_streaming_response(self) -> None:
        audio_path = Path(self.temp_dir.name) / "sample.mp3"
        audio_path.write_bytes(b"0123456789")

        with Session(self.engine) as session:
            session.add(
                Recording(
                    id="rec-1",
                    filename="sample.mp3",
                    original_path=str(audio_path),
                    format="mp3",
                )
            )
            session.commit()

            response = get_recording_audio("rec-1", session=session)

            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.headers["content-length"], "10")
            self.assertEqual(response.headers["accept-ranges"], "bytes")
            self.assertEqual(response.media_type, "audio/mpeg")

    def test_get_recording_audio_supports_byte_range(self) -> None:
        audio_path = Path(self.temp_dir.name) / "sample.wav"
        audio_path.write_bytes(b"0123456789")

        with Session(self.engine) as session:
            session.add(
                Recording(
                    id="rec-1",
                    filename="sample.wav",
                    original_path=str(audio_path),
                    format="wav",
                )
            )
            session.commit()

            response = get_recording_audio("rec-1", range_header="bytes=2-5", session=session)

            self.assertEqual(response.status_code, 206)
            self.assertEqual(response.headers["content-length"], "4")
            self.assertEqual(response.headers["content-range"], "bytes 2-5/10")
            self.assertEqual(response.media_type, "audio/wav")

    def test_get_recording_audio_rejects_missing_file(self) -> None:
        with Session(self.engine) as session:
            session.add(
                Recording(
                    id="rec-1",
                    filename="missing.mp3",
                    original_path=str(Path(self.temp_dir.name) / "missing.mp3"),
                    format="mp3",
                )
            )
            session.commit()

            with self.assertRaises(HTTPException) as error:
                get_recording_audio("rec-1", session=session)

            self.assertEqual(error.exception.status_code, 404)


if __name__ == "__main__":
    unittest.main()
