"""
Application exception hierarchy - 统一异常体系

所有业务异常继承自 AppBaseException，通过标准化的错误码和状态码
在统一异常处理中间件中转换为一致的 JSON 响应格式。
"""

from typing import Any, Optional


class AppBaseException(Exception):
    """应用异常基类。

    携带标准化的错误信息：HTTP 状态码、业务错误码、人类可读消息、调试详情。

    Attributes:
        message: 人类可读的错误描述
        code: 业务错误码（如 RECORDING_NOT_FOUND）
        status_code: HTTP 状态码
        details: 结构化错误详情（用于调试和前端展示）
    """

    def __init__(
        self,
        message: str,
        code: str = "INTERNAL_ERROR",
        status_code: int = 500,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.status_code = status_code
        self.details = details or {}

    def to_dict(self) -> dict[str, Any]:
        """转换为 API 错误响应的 JSON 格式。"""
        return {
            "error": self.message,
            "code": self.code,
            "details": self.details,
        }


class RecordingNotFoundError(AppBaseException):
    """录音记录不存在（404）。"""
    def __init__(self, recording_id: str) -> None:
        super().__init__(
            message=f"Recording not found: {recording_id}",
            code="RECORDING_NOT_FOUND",
            status_code=404,
            details={"recording_id": recording_id},
        )


class TaskFailedError(AppBaseException):
    """任务执行失败（422）。"""
    def __init__(self, task_id: str, reason: str) -> None:
        super().__init__(
            message=f"Task failed: {reason}",
            code="TASK_FAILED",
            status_code=422,
            details={"task_id": task_id, "reason": reason},
        )


class AudioProcessingError(AppBaseException):
    """音频处理失败（400）— FFmpeg 或 soundfile 错误。"""
    def __init__(self, reason: str) -> None:
        super().__init__(
            message=f"Audio processing failed: {reason}",
            code="AUDIO_PROCESSING_ERROR",
            status_code=400,
            details={"reason": reason},
        )


class LLMServiceError(AppBaseException):
    """大模型服务错误（502）— API 调用失败或超时。"""
    def __init__(self, provider: str, reason: str) -> None:
        super().__init__(
            message=f"LLM service error ({provider}): {reason}",
            code="LLM_SERVICE_ERROR",
            status_code=502,
            details={"provider": provider, "reason": reason},
        )


class StoragePathError(AppBaseException):
    """存储路径访问错误（403）— 路径遍历攻击或目录不可达。"""
    def __init__(self, path: str, reason: str) -> None:
        super().__init__(
            message=f"Storage path error: {reason}",
            code="STORAGE_PATH_ERROR",
            status_code=403,
            details={"path": path, "reason": reason},
        )


class ValidationError(AppBaseException):
    """业务参数校验错误（422）。"""
    def __init__(self, field: str, reason: str) -> None:
        super().__init__(
            message=f"Validation error: {reason}",
            code="VALIDATION_ERROR",
            status_code=422,
            details={"field": field, "reason": reason},
        )
