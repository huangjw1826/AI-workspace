"""
认证中间件 - 本地/远程请求分离鉴权

设计原则：
- PC 浏览器本地使用无需 Token
- Android 客户端远程访问必须携带 X-API-Token 请求头
- 本地回环地址（127.0.0.1 / ::1 / localhost）自动免检
- 反向代理来自本机连接通过 Host 头判断

支持两级 Token 认证：
1. 传统单 Token 模式（API_TOKEN 环境变量）
2. 数据库多 Token 模式（ApiToken 表，支持多个设备独立 Token）

访问日志：通过 Token 认证的远程请求自动记录访问日志。
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

# 本地回环主机名集合（不区分大小写）
_LOOPBACK_HOSTS = frozenset({"127.0.0.1", "::1", "localhost"})


def _host_is_loopback(host: str) -> bool:
    """判断主机名是否为本地回环地址。

    Args:
        host: 主机名字符串（可能包含 IPv6 方括号）

    Returns:
        True 表示是本地回环地址
    """
    return host.strip().lower().strip("[]") in _LOOPBACK_HOSTS


def _host_header_is_local(request: Request) -> bool:
    """从 Host 头判断请求是否来自本地。

    从 Host 头中提取主机名部分（去除端口），
    支持 IPv4、IPv6（方括号格式）和域名形式。

    Args:
        request: FastAPI 请求对象

    Returns:
        True 表示请求的 Host 是本地回环地址
    """
    host_header = request.headers.get("host", "").strip()
    if host_header.startswith("["):
        host = host_header[1:].split("]", 1)[0]
    elif host_header.count(":") == 1:
        host = host_header.rsplit(":", 1)[0]
    else:
        host = host_header
    return _host_is_loopback(host)


def is_local_request(request: Request) -> bool:
    """判断是否为本地请求，返回 True 表示来自本机。

    判断策略：
    1. 检查 Origin 头（如果有的话）：从 Origin 解析 hostname 并校验
    2. 检查 Host 头：直接判断是否为本地回环地址

    Notes:
        - 反向代理通常从 127.0.0.1 连接到 uvicorn
        - 因此客户端 IP 不可用于鉴权判断
        - 没有 Origin 头的公网请求视为远程请求

    Args:
        request: FastAPI 请求对象

    Returns:
        True 表示来自本机的本地请求
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
    """将远程访问记录写入访问日志表，用于审计追踪。

    Args:
        token_id: 使用的 Token ID（如有）
        device_name: 设备名称（来自 Token 记录）
        method: HTTP 方法（GET/POST/PUT/DELETE 等）
        path: 请求路径
        status_code: HTTP 状态码
        ip_address: 客户端 IP 地址
        user_agent: 客户端 User-Agent
    """
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
        logger.warning("写入访问日志失败: %s", e)


def _validate_db_token(candidate: str) -> Optional[ApiToken]:
    """验证 Token 是否在数据库中存在且已启用，并更新最后使用时间。

    使用条件：验证通过后自动更新 last_used_at 时间戳，
    方便用户查看每个 Token 的最后活跃情况。

    Args:
        candidate: 待验证的 Token 字符串

    Returns:
        验证通过返回 ApiToken 记录，否则返回 None
    """
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
        logger.warning("数据库 Token 验证失败: %s", e)
    return None


class LocalBypassTokenMiddleware(BaseHTTPMiddleware):
    """本地绕过鉴权中间件 - 远程 /api/* 请求需要 X-API-Token。

    设计：
    - 本地回环请求：绕过 Token 检查（PC 浏览器工作流）
    - 远程 Android 客户端：必须发送配置的 Token
    - 未配置 Token 时：路由保持开放（避免锁住首次设置的用户）

    支持传统单 Token（API_TOKEN 环境变量）和
    数据库多 Token（ApiToken 表）两种认证方式。
    """

    def __init__(
        self,
        app,
        api_token: str = "",
        protected_prefixes: tuple[str, ...] = ("/api",),
    ) -> None:
        """初始化中间件。

        Args:
            app: ASGI 应用实例
            api_token: 环境变量配置的传统单 Token（可选）
            protected_prefixes: 需要保护的路由前缀（默认 /api）
        """
        super().__init__(app)
        self.api_token = api_token.strip()
        self.protected_prefixes = protected_prefixes

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """请求分发：判断是否需要认证，通过后继续处理。

        中间件处理流程：
        1. OPTIONS 预检请求直接放行
        2. 本地请求直接放行
        3. 非 /api/* 路径直接放行
        4. 没有 Token 时检查是否需要认证
        5. 有 Token 时验证（单 Token / 多 Token）
        6. 记录访问日志

        Args:
            request: FastAPI 请求对象
            call_next: 下一处理阶段

        Returns:
            Response: 放行后的响应或 403 拒绝响应
        """
        if request.method == "OPTIONS":
            return await call_next(request)

        if is_local_request(request):
            response = await call_next(request)
            return response

        if not self._is_protected_path(request.url.path):
            return await call_next(request)

        # 远程请求需要 Token
        candidate = request.headers.get("X-API-Token", "")
        if not candidate:
            if self._needs_auth():
                logger.warning(
                    "远程请求 %s 被拒绝：缺少 API Token (Origin: %s, Host: %s)",
                    request.url.path,
                    request.headers.get("origin", "<none>"),
                    request.headers.get("host", "<none>"),
                )
                return JSONResponse({"detail": "API Token 无效"}, status_code=403)
            return await call_next(request)

        token_record = None
        if self.api_token and compare_digest(candidate, self.api_token):
            pass  # 传统单 Token 验证通过
        else:
            token_record = _validate_db_token(candidate)
            if not token_record:
                logger.warning(
                    "远程请求 %s 被拒绝：API Token 无效 (Origin: %s, Host: %s)",
                    request.url.path,
                    request.headers.get("origin", "<none>"),
                    request.headers.get("host", "<none>"),
                )
                return JSONResponse({"detail": "API Token 无效"}, status_code=403)

        response = await call_next(request)

        # 记录访问日志（仅远程请求）
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
        """判断请求路径是否需要 Token 认证保护。

        Args:
            path: 请求路径

        Returns:
            True 表示该路径需要认证保护
        """
        return any(path == prefix or path.startswith(f"{prefix}/") for prefix in self.protected_prefixes)

    def _needs_auth(self) -> bool:
        """检查系统是否配置了认证机制。

        只要有任一 Token（环境变量或数据库）存在，就需要认证。
        没有任何 Token 时，所有路由保持开放（方便首次设置）。

        Returns:
            True 表示需要 Token 认证
        """
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
