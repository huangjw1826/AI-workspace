"""
Task model - 任务记录数据模型

表示系统中的异步任务（转写、摘要等）。
任务状态机：queued → running → completed | error | cancelled
每个录音可以有多个不同 task_type 的活动任务共存。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Task(SQLModel, table=True):
    """异步任务 - 记录转写/摘要等后台任务的执行状态和结果。"""

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="任务唯一标识，UUID v4 格式"
    )
    recording_id: str = Field(
        index=True, description="关联的录音 ID"
    )
    task_type: str = Field(
        description="任务类型：transcription（转写）或 summary:<mode>（指定模板的摘要）"
    )
    status: str = Field(
        default="queued",
        description="任务状态：queued | running | completed | error | cancelled"
    )
    progress: int = Field(
        default=0, description="任务进度百分比（0-100）"
    )
    error_message: Optional[str] = Field(
        default=None, description="任务失败时的错误详情"
    )
    result_path: Optional[str] = Field(
        default=None, description="任务产物的文件路径"
    )

    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="任务创建时间（UTC）"
    )
    updated_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="最后更新时间（UTC）"
    )
    started_at: Optional[datetime] = Field(
        default=None, description="任务开始执行时间（UTC）"
    )
    completed_at: Optional[datetime] = Field(
        default=None, description="任务完成/失败/取消时间（UTC）"
    )

