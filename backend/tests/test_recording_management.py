import json
import tempfile
import unittest
from asyncio import run
from pathlib import Path

from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine

from app.api import recordings as recordings_api
from app.api.recordings import (
    RecordingTagsUpdate,
    TranscriptSegmentUpdate,
    export_transcript,
    list_recordings,
    upload_recording,
    update_recording_tags,
    update_transcript_segment,
)
from app.models import Recording, Summary, TranscriptSegment


def create_test_engine():
    return create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )


class FakeSettings:
    def __init__(self, root: Path) -> None:
        self.resolved_data_dir = root / "data"
        self.resolved_transcript_dir = root / "transcripts"
        self.resolved_watch_dir = root / "library"
        self.resolved_data_dir.mkdir(parents=True, exist_ok=True)
        (self.resolved_data_dir / "recordings").mkdir(parents=True, exist_ok=True)
        self.resolved_transcript_dir.mkdir(parents=True, exist_ok=True)
        self.resolved_watch_dir.mkdir(parents=True, exist_ok=True)


class FakeUploadFile:
    def __init__(self, filename: str, content: bytes) -> None:
        self.filename = filename
        self.content = content
        self.offset = 0
        self.read_sizes: list[int] = []

    async def read(self, size: int = -1) -> bytes:
        self.read_sizes.append(size)
        if size == -1:
            size = len(self.content) - self.offset
        chunk = self.content[self.offset : self.offset + size]
        self.offset += len(chunk)
        return chunk


class RecordingManagementTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_test_engine()
        SQLModel.metadata.create_all(self.engine)
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.old_get_settings = recordings_api.get_settings
        recordings_api.get_settings = lambda: FakeSettings(Path(self.temp_dir.name))
        self.addCleanup(lambda: setattr(recordings_api, "get_settings", self.old_get_settings))

    def seed_recording(self, session: Session) -> None:
        recording = Recording(
            id="rec-1",
            filename="meeting.mp3",
            original_path="C:/recordings/meeting.mp3",
            format="mp3",
            tags="planning",
        )
        session.add(recording)
        session.add(
            TranscriptSegment(
                id="seg-1",
                recording_id=recording.id,
                start_time=1.25,
                end_time=3.5,
                text="old transcript",
                sequence=0,
            )
        )
        session.add(Summary(recording_id=recording.id, mode="summary", content="budget review"))
        session.commit()

    def test_update_segment_persists_database_and_json(self) -> None:
        with Session(self.engine) as session:
            self.seed_recording(session)

            segment = update_transcript_segment(
                "rec-1",
                "seg-1",
                TranscriptSegmentUpdate(text="new transcript"),
                session=session,
            )

            self.assertEqual(segment.text, "new transcript")
            transcript_path = Path(self.temp_dir.name) / "transcripts" / "rec-1.json"
            payload = json.loads(transcript_path.read_text(encoding="utf-8"))
            self.assertEqual(payload[0]["text"], "new transcript")

    def test_update_segment_rolls_back_when_json_write_fails(self) -> None:
        blocked_path = Path(self.temp_dir.name) / "blocked"
        blocked_path.write_text("not a directory", encoding="utf-8")
        recordings_api.get_settings = lambda: type("BlockedSettings", (), {"resolved_transcript_dir": blocked_path})()

        with Session(self.engine) as session:
            self.seed_recording(session)

            with self.assertRaises(Exception):
                update_transcript_segment(
                    "rec-1",
                    "seg-1",
                    TranscriptSegmentUpdate(text="should not persist"),
                    session=session,
                )

            segment = session.get(TranscriptSegment, "seg-1")
            self.assertEqual(segment.text, "old transcript")

    def test_search_matches_transcripts_summaries_and_tags(self) -> None:
        with Session(self.engine) as session:
            self.seed_recording(session)

            result = list_recordings(query="old transcript", session=session)
            self.assertEqual([item.id for item in result.recordings], ["rec-1"])
            result = list_recordings(query="budget", session=session)
            self.assertEqual([item.id for item in result.recordings], ["rec-1"])
            result = list_recordings(tag="planning", session=session)
            self.assertEqual([item.id for item in result.recordings], ["rec-1"])

    def test_update_tags_normalizes_values(self) -> None:
        with Session(self.engine) as session:
            self.seed_recording(session)

            recording = update_recording_tags(
                "rec-1",
                RecordingTagsUpdate(tags=[" Project ", "#project", "Next"]),
                session=session,
            )

            self.assertEqual(recording.tags, "Project,Next")

    def test_export_transcript_supports_json_and_srt(self) -> None:
        with Session(self.engine) as session:
            self.seed_recording(session)

            json_response = export_transcript("rec-1", format="json", session=session)
            srt_response = export_transcript("rec-1", format="srt", session=session)

            self.assertIn(b"old transcript", json_response.body)
            self.assertIn(b"00:00:01,250 --> 00:00:03,500", srt_response.body)

    def test_upload_recording_reads_file_in_chunks(self) -> None:
        original_chunk_size = recordings_api.STREAM_CHUNK_SIZE
        recordings_api.STREAM_CHUNK_SIZE = 3
        self.addCleanup(lambda: setattr(recordings_api, "STREAM_CHUNK_SIZE", original_chunk_size))
        upload = FakeUploadFile("meeting.mp3", b"0123456789")

        with Session(self.engine) as session:
            recording = run(upload_recording(upload, session=session))

            self.assertEqual(recording.filename, "meeting.mp3")
            self.assertEqual(recording.file_size_bytes, 10)
            self.assertEqual(upload.read_sizes, [3, 3, 3, 3, 3])


if __name__ == "__main__":
    unittest.main()
