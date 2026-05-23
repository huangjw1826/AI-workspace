from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session

from app.db.database import get_session
from app.models import Task
from app.services.task_service import cancel_task

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


@router.get("/{task_id}")
def get_task(task_id: str, session: Session = Depends(get_session)) -> Task:
    task = session.get(Task, task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return task


@router.post("/{task_id}/cancel")
def cancel(task_id: str, session: Session = Depends(get_session)) -> Task:
    task = cancel_task(session, task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return task
