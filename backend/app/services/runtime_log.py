"""
Runtime logging service - 运行时日志配置和查询

配置 RotatingFileHandler 进行日志轮转（1MB/文件，保留 3 个备份）。
提供最近错误查询功能，供健康检查 API 使用。
"""

from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

from app.config import get_settings

LOG_NAME = "ai_recorder"


def configure_logging() -> Path:
    """配置应用日志：RotatingFileHandler + 格式化输出。

    - 日志文件：logs/ai-recorder.log
    - 轮转策略：超过 1MB 自动轮转，保留 3 个备份
    - 格式：YYYY-MM-DD HH:MM:SS,mmm LEVEL NAME message
    - 容错：文件不可写时回退到 NullHandler（不中断应用启动）

    Returns:
        日志文件的完整路径
    """
    settings = get_settings()
    log_path = settings.resolved_log_dir / "ai-recorder.log"
    logger = logging.getLogger(LOG_NAME)
    logger.setLevel(logging.INFO)
    # 避免重复添加 handler
    if not any(isinstance(handler, RotatingFileHandler) and handler.baseFilename == str(log_path) for handler in logger.handlers):
        try:
            handler: logging.Handler = RotatingFileHandler(
                log_path,
                maxBytes=1_000_000,
                backupCount=3,
                encoding="utf-8",
            )
        except OSError:
            # 文件不可写时使用 NullHandler 避免应用启动失败
            handler = logging.NullHandler()
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
        logger.addHandler(handler)
    return log_path


def get_logger() -> logging.Logger:
    """获取应用根日志记录器。

    首次调用时自动配置日志（幂等）。
    """
    configure_logging()
    return logging.getLogger(LOG_NAME)


def recent_errors(limit: int = 8) -> list[str]:
    """获取最近的错误日志行。

    从 ai-recorder.log 和 backend.err.log 中搜索含 ERROR/CRITICAL 的行，
    返回最近的 limit 条（从尾部计数）。

    Args:
        limit: 最多返回的错误行数（默认 8）

    Returns:
        错误日志行列表（最新在末尾）
    """
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
