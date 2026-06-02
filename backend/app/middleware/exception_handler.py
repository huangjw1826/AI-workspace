"""
统一异常处理中间件

为所有请求注入唯一 Trace ID，统一处理两大类异常：
1. AppBaseException（应用级业务异常）：返回对应的 HTTP 状态码和错误码
2. Exception（未预期的系统异常）：返回 500 内部错误，避免泄露敏感信息

Trace ID 机制确保每个错误都可以在日志中准确定位到原始异常。
"""

import traceback
import uuid
from typing import Callable

from fastapi import Request, Response
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.exceptions import AppBaseException
from app.services.runtime_log import get_logger

logger = get_logger()


def generate_trace_id() -> str:
    """生成全局唯一的 Trace ID，用于请求追踪和日志关联。

    Returns:
        UUID v4 字符串
    """
    return str(uuid.uuid4())


class ExceptionHandlerMiddleware(BaseHTTPMiddleware):
    """统一异常处理中间件。

    为每个请求生成 Trace ID，注入到 request.state 和响应头中。
    已知业务异常返回结构化错误响应，未知异常返回 500 内部错误。
    """

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """处理请求并捕获所有异常。

        流程：
        1. 生成 Trace ID 注入 request.state
        2. 正常处理请求
        3. 在响应头中注入 X-Request-ID
        4. 捕获 AppBaseException 返回结构化错误
        5. 捕获未知异常返回 500 Internal Error

        Args:
            request: FastAPI 请求对象
            call_next: 下一处理阶段

        Returns:
            Response: 正常响应或错误响应
        """
        trace_id = generate_trace_id()
        request.state.trace_id = trace_id

        try:
            response = await call_next(request)
            if hasattr(response, "headers"):
                response.headers["X-Request-ID"] = trace_id
            return response

        except AppBaseException as exc:
            # 已知业务异常 - 安全地返回业务错误信息
            logger.warning(
                "应用异常: %s [%s] trace_id=%s",
                exc.code,
                exc.message,
                trace_id,
            )
            return JSONResponse(
                status_code=exc.status_code,
                content={
                    "error": exc.message,
                    "code": exc.code,
                    "trace_id": trace_id,
                    "details": exc.details,
                },
                headers={"X-Request-ID": trace_id},
            )

        except Exception as exc:
            # 未知异常 - 记录完整堆栈，仅返回通用错误信息
            logger.exception(
                "未处理的异常: %s trace_id=%s",
                str(exc),
                trace_id,
            )
            return JSONResponse(
                status_code=500,
                content={
                    "error": "服务器内部错误",
                    "code": "INTERNAL_ERROR",
                    "trace_id": trace_id,
                },
                headers={"X-Request-ID": trace_id},
            )
