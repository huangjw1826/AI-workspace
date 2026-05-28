"""
Summary model - 摘要记录数据模型

表示大模型对转写内容生成的摘要结果。同一条录音可以有多个不同模板(mode)的摘要，
也可以多次对同一模板生成多份摘要（触发时间不同）。
"""

from datetime import datetime, timezone
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Summary(SQLModel, table=True):
    """摘要记录 - 存储 LLM 生成的摘要内容和模板类型。

    mode 标识使用的摘要模板：
    - structured_summary: 结构化摘要（背景、主题、关键结论、后续事项）
    - meeting_minutes: 会议纪要（议题、结论、风险、责任人）
    - action_items: 待办事项
    - decisions_risks: 决策与风险
    - executive_brief: 管理层简报
    - polished_transcript: 转写内容规整
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="摘要唯一标识，UUID v4 格式"
    )
    recording_id: str = Field(
        index=True, description="关联的录音 ID"
    )
    mode: str = Field(
        default="summary",
        description="摘要模板标识（如 meeting_minutes、action_items 等）"
    )
    content: str = Field(
        description="Markdown 格式的摘要正文"
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="摘要生成时间（UTC）"
    )
