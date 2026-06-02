"""
TranscriptSegment 模型 - 转写片段记录

存储语音转写结果的每个片段，包含时间轴、文本和说话人信息。
多个片段按 sequence 排序后构成完整的转写文本。

每个片段独立存储支持：
- 按时间戳跳转播放音频
- 片段级别的转写校对编辑
- 说话人分离（diarization）标签
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class TranscriptSegment(SQLModel, table=True):
    """转写片段 - 语音转写结果的最小单元。

    由 ASR 服务生成，包含时间轴和文本内容。
    用户可以对片段的 text 字段进行校对修改。
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="片段唯一标识，UUID v4 格式",
    )
    recording_id: str = Field(
        index=True, foreign_key="recording.id",
        description="关联的录音记录 ID",
    )
    start_time: Optional[float] = Field(
        default=None,
        description="片段开始时间（秒，相对于音频开头）",
    )
    end_time: Optional[float] = Field(
        default=None,
        description="片段结束时间（秒，相对于音频开头）",
    )
    speaker: Optional[str] = Field(
        default=None,
        description="说话人标识（如 speaker_1, speaker_2），说话人分离启用时有效",
    )
    text: str = Field(
        default="",
        description="转写文本内容（允许用户校对编辑）",
    )
    sequence: int = Field(
        default=0,
        description="片段序号（按时间顺序排列，从 0 开始）",
    )
