"""
Transcript model - 转写片段数据模型

表示 FunASR 语音转写结果中的单个片段（句子级别），包含时间轴、说话人和文本内容。
一个录音包含多个有序的 TranscriptSegment，按 sequence 字段排序。
"""

from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class TranscriptSegment(SQLModel, table=True):
    """转写片段 - 语音转写结果的最小单元，每个片段对应一句话。

    片段可被用户编辑（通过 PATCH /api/recordings/{id}/segments/{segment_id}），
    修改会同步到数据库和 JSON 备份文件。
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="片段唯一标识，UUID v4 格式"
    )
    recording_id: str = Field(
        index=True, description="所属录音 ID"
    )
    start_time: float = Field(
        description="片段开始时间，相对于音频开头的秒数"
    )
    end_time: float = Field(
        description="片段结束时间，相对于音频开头的秒数"
    )
    speaker: str = Field(
        default="speaker_1",
        description="说话人标签（如 speaker_1、speaker_2），说话人分离功能未启用时均为 speaker_1"
    )
    text: str = Field(
        description="转写或校对后的文本内容"
    )
    sequence: int = Field(
        default=0, description="片段排序序号，从 0 开始递增"
    )


class TranscriptSegmentRead(SQLModel):
    """转写片段只读视图 - 用于 API 响应，与 TranscriptSegment 字段一致但无数据库绑定。"""

    id: str
    recording_id: str
    start_time: float
    end_time: float
    speaker: str
    text: str
    sequence: int

