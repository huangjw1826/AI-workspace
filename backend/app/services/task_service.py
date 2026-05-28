"""
Task service - 异步任务生命周期管理

提供任务的核心管理能力：
- 创建/复用：同录音同类型活动任务复用，避免重复提交
- 恢复：应用重启后自动将遗留的 queued/running 任务标记为中断错误
- 取消：用户主动取消运行中任务，关联录音状态自动回退
"""

from datetime import datetime, timezone

from sqlmodel import Session, select

from app.models import Recording, Summary, Task, TranscriptSegment


# 活动任务状态集合：可复用的任务（避免重复创建）
ACTIVE_TASK_STATUSES = {"queued", "running"}
INTERRUPTED_MESSAGE = "Task was interrupted by application restart. Please retry."
CANCELLED_MESSAGE = "Task was cancelled by user."


def utc_now() -> datetime:
    """当前 UTC 时间。"""
    return datetime.now(timezone.utc)


def create_or_get_task(
    session: Session,
    recording: Recording,
    task_type: str,
) -> tuple[Task, bool]:
    """创建或获取已有活动任务，避免重复提交。

    查询条件：同一录音 + 同一任务类型 + 活动状态（queued/running）。
    如果存在活动任务直接返回，不存在则创建新任务。

    Args:
        session: 数据库会话
        recording: 关联的录音记录
        task_type: 任务类型（transcription 或 summary:<mode>）

    Returns:
        (task, created) — task 为任务对象，created 为 True 表示新建
    """
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
    """应用启动时恢复中断的任务。

    将所有 queued/running 状态的任务标记为 error，
    原因消息为 INTERRUPTED_MESSAGE，录音状态根据已有数据恢复。

    Args:
        session: 数据库会话

    Returns:
        恢复的任务数量
    """
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
    """取消任务。

    已结束的任务（completed/error/cancelled）直接返回不操作。
    活动任务标记为 cancelled，关联录音状态回退。

    Args:
        session: 数据库会话
        task_id: 要取消的任务 ID

    Returns:
        取消后的任务对象，不存在返回 None
    """
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
    """中断恢复时根据任务类型和已有数据还原录音状态。

    摘要任务：恢复到已有摘要/转写对应的状态
    转写等其他任务：标记为 error
    """
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
    """任务停止/取消后将录音状态回退到匹配的已有数据状态。

    优先级：completed（有摘要）> transcribed（有转写）> uploaded（无数据）
    """
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
