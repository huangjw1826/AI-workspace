from typing import Any, Optional


class AppBaseException(Exception):
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
        return {
            "error": self.message,
            "code": self.code,
            "details": self.details,
        }


class RecordingNotFoundError(AppBaseException):
    def __init__(self, recording_id: str) -> None:
        super().__init__(
            message=f"Recording not found: {recording_id}",
            code="RECORDING_NOT_FOUND",
            status_code=404,
            details={"recording_id": recording_id},
        )


class TaskFailedError(AppBaseException):
    def __init__(self, task_id: str, reason: str) -> None:
        super().__init__(
            message=f"Task failed: {reason}",
            code="TASK_FAILED",
            status_code=422,
            details={"task_id": task_id, "reason": reason},
        )


class AudioProcessingError(AppBaseException):
    def __init__(self, reason: str) -> None:
        super().__init__(
            message=f"Audio processing failed: {reason}",
            code="AUDIO_PROCESSING_ERROR",
            status_code=400,
            details={"reason": reason},
        )


class LLMServiceError(AppBaseException):
    def __init__(self, provider: str, reason: str) -> None:
        super().__init__(
            message=f"LLM service error ({provider}): {reason}",
            code="LLM_SERVICE_ERROR",
            status_code=502,
            details={"provider": provider, "reason": reason},
        )


class StoragePathError(AppBaseException):
    def __init__(self, path: str, reason: str) -> None:
        super().__init__(
            message=f"Storage path error: {reason}",
            code="STORAGE_PATH_ERROR",
            status_code=403,
            details={"path": path, "reason": reason},
        )


class ValidationError(AppBaseException):
    def __init__(self, field: str, reason: str) -> None:
        super().__init__(
            message=f"Validation error: {reason}",
            code="VALIDATION_ERROR",
            status_code=422,
            details={"field": field, "reason": reason},
        )