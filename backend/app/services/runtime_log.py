from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

from app.config import get_settings

LOG_NAME = "ai_recorder"


def configure_logging() -> Path:
    settings = get_settings()
    log_path = settings.resolved_log_dir / "ai-recorder.log"
    logger = logging.getLogger(LOG_NAME)
    logger.setLevel(logging.INFO)
    if not any(isinstance(handler, RotatingFileHandler) and handler.baseFilename == str(log_path) for handler in logger.handlers):
        try:
            handler: logging.Handler = RotatingFileHandler(
                log_path,
                maxBytes=1_000_000,
                backupCount=3,
                encoding="utf-8",
            )
        except OSError:
            handler = logging.NullHandler()
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
        logger.addHandler(handler)
    return log_path


def get_logger() -> logging.Logger:
    configure_logging()
    return logging.getLogger(LOG_NAME)


def recent_errors(limit: int = 8) -> list[str]:
    settings = get_settings()
    configure_logging()
    candidates = [
        settings.resolved_log_dir / "ai-recorder.log",
        settings.resolved_log_dir / "backend.err.log",
    ]
    errors: list[str] = []
    for log_path in candidates:
        if not log_path.exists() or not log_path.is_file():
            continue
        lines = log_path.read_text(encoding="utf-8", errors="replace").splitlines()
        errors.extend(line for line in lines if " ERROR " in line or " CRITICAL " in line)
    return errors[-limit:]
