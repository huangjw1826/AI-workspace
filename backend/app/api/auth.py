"""Authentication middleware with local/remote separation.

``API_TOKEN`` is only for Android remote access. The PC browser can use the
local app without a token, but public hosts and reverse-proxy traffic must
present ``X-API-Token`` for ``/api/*`` routes.
"""

import logging
from hmac import compare_digest
from urllib.parse import urlparse

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

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


class LocalBypassTokenMiddleware(BaseHTTPMiddleware):
    """Require ``X-API-Token`` for remote ``/api/*`` requests.

    Local loopback requests bypass token checks for the PC browser workflow.
    Remote Android clients must send the configured token. If no token is
    configured, routes remain open so local setup does not lock the user out.
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
            return await call_next(request)

        if not self.api_token:
            return await call_next(request)

        if self._is_protected_path(request.url.path):
            candidate = request.headers.get("X-API-Token", "")
            if not candidate or not compare_digest(candidate, self.api_token):
                logger.warning(
                    "Remote request to %s rejected: invalid or missing API token (Origin: %s, Host: %s)",
                    request.url.path,
                    request.headers.get("origin", "<none>"),
                    request.headers.get("host", "<none>"),
                )
                return JSONResponse({"detail": "Invalid API token"}, status_code=403)

        return await call_next(request)

    def _is_protected_path(self, path: str) -> bool:
        return any(path == prefix or path.startswith(f"{prefix}/") for prefix in self.protected_prefixes)
