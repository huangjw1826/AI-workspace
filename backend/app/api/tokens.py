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
    name: str = Field(..., description="Token name/device identifier")
    device_info: Optional[str] = Field(None, description="JSON string with device info")


class ApiTokenResponse(BaseModel):
    id: str
    name: str
    device_info: Optional[str]
    is_active: bool
    created_at: datetime
    last_used_at: Optional[datetime]
    token: Optional[str] = None  # Only returned on creation


class ApiTokenUpdate(BaseModel):
    name: Optional[str] = None
    is_active: Optional[bool] = None


class AccessLogResponse(BaseModel):
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
    """Raise 403 if not a local request."""
    if not is_local_request(request):
        raise HTTPException(status_code=403, detail="Local access only")


def _mask_token(token: str) -> str:
    """Mask token for display."""
    if len(token) <= 8:
        return "*" * len(token)
    return token[:4] + "..." + token[-4:]


@router.post("", response_model=ApiTokenResponse)
def create_token(
    request: Request,
    token_data: ApiTokenCreate,
    session: Session = Depends(get_session),
):
    """Create a new API token. Returns the full token only once."""
    if not is_local_request(request):
        candidate = request.headers.get("X-API-Token", "")
        if not candidate:
            raise HTTPException(status_code=403, detail="Local access or valid token required")
        statement = select(ApiToken).where(
            ApiToken.token == candidate,
            ApiToken.is_active == True,
        )
        if not session.exec(statement).first():
            raise HTTPException(status_code=403, detail="Local access or valid token required")

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
        token=token,
    )


@router.get("", response_model=List[ApiTokenResponse])
def list_tokens(
    request: Request,
    session: Session = Depends(get_session),
):
    """List all API tokens (masked). Local access only."""
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
            token=_mask_token(t.token),
        )
        for t in tokens
    ]


@router.get("/{token_id}", response_model=ApiTokenResponse)
def get_token(
    request: Request,
    token_id: str,
    session: Session = Depends(get_session),
):
    """Get a single API token (masked). Local access only."""
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token not found")

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
    """Update an API token. Local access only."""
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token not found")

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
    """Delete/revoke an API token. Local access only."""
    _require_local(request)
    token = session.get(ApiToken, token_id)
    if not token:
        raise HTTPException(status_code=404, detail="Token not found")

    session.delete(token)
    session.commit()
    return {"message": "Token deleted successfully"}


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
