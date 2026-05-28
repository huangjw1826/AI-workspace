"""
Recording model - 录音记录数据模型

表示系统中一条完整的录音记录，包含文件元数据、处理状态和处理结果信息。
录音状态机：uploaded → queued → normalizing → transcribing → transcribed → completed
任何阶段出错进入 error 状态。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Recording(SQLModel, table=True):
    """录音记录 - 音频文件的数据库映射，关联文件存储路径和处理状态。"""

    # 标识信息
    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="录音唯一标识，UUID v4 格式"
    )
    filename: str = Field(description="原始文件名（不含路径）")
    original_path: str = Field(description="原始音频文件的绝对路径")

    # 处理文件路径
    normalized_path: Optional[str] = Field(
        default=None,
        description="归一化后的音频路径（单声道 16kHz WAV），用于转写"
    )

    # 音频属性
    duration_seconds: Optional[float] = Field(
        default=None, description="音频时长，单位秒"
    )
    file_size_bytes: Optional[int] = Field(
        default=None, description="原始文件大小，单位字节"
    )
    source_mtime: Optional[float] = Field(
        default=None, description="源文件修改时间戳，用于目录监控去重"
    )
    format: str = Field(description="音频格式扩展名：wav、mp3、m4a、flac、aac、ogg 等")

    # 去重和来源
    content_hash: Optional[str] = Field(
        default=None, index=True,
        description="文件内容哈希值（SHA-256），用于去重检测"
    )
    source_type: str = Field(
        default="upload", description="录音来源：upload（手动上传）或 watch（目录监控）"
    )
    source_path: Optional[str] = Field(
        default=None, description="监控来源的原始目录路径（仅 watch 类型）"
    )

    # 管理属性
    tags: str = Field(
        default="", description="标签列表，逗号分隔的字符串"
    )
    status: str = Field(
        default="uploaded",
        description="处理状态：uploaded | queued | normalizing | transcribing | transcribed | completed | error"
    )
    error_message: Optional[str] = Field(
        default=None, description="最近一次错误的详细信息"
    )

    # 时间戳
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="记录创建时间（UTC）"
    )
    updated_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="最后更新时间（UTC）"
    )
