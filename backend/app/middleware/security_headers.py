from typing import Callable

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """安全头中间件，添加安全相关的 HTTP 响应头"""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        response = await call_next(request)
        
        # 添加安全头
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-XSS-Protection"] = "0"  # 现代浏览器使用 CSP，设为 0
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = (
            "accelerometer=(), ambient-light-sensor=(), autoplay=(), battery=(), camera=(), "
            "clipboard-write=(), cross-origin-isolated=(), display-capture=(), document-domain=(), "
            "encrypted-media=(), fullscreen=(), gamepad=(), geolocation=(), gyroscope=(), "
            "keyboard-map=(), magnetometer=(), microphone=(), midi=(), payment=(), "
            "picture-in-picture=(), publickey-credentials-get=(), screen-wake-lock=(), "
            "sync-xhr=(), usb=(), web-share=(), xr-spatial-tracking=()"
        )
        
        # 基础 CSP（由于是本地应用，较为宽松）
        # 生产环境可以考虑更严格的 CSP
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data:; "
            "font-src 'self'; "
            "connect-src 'self'; "
            "frame-ancestors 'none'; "
            "base-uri 'self'; "
            "form-action 'self'"
        )
        
        return response
