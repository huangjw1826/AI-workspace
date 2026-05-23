import unittest
from datetime import datetime, timezone

from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine, select

from app.models import Recording, Task, TranscriptSegment
from app.services.task_service import (
    cancel_task,
    create_or_get_task,
    recover_interrupted_tasks,
)


def create_test_engine():
    return create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )


class TaskServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_test_engine()
        SQLModel.metadata.create_all(self.engine)

    def test_recover_interrupted_tasks_marks_active_tasks_as_errors(self) -> None:
        with Session(self.engine) as session:
            recording = Recording(
                id="rec-1",
                filename="meeting.mp3",
                original_path="C:/recordings/meeting.mp3",
                format="mp3",
                status="transcribing",
            )
            task = Task(
                id="task-1",
                recording_id=recording.id,
                task_type="transcription",
                status="running",
                progress=35,
                started_at=datetime.now(timezone.utc),
            )
            session.add(recording)
            session.add(task)
            session.commit()

            recovered = recover_interrupted_tasks(session)

            updated_task = session.get(Task, task.id)
            updated_recording = session.get(Recording, recording.id)
            self.assertEqual(recovered, 1)
            self.assertEqual(updated_task.status, "error")
            self.assertEqual(updated_task.progress, 35)
            self.assertIsNotNone(updated_task.completed_at)
            self.assertIn("interrupted", updated_task.error_message.lower())
            self.assertEqual(updated_recording.status, "error")
            self.assertIn("interrupted", updated_recording.error_message.lower())

    def test_create_or_get_task_reuses_active_task_for_same_recording(self) -> None:
        with Session(self.engine) as session:
            recording = Recording(
                id="rec-1",
                filename="meeting.mp3",
                original_path="C:/recordings/meeting.mp3",
                format="mp3",
                status="queued",
            )
            active_task = Task(
                id="task-1",
                recording_id=recording.id,
                task_type="transcription",
                status="queued",
            )
            session.add(recording)
            session.add(active_task)
            session.commit()

            task, created = create_or_get_task(session, recording, "transcription")

            tasks = session.exec(select(Task)).all()
            self.assertFalse(created)
            self.assertEqual(task.id, active_task.id)
            self.assertEqual(len(tasks), 1)

    def test_recover_summary_task_preserves_transcribed_recording(self) -> None:
        with Session(self.engine) as session:
            recording = Recording(
                id="rec-1",
                filename="meeting.mp3",
                original_path="C:/recordings/meeting.mp3",
                format="mp3",
                status="completed",
            )
            session.add(recording)
            session.add(
                TranscriptSegment(
                    recording_id=recording.id,
                    start_time=0,
                    end_time=1,
                    text="hello",
                )
            )
            session.add(
                Task(
                    id="task-1",
                    recording_id=recording.id,
                    task_type="summary:structured_summary",
                    status="running",
                )
            )
            session.commit()

            recover_interrupted_tasks(session)

            updated_recording = session.get(Recording, recording.id)
            self.assertEqual(updated_recording.status, "transcribed")

    def test_cancel_task_marks_task_cancelled_and_restores_recording(self) -> None:
        with Session(self.engine) as session:
            recording = Recording(
                id="rec-1",
                filename="meeting.mp3",
                original_path="C:/recordings/meeting.mp3",
                format="mp3",
                status="queued",
            )
            task = Task(
                id="task-1",
                recording_id=recording.id,
                task_type="transcription",
                status="queued",
            )
            session.add(recording)
            session.add(task)
            session.commit()

            cancelled = cancel_task(session, task.id)

            updated_recording = session.get(Recording, recording.id)
            self.assertEqual(cancelled.status, "cancelled")
            self.assertEqual(cancelled.error_message, "Task was cancelled by user.")
            self.assertIsNotNone(cancelled.completed_at)
            self.assertEqual(updated_recording.status, "uploaded")


if __name__ == "__main__":
    unittest.main()
