from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlmodel import Session

from app.db.database import get_session
from app.models import Recording, Task
from app.pipeline.workflow import run_transcription_task
from app.services.task_service import create_or_get_task

router = APIRouter(prefix="/api/transcribe", tags=["transcribe"])


class BatchTranscribeRequest(BaseModel):
    recording_ids: list[str] = Field(min_length=1, max_length=200)


@router.post("/batch")
def transcribe_batch(
    payload: BatchTranscribeRequest,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
) -> list[Task]:
    tasks: list[Task] = []
    for recording_id in payload.recording_ids:
        recording = session.get(Recording, recording_id)
        if recording is None:
            continue
        task, created = create_or_get_task(session, recording, "transcription")
        if created:
            recording.status = "queued"
            session.add(recording)
            session.commit()
            session.refresh(task)
            background_tasks.add_task(run_transcription_task, task.id)
        tasks.append(task)
    return tasks


@router.post("/{recording_id}")
def transcribe(
    recording_id: str,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
) -> Task:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    task, created = create_or_get_task(session, recording, "transcription")
    if not created:
        return task
    recording.status = "queued"
    session.add(recording)
    session.commit()
    session.refresh(task)
    background_tasks.add_task(run_transcription_task, task.id)
    return task
