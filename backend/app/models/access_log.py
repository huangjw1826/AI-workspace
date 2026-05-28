"""
AccessLog model - API 访问日志数据模型

记录经过认证的远程 API 访问日志，用于安全审计和访问追踪。
仅记录通过 API Token 认证的请求，本地免认证请求不记录。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class AccessLog(SQLModel, table=True):
    """访问日志 - 记录每次 API Token 认证请求的关键信息。

    记录内容包含请求源信息、目标路径和响应状态码，
    不记录请求 body 和响应内容（隐私保护）。
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="日志唯一标识，UUID v4 格式"
    )
    token_id: Optional[str] = Field(
        index=True, default=None,
        description="使用的 API Token 记录 ID（用于追踪特定 token 的使用情况）"
    )
    device_name: Optional[str] = Field(
        default=None, description="设备名称（从 Token 元数据中提取）"
    )
    method: str = Field(
        description="HTTP 请求方法：GET、POST、PATCH、DELETE 等"
    )
    path: str = Field(
        description="请求的 API 路径"
    )
    status_code: int = Field(
        description="响应 HTTP 状态码"
    )
    ip_address: Optional[str] = Field(
        default=None, description="请求来源 IP 地址"
    )
    user_agent: Optional[str] = Field(
        default=None, description="请求的 User-Agent 头"
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="请求时间（UTC）"
    )
