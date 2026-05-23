from datetime import datetime, timezone

from sqlmodel import Session, select

from app.models import Recording, Summary, Task, TranscriptSegment


ACTIVE_TASK_STATUSES = {"queued", "running"}
INTERRUPTED_MESSAGE = "Task was interrupted by application restart. Please retry."
CANCELLED_MESSAGE = "Task was cancelled by user."


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def create_or_get_task(
    session: Session,
    recording: Recording,
    task_type: str,
) -> tuple[Task, bool]:
    existing = session.exec(
        select(Task)
        .where(Task.recording_id == recording.id)
        .where(Task.task_type == task_type)
        .where(Task.status.in_(ACTIVE_TASK_STATUSES))
        .order_by(Task.created_at.desc())
    ).first()
    if existing is not None:
        return existing, False

    task = Task(recording_id=recording.id, task_type=task_type)
    session.add(task)
    session.commit()
    session.refresh(task)
    return task, True


def recover_interrupted_tasks(session: Session) -> int:
    tasks = session.exec(
        select(Task)
        .where(Task.status.in_(ACTIVE_TASK_STATUSES))
        .order_by(Task.updated_at.asc())
    ).all()
    recovered = 0
    now = utc_now()
    for task in tasks:
        task.status = "error"
        task.error_message = INTERRUPTED_MESSAGE
        task.completed_at = now
        task.updated_at = now
        session.add(task)
        _recover_recording_status(session, task, now)
        recovered += 1

    if recovered:
        session.commit()
    return recovered


def cancel_task(session: Session, task_id: str) -> Task | None:
    task = session.get(Task, task_id)
    if task is None:
        return None

    if task.status in {"completed", "error", "cancelled"}:
        return task

    now = utc_now()
    task.status = "cancelled"
    task.error_message = CANCELLED_MESSAGE
    task.completed_at = now
    task.updated_at = now
    session.add(task)
    _restore_recording_after_stopped_task(session, task, now)
    session.commit()
    session.refresh(task)
    return task


def _recover_recording_status(session: Session, task: Task, now: datetime) -> None:
    recording = session.get(Recording, task.recording_id)
    if recording is None:
        return

    if task.task_type.startswith("summary:"):
        _restore_recording_after_stopped_task(session, task, now)
    else:
        recording.status = "error"
        recording.error_message = INTERRUPTED_MESSAGE

    recording.updated_at = now
    session.add(recording)


def _restore_recording_after_stopped_task(session: Session, task: Task, now: datetime) -> None:
    recording = session.get(Recording, task.recording_id)
    if recording is None:
        return

    existing_summary = session.exec(
        select(Summary).where(Summary.recording_id == recording.id)
    ).first()
    existing_segment = session.exec(
        select(TranscriptSegment).where(TranscriptSegment.recording_id == recording.id)
    ).first()
    if existing_summary is not None:
        recording.status = "completed"
    elif existing_segment is not None:
        recording.status = "transcribed"
    else:
        recording.status = "uploaded"
    recording.updated_at = now
    session.add(recording)
