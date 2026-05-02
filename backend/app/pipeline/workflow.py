import json
from datetime import datetime, timezone
from pathlib import Path

from sqlmodel import Session, delete, select

from app.db.database import engine
from app.models import Recording, Summary, Task, TranscriptSegment
from app.config import get_settings
from app.services.asr_service import ASRService
from app.services.audio_service import AudioService
from app.services.export_names import summary_filename
from app.services.summary_service import SUMMARY_TEMPLATES, SummaryService


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _update_task(session: Session, task: Task, **values: object) -> None:
    for key, value in values.items():
        setattr(task, key, value)
    task.updated_at = _now()
    session.add(task)
    session.commit()
    session.refresh(task)


def run_transcription_task(task_id: str) -> None:
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            return

        try:
            _update_task(session, task, status="running", progress=10, started_at=_now())
            recording.status = "normalizing"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()

            audio_service = AudioService()
            source = Path(recording.original_path)
            normalized = audio_service.normalize(source, recording.id)
            duration = audio_service.duration_seconds(normalized)

            recording.normalized_path = str(normalized)
            recording.duration_seconds = duration
            recording.status = "transcribing"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(session, task, progress=35)

            segments = ASRService().transcribe(normalized)
            session.exec(delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording.id))
            for index, segment in enumerate(segments):
                session.add(
                    TranscriptSegment(
                        recording_id=recording.id,
                        start_time=segment.start_time,
                        end_time=segment.end_time,
                        speaker=segment.speaker,
                        text=segment.text,
                        sequence=index,
                    )
                )
            transcript_path = get_settings().resolved_transcript_dir / f"{recording.id}.json"
            transcript_path.write_text(
                json.dumps([segment.__dict__ for segment in segments], ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

            recording.status = "transcribed"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(transcript_path),
                completed_at=_now(),
            )
        except Exception as exc:
            recording.status = "error"
            recording.error_message = str(exc)
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(
                session,
                task,
                status="error",
                error_message=str(exc),
                completed_at=_now(),
            )


def run_summary_task(task_id: str, mode: str = "summary") -> None:
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            return

        try:
            _update_task(session, task, status="running", progress=20, started_at=_now())
            segments = session.exec(
                select(TranscriptSegment)
                .where(TranscriptSegment.recording_id == recording.id)
                .order_by(TranscriptSegment.sequence)
            ).all()
            transcript = "\n".join(segment.text for segment in segments if segment.text.strip())
            if not transcript:
                raise RuntimeError("Transcript is empty. Run transcription first.")

            content = SummaryService().generate(transcript, mode=mode)
            summary = Summary(recording_id=recording.id, mode=mode, content=content)
            session.add(summary)

            template = SUMMARY_TEMPLATES.get(mode)
            template_name = str(template["name"]) if template else mode
            summary_path = get_settings().resolved_summary_dir / summary_filename(
                recording.filename,
                template_name,
                summary.created_at,
                "md",
                recording.id,
            )
            summary_path.write_text(content, encoding="utf-8")
            recording.status = "completed"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(summary_path),
                completed_at=_now(),
            )
        except Exception as exc:
            _update_task(
                session,
                task,
                status="error",
                error_message=str(exc),
                completed_at=_now(),
            )
