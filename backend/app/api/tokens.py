"""
API Token 管理模块 - API 认证令牌的完整生命周期管理

提供 API Token 的 CRUD 操作，支持：
- 创建新 Token（secrets.token_urlsafe 生成，仅返回一次）
- 列出/查看 Token（敏感信息自动掩码处理）
- 启用/禁用 Token（用于临时撤销访问权限）
- 删除 Token（永久撤销）
- 访问日志查询（审计追踪）

安全策略：创建新 Token 需要本地请求或有效 Token 认证，
其余操作仅限于本地请求（_require_local 保护）。
"""

import secrets
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlmodel import Session, select, desc

from app.api.auth import is_local_request
from app.db.database import get_session
from app.models.api_token import ApiToken
from app.models.access_log import AccessLog

router = APIRouter(prefix="/api/tokens", tags=["tokens"])


class ApiTokenCreate(BaseModel):
    """创建 Token 请求模型。"""
    name: str = Field(..., description="Token 名称/设备标识（如：我的 Android 手机）")
    device_info: Optional[str] = Field(None, description="设备信息的 JSON 字符串（可选）")


class ApiTokenResponse(BaseModel):
    """Token 响应模型 - 敏感字段自动掩码。"""
    id: str
    name: str
    device_info: Optional[str]
    is_active: bool
    created_at: datetime
    last_used_at: Optional[datetime]
    token: Optional[str] = None  # 仅在创建时返回完整 Token


class ApiTokenUpdate(BaseModel):
    """更新 Token 请求模型 - 支持修改名称和启用状态。"""
    name: Optional[str] = None
    is_active: Optional[bool] = None


class AccessLogResponse(BaseModel):
    """访问日志响应模型。"""
    id: str
    token_id: Optional[str]
    device_name: Optional[str]
    method: str
    path: str
    status_code: int
    ip_address: Optional[str]
    user_agent: Optional[str]
    created_at: datetime


def _require_local(request: Request) -> None:
    """强制本地请求校验 - 非本地请求返回 403 禁止访问。

    Args:
        request: FastAPI 请求对象

    Raises:
        HTTPException: 非本地请求时返回 403
    """
    if not is_local_request(request):
        raise HTTPException(status_code=403, detail="仅允许本地访问")


def _mask_token(token: str) -> str:
    """对 Token 进行掩码处理，用于前端展示防止泄露。

    短 Token（<=8 字符）全部替换为 *；
    长 Token 保留首尾 4 字符，中间替换为 ...。

    Args:
        token: 原始 Token 字符串

    Returns:
        掩码后的 Token 字符串
    """
    if len(token) <= 8:
        return "*" * len(token)
    return token[:4] + "..." + token[-4:]


@router.post("", response_model=ApiTokenResponse)
def create_token(
    request: Request,
    token_data: ApiTokenCreate,
    session: Session = Depends(get_session),
):
    """创建新的 API Token（完整 Token 仅此一次返回）。

    安全验证：非本地请求时必须提供已有有效 Token 才能创建新 Token，
    防止未授权用户滥用 Token 创建接口。

    Args:
        request: FastAPI 请求对象（用于判断来源）
        token_data: Token 创建参数（名称和设备信息）
        session: 数据库会话

    Returns:
        ApiTokenResponse - 包含完整的 Token 字符串（仅此次返回）
    """
    if not is_local_request(request):
        candidate = request.headers.get("X-API-Token", "")
        if not candidate:
            raise HTTPException(status_code=403, detail="需要本地访问或有效 Token")
        statement = select(ApiToken).where(
            ApiToken.token == candidate,
            ApiToken.is_active == True,
        )
        if not session.exec(statement).first():
            raise HTTPException(status_code=403, detail="需要本地访问或有效 Token")

    # 使用 secrets.token_urlsafe 生成 32 字节的加密安全随机 Token
    token = secrets.token_urlsafe(32)
    db_token = ApiToken(
        token=token,
        name=token_data.name,
        device_info=token_data.device_info,
        is_active=True,
    )
    session.add(db_token)
    session.commit()
    session.refresh(db_token)

    return ApiTokenResponse(
        id=db_token.id,
        name=db_token.name,
        device_info=db_token.device_info,
        is_active=db_token.is_active,
        created_at=db_token.created_at,
        last_used_at=db_token.last_used_at,
        token=token,  # 完整 Token 仅创建时返回
    )


