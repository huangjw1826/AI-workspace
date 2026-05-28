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
from app.services.runtime_log import get_logger
from app.services.summary_service import SUMMARY_TEMPLATES, SummaryService

logger = get_logger()

_sse_service = None


async def _get_sse_service():
    global _sse_service
    if _sse_service is None:
        from app.services.sse_service import get_sse_service as _get_sse
        _sse_service = await _get_sse()
    return _sse_service


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _update_task(session: Session, task: Task, **values: object) -> None:
    for key, value in values.items():
        setattr(task, key, value)
    task.updated_at = _now()
    session.add(task)
    session.commit()
    session.refresh(task)


def _is_cancelled(session: Session, task: Task) -> bool:
    session.refresh(task)
    return task.status == "cancelled"


def _finish_cancelled(session: Session, task: Task, recording: Recording | None = None) -> None:
    task.completed_at = task.completed_at or _now()
    task.updated_at = _now()
    session.add(task)
    if recording is not None and recording.status in {"queued", "normalizing", "transcribing"}:
        recording.status = "uploaded"
        recording.updated_at = _now()
        session.add(recording)
    session.commit()


def _run_async_task(coro):
    """
    Safely run an async task from a synchronous context.
    Creates a new event loop if none exists or the existing one is closed.
    """
    import asyncio
    import threading

    def get_or_create_loop():
        try:
            loop = asyncio.get_event_loop()
            if not loop.is_closed():
                return loop
        except RuntimeError:
            pass
        new_loop = asyncio.new_event_loop()
        asyncio.set_event_loop(new_loop)
        return new_loop

    try:
        loop = get_or_create_loop()
        if loop.is_running():
            def run_in_thread():
                new_loop = None
                try:
                    new_loop = asyncio.new_event_loop()
                    asyncio.set_event_loop(new_loop)
                    new_loop.run_until_complete(coro)
                except Exception as e:
                    logger.exception("Async task failed in background thread", exc_info=e)
                finally:
                    if new_loop is not None and not new_loop.is_closed():
                        new_loop.close()
            thread = threading.Thread(target=run_in_thread, daemon=True)
            thread.start()
        else:
            try:
                loop.run_until_complete(coro)
            except Exception as e:
                logger.exception("Async task failed", exc_info=e)
    except Exception as e:
        logger.exception("Async task failed", exc_info=e)


async def _emit_task_started(task_id: str, recording_id: str, message: str) -> None:
    try:
        service = await _get_sse_service()
        await service.emit_task_started(task_id, recording_id, message)
    except Exception:
        logger.warning("Failed to emit task started event: %s", task_id)


async def _emit_task_progress(task_id: str, recording_id: str, progress: int, message: str) -> None:
    try:
        service = await _get_sse_service()
        await service.emit_task_progress(task_id, recording_id, progress, message)
    except Exception:
        logger.warning("Failed to emit task progress event: %s", task_id)


async def _emit_task_completed(task_id: str, recording_id: str, result_path: str) -> None:
    try:
        service = await _get_sse_service()
        await service.emit_task_completed(task_id, recording_id, result_path)
    except Exception:
        logger.warning("Failed to emit task completed event: %s", task_id)


async def _emit_task_failed(task_id: str, recording_id: str, error_message: str) -> None:
    try:
        service = await _get_sse_service()
        await service.emit_task_failed(task_id, recording_id, error_message)
    except Exception:
        logger.warning("Failed to emit task failed event: %s", task_id)


def run_transcription_task(task_id: str) -> None:
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            _run_async_task(_emit_task_failed(task_id, "", "Recording not found"))
            return

        try:
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            _update_task(session, task, status="running", progress=10, started_at=_now())
            _run_async_task(_emit_task_started(task_id, recording.id, "Task started"))
            recording.status = "normalizing"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()

            audio_service = AudioService()
            source = Path(recording.original_path)
            normalized = audio_service.normalize(source, recording.id)
            duration = audio_service.duration_seconds(normalized)
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return

            recording.normalized_path = str(normalized)
            recording.duration_seconds = duration
            recording.status = "transcribing"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(session, task, progress=35)
            _run_async_task(_emit_task_progress(task_id, recording.id, 35, "Processing audio"))

            segments = ASRService().transcribe(normalized)
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            _run_async_task(_emit_task_progress(task_id, recording.id, 70, "Transcribing"))
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
            _run_async_task(_emit_task_completed(task_id, recording.id, str(transcript_path)))
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(transcript_path),
                completed_at=_now(),
            )
        except Exception as exc:
            logger.exception("Transcription task failed: %s", task_id)
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
            _run_async_task(_emit_task_failed(task_id, recording.id, str(exc)))


def run_summary_task(task_id: str, mode: str = "summary") -> None:
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            _run_async_task(_emit_task_failed(task_id, "", "Recording not found"))
            return

        try:
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            _update_task(session, task, status="running", progress=20, started_at=_now())
            _run_async_task(_emit_task_started(task_id, recording.id, f"Summary task started ({mode})"))
            segments = session.exec(
                select(TranscriptSegment)
                .where(TranscriptSegment.recording_id == recording.id)
                .order_by(TranscriptSegment.sequence)
            ).all()
            transcript = "\n".join(segment.text for segment in segments if segment.text.strip())
            if not transcript:
                raise RuntimeError("Transcript is empty. Run transcription first.")

            _run_async_task(_emit_task_progress(task_id, recording.id, 50, "Generating summary"))
            content = SummaryService().generate(transcript, mode=mode)
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
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
            _run_async_task(_emit_task_completed(task_id, recording.id, str(summary_path)))
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(summary_path),
                completed_at=_now(),
            )
        except Exception as exc:
            logger.exception("Summary task failed: %s", task_id)
            _update_task(
                session,
                task,
                status="error",
                error_message=str(exc),
                completed_at=_now(),
            )
            _run_async_task(_emit_task_failed(task_id, recording.id if recording else "", str(exc)))
