from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from sqlmodel import Session

from app.db.database import get_session
from app.models import Recording, Task
from app.pipeline.workflow import run_transcription_task

router = APIRouter(prefix="/api/transcribe", tags=["transcribe"])


@router.post("/{recording_id}")
def transcribe(
    recording_id: str,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
) -> Task:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    task = Task(recording_id=recording_id, task_type="transcription")
    recording.status = "queued"
    session.add(task)
    session.add(recording)
    session.commit()
    session.refresh(task)
    background_tasks.add_task(run_transcription_task, task.id)
    return task

