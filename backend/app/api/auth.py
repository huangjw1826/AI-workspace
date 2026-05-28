"""Authentication middleware with local/remote separation.

``API_TOKEN`` is only for Android remote access. The PC browser can use the
local app without a token, but public hosts and reverse-proxy traffic must
present ``X-API-Token`` for ``/api/*`` routes.

Supports backward compatibility for single API_TOKEN and new database-based multi-token system.
"""

import logging
from datetime import datetime, timezone
from hmac import compare_digest
from typing import Optional
from urllib.parse import urlparse

from sqlmodel import Session, select
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.db.database import engine
from app.models.api_token import ApiToken
from app.models.access_log import AccessLog

logger = logging.getLogger(__name__)

_LOOPBACK_HOSTS = frozenset({"127.0.0.1", "::1", "localhost"})


def _host_is_loopback(host: str) -> bool:
    return host.strip().lower().strip("[]") in _LOOPBACK_HOSTS


def _host_header_is_local(request: Request) -> bool:
    host_header = request.headers.get("host", "").strip()
    if host_header.startswith("["):
        host = host_header[1:].split("]", 1)[0]
    elif host_header.count(":") == 1:
        host = host_header.rsplit(":", 1)[0]
    else:
        host = host_header
    return _host_is_loopback(host)


def is_local_request(request: Request) -> bool:
    """Return true only for requests that explicitly target loopback.

    Reverse proxies often connect to uvicorn from 127.0.0.1, so client IP is
    not an auth signal. Public hosts without an Origin are treated as remote.
    """
    origin = request.headers.get("origin", "").strip()
    if origin:
        try:
            hostname = urlparse(origin).hostname or ""
        except Exception:
            return False
        return _host_is_loopback(hostname) and _host_header_is_local(request)

    return _host_header_is_local(request)


def _write_access_log(
    token_id: Optional[str],
    device_name: Optional[str],
    method: str,
    path: str,
    status_code: int,
    ip_address: Optional[str] = None,
    user_agent: Optional[str] = None,
) -> None:
    """Write access log to database."""
    try:
        with Session(engine) as session:
            log = AccessLog(
                token_id=token_id,
                device_name=device_name,
                method=method,
                path=path,
                status_code=status_code,
                ip_address=ip_address,
                user_agent=user_agent,
            )
            session.add(log)
            session.commit()
    except Exception as e:
        logger.warning("Failed to write access log: %s", e)


def _validate_db_token(candidate: str) -> Optional[ApiToken]:
    """Validate token against database and update last_used_at."""
    try:
        with Session(engine) as session:
            statement = select(ApiToken).where(
                ApiToken.token == candidate,
                ApiToken.is_active == True,
            )
            token_record = session.exec(statement).first()
            if token_record:
                token_record.last_used_at = datetime.now(timezone.utc)
                session.add(token_record)
                session.commit()
                session.refresh(token_record)
                return token_record
    except Exception as e:
        logger.warning("Database token validation failed: %s", e)
    return None


class LocalBypassTokenMiddleware(BaseHTTPMiddleware):
    """Require ``X-API-Token`` for remote ``/api/*`` requests.

    Local loopback requests bypass token checks for the PC browser workflow.
    Remote Android clients must send the configured token. If no token is
    configured, routes remain open so local setup does not lock the user out.

    Supports both legacy single API_TOKEN and new database-based multi-token system.
    """

    def __init__(
        self,
        app,
        api_token: str = "",
        protected_prefixes: tuple[str, ...] = ("/api",),
    ) -> None:
        super().__init__(app)
        self.api_token = api_token.strip()
        self.protected_prefixes = protected_prefixes

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        if request.method == "OPTIONS":
            return await call_next(request)

        if is_local_request(request):
            response = await call_next(request)
            return response

        if not self._is_protected_path(request.url.path):
            return await call_next(request)

        candidate = request.headers.get("X-API-Token", "")
        if not candidate:
            if self._needs_auth():
                logger.warning(
                    "Remote request to %s rejected: missing API token (Origin: %s, Host: %s)",
                    request.url.path,
                    request.headers.get("origin", "<none>"),
                    request.headers.get("host", "<none>"),
                )
                return JSONResponse({"detail": "Invalid API token"}, status_code=403)
            return await call_next(request)

        token_record = None
        if self.api_token and compare_digest(candidate, self.api_token):
            pass
        else:
            token_record = _validate_db_token(candidate)
            if not token_record:
                logger.warning(
                    "Remote request to %s rejected: invalid API token (Origin: %s, Host: %s)",
                    request.url.path,
                    request.headers.get("origin", "<none>"),
                    request.headers.get("host", "<none>"),
                )
                return JSONResponse({"detail": "Invalid API token"}, status_code=403)

        response = await call_next(request)

        _write_access_log(
            token_id=token_record.id if token_record else None,
            device_name=token_record.name if token_record else None,
            method=request.method,
            path=request.url.path,
            status_code=response.status_code,
            ip_address=request.client.host if request.client else None,
            user_agent=request.headers.get("user-agent"),
        )

        return response

    def _is_protected_path(self, path: str) -> bool:
        return any(path == prefix or path.startswith(f"{prefix}/") for prefix in self.protected_prefixes)

    def _needs_auth(self) -> bool:
        """Check if authentication is required."""
        if self.api_token:
            return True
        try:
            with Session(engine) as session:
                statement = select(ApiToken).where(ApiToken.is_active == True)
                if session.exec(statement).first():
                    return True
        except Exception:
            pass
        return False
