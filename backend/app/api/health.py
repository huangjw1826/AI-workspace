import importlib.util
import sys

from fastapi import APIRouter

from app.config import get_settings
from app.services.audio_service import AudioService
from app.services.runtime_log import recent_errors

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> dict[str, object]:
    settings = get_settings()
    return {
        "status": "ok",
        "python": sys.version.split()[0],
        "ffmpeg": AudioService().ffmpeg_available(),
        "funasr": importlib.util.find_spec("funasr") is not None,
        "data_dir": str(settings.resolved_data_dir),
        "model_dir": str(settings.resolved_model_dir),
        "asr_model": settings.asr_model,
        "llm_provider": settings.normalized_llm_provider,
        "llm_base_url": settings.resolved_llm_base_url,
        "llm_model": settings.resolved_llm_model,
        "llm_configured": bool(settings.resolved_llm_api_key),
        "log_dir": str(settings.resolved_log_dir),
        "recent_errors": recent_errors(),
    }
