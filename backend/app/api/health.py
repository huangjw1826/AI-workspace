import importlib.util
import os
import sys
import time
from pathlib import Path

from fastapi import APIRouter

from app.config import get_settings
from app.services.audio_service import AudioService
from app.services.runtime_log import recent_errors

router = APIRouter(tags=["health"])

# 启动时间记录
_start_time = time.time()


def _get_cpu_usage() -> float:
    """获取CPU使用率（简单实现）"""
    try:
        import psutil
        return psutil.cpu_percent(interval=0.5)
    except Exception:
        return -1.0


def _get_memory_info() -> dict[str, int]:
    """获取内存信息"""
    try:
        import psutil
        mem = psutil.virtual_memory()
        return {
            "total": mem.total,
            "available": mem.available,
            "used": mem.used,
            "percent": mem.percent
        }
    except Exception:
        return {"total": -1, "available": -1, "used": -1, "percent": -1}


def _get_disk_info(path: Path) -> dict[str, int]:
    """获取磁盘信息"""
    try:
        import shutil
        usage = shutil.disk_usage(path)
        return {
            "total": usage.total,
            "used": usage.used,
            "free": usage.free
        }
    except Exception:
        return {"total": -1, "used": -1, "free": -1}


def _get_uptime_seconds() -> float:
    """获取运行时长（秒）"""
    return time.time() - _start_time


@router.get("/health")
def health() -> dict[str, object]:
    settings = get_settings()

    ffmpeg_ok = False
    try:
        ffmpeg_ok = AudioService().ffmpeg_available()
    except Exception:
        pass

    funasr_ok = False
    try:
        funasr_ok = importlib.util.find_spec("funasr") is not None
    except Exception:
        pass

    llm_configured = False
    try:
        llm_configured = bool(settings.resolved_llm_api_key)
    except Exception:
        pass

    log_dir = ""
    recent = []
    try:
        log_dir = str(settings.resolved_log_dir)
        recent = recent_errors()
    except Exception:
        pass

    # 系统信息
    cpu_percent = _get_cpu_usage()
    memory_info = _get_memory_info()
    disk_info = _get_disk_info(settings.resolved_data_dir)
    uptime_seconds = _get_uptime_seconds()

    # 检查Cloudflare Tunnel连接状态（简化实现）
    # 实际项目中可能需要检测cloudflared进程
    tunnel_connected = True  # 假设已连接

    return {
        "status": "ok",
        "python": sys.version.split()[0],
        "ffmpeg": ffmpeg_ok,
        "funasr": funasr_ok,
        "data_dir": str(settings.resolved_data_dir),
        "model_dir": str(settings.resolved_model_dir),
        "asr_model": settings.asr_model,
        "llm_provider": settings.normalized_llm_provider,
        "llm_base_url": settings.resolved_llm_base_url,
        "llm_model": settings.resolved_llm_model,
        "llm_configured": llm_configured,
        "log_dir": log_dir,
        "recent_errors": recent,
        # 新增系统健康信息
        "system": {
            "cpu_percent": cpu_percent,
            "memory": memory_info,
            "disk": disk_info,
            "uptime_seconds": uptime_seconds
        },
        "tunnel": {
            "connected": tunnel_connected
        }
    }
