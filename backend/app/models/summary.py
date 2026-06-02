"""
Summary 模型 - 摘要记录

存储 LLM 生成的智能摘要内容，每条录音可以有多份不同模板的摘要。
同一条录音可以保留多次不同角度的摘要结果（如会议纪要 + 待办事项）。
"""

from datetime import datetime, timezone
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Summary(SQLModel, table=True):
    """摘要记录 - LLM 生成的智能摘要数据库映射。

    每条摘要关联一条录音记录，通过 mode 字段区分使用的摘要模板。
    同一条录音可以创建多份不同模板的摘要。
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="摘要唯一标识，UUID v4 格式",
    )
    recording_id: str = Field(
        index=True, foreign_key="recording.id",
        description="关联的录音记录 ID",
    )
    mode: str = Field(
        default="structured_summary",
        description="摘要模板 ID，如：structured_summary（结构化摘要）| meeting_minutes（会议纪要）| action_items（待办事项）等",
    )
    content: str = Field(
        default="",
        description="摘要内容（Markdown 格式），由 LLM 生成的完整摘要文本",
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="摘要创建时间（UTC）",
    )
