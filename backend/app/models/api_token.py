"""
ApiToken model - API Token 数据模型

管理远程访问的 API Token，支持多个设备的独立 Token。
Token 通过 Web 界面（设置页）创建和管理，存储在数据库中。
"""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class ApiToken(SQLModel, table=True):
    """API Token - 用于 Android 客户端或其他远程设备的 API 认证。

    与 backend/.env 中的 API_TOKEN 不同，数据库 Token 具备以下特性：
    - 可创建多个，每个对应一台设备
    - 支持启用/禁用切换（is_active）
    - 记录最后使用时间用于审计
    - Token 值在创建时生成，展示时部分掩码
    """

    id: str = Field(
        default_factory=lambda: str(uuid4()), primary_key=True,
        description="Token 记录唯一标识，UUID v4 格式"
    )
    token: str = Field(
        index=True, unique=True,
        description="Token 值（明文存储，仅用于认证匹配）"
    )
    name: str = Field(
        description="Token 名称（如 '我的手机'），用于识别设备"
    )
    device_info: Optional[str] = Field(
        default=None, description="设备信息备注"
    )
    is_active: bool = Field(
        default=True, description="Token 是否启用，False 则该 Token 无法通过认证"
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="Token 创建时间（UTC）"
    )
    last_used_at: Optional[datetime] = Field(
        default=None, description="Token 最后使用时间（UTC）"
    )
