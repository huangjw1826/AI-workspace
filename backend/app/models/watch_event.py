"""
WatchEvent model - 目录监控事件数据模型

记录目录监控服务的每次扫描事件，包括文件导入成功、重复跳过、格式不支持等情况。
用于审计追踪和前端展示监控历史。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class WatchEvent(SQLModel, table=True):
    """监控事件 - 目录监控服务发现文件时的处理结果记录。

    事件状态：
    - imported: 成功导入并入库
    - duplicate_skipped: 内容哈希已存在，跳过
    - skipped: 文件格式不支持或其他原因跳过
    - error: 处理过程中发生错误
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="事件唯一标识，UUID v4 格式"
    )
    file_path: str = Field(
        index=True, description="被监控发现的文件完整路径"
    )
    filename: str = Field(
        description="文件名（不含路径）"
    )
    status: str = Field(
        index=True,
        description="处理状态：imported | duplicate_skipped | skipped | error"
    )
    reason: Optional[str] = Field(
        default=None, description="状态原因说明（跳过原因或错误信息）"
    )
    recording_id: Optional[str] = Field(
        default=None, index=True,
        description="成功导入后关联的录音 ID（导入失败时为 None）"
    )
    duplicate_of_id: Optional[str] = Field(
        default=None, index=True,
        description="重复时指向已存在的录音 ID"
    )
    content_hash: Optional[str] = Field(
        default=None, index=True,
        description="文件内容哈希值（SHA-256）"
    )
    file_size: Optional[int] = Field(
        default=None, description="文件大小，单位字节"
    )
    file_mtime: Optional[float] = Field(
        default=None, description="文件最后修改时间戳"
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="事件创建时间（UTC）"
    )
