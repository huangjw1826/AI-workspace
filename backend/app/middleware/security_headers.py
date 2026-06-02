"""
安全响应头中间件

为所有 HTTP 响应添加安全相关的响应头，降低常见 Web 安全风险。
由于 AI Recorder 是一个本地应用（主要通过 127.0.0.1 访问），
安全策略在"足够安全"和"方便使用"之间取得平衡。

配置的安全头：
- X-Content-Type-Options: nosniff — 防止 MIME 类型嗅探
- X-Frame-Options: DENY — 防止点击劫持
- X-XSS-Protection: 0 — 现代浏览器用 CSP 替代
- Referrer-Policy — 控制 Referer 头传输范围
- Permissions-Policy — 禁用非必要 API
- Content-Security-Policy — 内容安全策略
"""

from typing import Callable

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """安全头中间件 - 为所有响应添加安全相关的 HTTP 响应头。

    中间件在请求处理完成后，在返回的响应上附加安全头。
    此中间件应作为最外层中间件注册，确保安全头覆盖所有响应。
    """

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """处理请求并在响应上附加安全头。

        Args:
            request: FastAPI 请求对象
            call_next: 下一处理阶段

        Returns:
            Response: 添加了安全头的响应
        """
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
        # 生产环境（远程访问）应考虑更严格的 CSP 策略
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
