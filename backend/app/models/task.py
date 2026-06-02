"""
Task 模型 - 异步任务记录

表示转写或摘要任务的执行记录，包含完整的状态机转换：
queued → running → completed | error | cancelled

每个任务关联一条录音记录（recording_id），
通过 task_type 区分转写（transcription）和摘要（summary:<mode>）任务。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Task(SQLModel, table=True):
    """异步任务记录 - 转写/摘要任务的数据库映射。

    任务类型（task_type）格式：
    - "transcription"：语音转写任务
    - "summary:{mode}"：摘要任务，{mode} 为模板 ID
    """

    # 标识信息
    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="任务唯一标识，UUID v4 格式",
    )
    recording_id: str = Field(
        index=True, foreign_key="recording.id",
        description="关联的录音记录 ID",
    )
    task_type: str = Field(
        description="任务类型：transcription（转写）或 summary:{mode}（摘要，mode 为模板 ID）",
    )

    # 执行状态
    status: str = Field(
        default="queued",
        description="任务状态：queued（排队中）| running（运行中）| completed（完成）| error（错误）| cancelled（已取消）",
    )
    progress: int = Field(
        default=0, ge=0, le=100,
        description="任务进度百分比（0-100）",
    )
    error_message: Optional[str] = Field(
        default=None,
        description="错误信息（仅在 status=error 时有效）",
    )
    result_path: Optional[str] = Field(
        default=None,
        description="结果文件的绝对路径（JSON/Markdown）",
    )

    # 时间戳
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="任务创建时间（UTC）",
    )
    updated_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="最后更新时间（UTC）",
    )
    started_at: Optional[datetime] = Field(
        default=None,
        description="任务开始执行时间（UTC，从 queued → running 的时间点）",
    )
    completed_at: Optional[datetime] = Field(
        default=None,
        description="任务完成时间（UTC，进入最终状态：completed/error/cancelled 的时间点）",
    )
