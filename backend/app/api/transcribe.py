"""
转写 API 模块 - 语音转写任务的触发接口

支持单个录音和批量转写两种模式。
转写任务在后台异步执行（通过 FastAPI BackgroundTasks），
任务进度通过 SSE 事件实时推送给前端。

核心流程：
1. 接收转写请求（单个或批量）
2. 通过 create_or_get_task 创建/复用好任务
3. 将录音状态更新为 queued
4. 通过 BackgroundTasks 在后台执行 run_transcription_task
5. 任务进度通过 SSE 事件推送
"""

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlmodel import Session

from app.db.database import get_session
from app.models import Recording, Task
from app.pipeline.workflow import run_transcription_task
from app.services.task_service import create_or_get_task

router = APIRouter(prefix="/api/transcribe", tags=["transcribe"])


class BatchTranscribeRequest(BaseModel):
    """批量转写请求模型。"""
    recording_ids: list[str] = Field(min_length=1, max_length=200, description="录音 ID 列表（1-200 个）")


@router.post("/batch")
def transcribe_batch(
    payload: BatchTranscribeRequest,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
) -> list[Task]:
    """批量发起转写任务。

    对每个录音 ID 执行以下逻辑：
    - 如果录音不存在，跳过
    - 如果已有活动任务（queued/running），直接返回已有任务
    - 否则创建新任务并加入后台执行

    Args:
        payload: 包含录音 ID 列表的请求体
        background_tasks: FastAPI 后台任务（用于异步执行转写）
        session: 数据库会话

    Returns:
        list[Task]: 每个录音对应的任务列表
    """
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
    """发起单个录音的转写任务。

    如果该录音已有活动任务（queued/running），直接返回已有任务，
    不会重复创建。新建任务在后台异步执行。

    Args:
        recording_id: 录音 ID
        background_tasks: FastAPI 后台任务（用于异步执行转写）
        session: 数据库会话

    Returns:
        Task: 创建或复用的转写任务

    Raises:
        HTTPException 404: 录音不存在
    """
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="录音不存在")
    task, created = create_or_get_task(session, recording, "transcription")
    if not created:
        return task
    recording.status = "queued"
    session.add(recording)
    session.commit()
    session.refresh(task)
    background_tasks.add_task(run_transcription_task, task.id)
    return task