@router.get("", response_model=List[ApiTokenResponse])
def list_tokens(
    request: Request,
    session: Session = Depends(get_session),
):
    """获取所有 API Token 列表（敏感信息自动掩码）。

    仅限本地请求访问，返回所有 Token 的摘要信息。

    Args:
        request: FastAPI 请求对象
        session: 数据库会话

    Returns:
        List[ApiTokenResponse] - Token 列表（token 字段已掩码）
    """
    _require_local(request)
    statement = select(ApiToken).order_by(desc(ApiToken.created_at))
    tokens = session.exec(statement).all()

    return [
        ApiTokenResponse(
            id=t.id,
            name=t.name,
            device_info=t.device_info,
            is_active=t.is_active,
            created_at=t.created_at,
            last_used_at=t.last_used_at,
            token=_mask_token(t.token),  # 自动掩码敏感信息
        )
        for t in tokens
    ]


@router.get("/{token_id}", response_model=ApiTokenResponse)
def get_token(
    request: Request,
    token_id: str,
    session: Session = Depends(get_session),
):
    """获取单个 API Token 详情（敏感信息掩码）。

    仅限本地请求访问。

    Args:
        request: FastAPI 请求对象
        token_id: Token ID
        session: 数据库会话

    Returns:
        ApiTokenResponse - Token 详情（token 字段已掩码）
    """
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token 不存在")

    return ApiTokenResponse(
        id=token.id,
        name=token.name,
        device_info=token.device_info,
        is_active=token.is_active,
        created_at=token.created_at,
        last_used_at=token.last_used_at,
        token=_mask_token(token.token),
    )


@router.patch("/{token_id}", response_model=ApiTokenResponse)
def update_token(
    request: Request,
    token_id: str,
    token_data: ApiTokenUpdate,
    session: Session = Depends(get_session),
):
    """更新 API Token 信息（名称/启用状态）。

    仅限本地请求访问。支持部分更新，未提供的字段保持原值。
    禁用 Token 可用于临时撤销设备访问权限而不删除该记录。

    Args:
        request: FastAPI 请求对象
        token_id: Token ID
        token_data: 要更新的字段
        session: 数据库会话

    Returns:
        ApiTokenResponse - 更新后的 Token 信息
    """
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token 不存在")

    if token_data.name is not None:
        token.name = token_data.name
    if token_data.is_active is not None:
        token.is_active = token_data.is_active

    session.add(token)
    session.commit()
    session.refresh(token)

    return ApiTokenResponse(
        id=token.id,
        name=token.name,
        device_info=token.device_info,
        is_active=token.is_active,
        created_at=token.created_at,
        last_used_at=token.last_used_at,
        token=_mask_token(token.token),
    )


@router.delete("/{token_id}")
def delete_token(
    request: Request,
    token_id: str,
    session: Session = Depends(get_session),
):
    """删除/撤销 API Token。

    仅限本地请求访问。删除后使用此 Token 的设备将无法再访问 API。
    操作不可逆，请谨慎使用。

    Args:
        request: FastAPI 请求对象
        token_id: 要删除的 Token ID
        session: 数据库会话

    Returns:
        dict: 操作结果消息
    """
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token 不存在")

    session.delete(token)
    session.commit()
    return {"message": "Token 已成功删除"}


@router.get("/logs/list", response_model=List[AccessLogResponse])
def list_access_logs(
    request: Request,
    token_id: Optional[str] = None,
    limit: int = 100,
    offset: int = 0,
    session: Session = Depends(get_session),
):
    """List access logs. Local access only."""
    _require_local(request)
    statement = select(AccessLog)
    if token_id:
        statement = statement.where(AccessLog.token_id == token_id)
    statement = statement.order_by(desc(AccessLog.created_at)).limit(limit).offset(offset)
    logs = session.exec(statement).all()

    return [
        AccessLogResponse(
            id=l.id,
            token_id=l.token_id,
            device_name=l.device_name,
            method=l.method,
            path=l.path,
            status_code=l.status_code,
            ip_address=l.ip_address,
            user_agent=l.user_agent,
            created_at=l.created_at,
        )
        for l in logs
    ]
