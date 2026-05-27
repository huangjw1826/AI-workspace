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
    return str(uuid.uuid4())


class ExceptionHandlerMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        trace_id = generate_trace_id()
        request.state.trace_id = trace_id

        try:
            response = await call_next(request)
            if hasattr(response, "headers"):
                response.headers["X-Request-ID"] = trace_id
            return response

        except AppBaseException as exc:
            logger.warning(
                "App exception: %s [%s] trace_id=%s",
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
            logger.exception(
                "Unhandled exception: %s trace_id=%s",
                str(exc),
                trace_id,
            )
            return JSONResponse(
                status_code=500,
                content={
                    "error": "Internal server error",
                    "code": "INTERNAL_ERROR",
                    "trace_id": trace_id,
                },
                headers={"X-Request-ID": trace_id},
            )
