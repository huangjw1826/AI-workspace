"""
任务管理 API 模块 - 转写/摘要任务的状态查询和取消操作

提供任务的实时状态查询和用户主动取消功能。
任务的创建由其他 API（transcribe.py / summary.py）触发，
此模块主要负责任务生命周期的控制和状态监控。
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session

from app.db.database import get_session
from app.models import Task
from app.services.task_service import cancel_task

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


@router.get("/{task_id}")
def get_task(task_id: str, session: Session = Depends(get_session)) -> Task:
    """获取任务的当前状态和进度信息。

    任务状态机：queued → running → completed | error | cancelled
    进度值范围：0（未开始）~ 100（已完成）

    Args:
        task_id: 任务唯一标识（UUID）
        session: 数据库会话

    Returns:
        Task: 包含状态、进度、错误信息等的完整任务实体

    Raises:
        HTTPException 404: 任务不存在
    """
    task = session.get(Task, task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return task


@router.post("/{task_id}/cancel")
def cancel(task_id: str, session: Session = Depends(get_session)) -> Task:
    """取消一个正在运行或排队中的任务。

    取消后任务状态变为 cancelled，关联录音状态自动回退：
    - 有已有摘要的记录恢复到 completed
    - 有已有转写的恢复到 transcribed
    - 无任何数据的恢复到 uploaded

    Args:
        task_id: 要取消的任务 ID
        session: 数据库会话

    Returns:
        Task: 取消后的任务（状态变为 cancelled）

    Raises:
        HTTPException 404: 任务不存在

    注意：已 completed/error/cancelled 的任务调用取消不会报错，
    但不会有任何效果（幂等操作）。
    """
    task = cancel_task(session, task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return task
