from pathlib import Path
from time import perf_counter

from fastapi import APIRouter, HTTPException
from openai import OpenAI
from pydantic import BaseModel, Field

from app.config import LLM_PROVIDER_DEFAULTS, get_settings

router = APIRouter(prefix="/api/settings", tags=["settings"])


class LLMSettingsRead(BaseModel):
    provider: str
    base_url: str
    model: str
    configured: bool
    api_key_masked: str
    mimo_thinking: str
    max_completion_tokens: int
    temperature: float | None
    top_p: float | None
    providers: dict[str, dict[str, object]]


class LLMSettingsUpdate(BaseModel):
    provider: str = Field(pattern="^(deepseek|tongyi|qwen|mimo)$")
    api_key: str = ""
    base_url: str = ""
    model: str = ""
    mimo_thinking: str = Field(default="disabled", pattern="^(enabled|disabled)$")
    max_completion_tokens: int = Field(default=2048, ge=1, le=131072)
    temperature: float | None = Field(default=None, ge=0, le=1.5)
    top_p: float | None = Field(default=None, ge=0.01, le=1.0)


class WatchSettingsRead(BaseModel):
    enabled: bool
    watch_dir: str
    recursive: bool
    interval_seconds: int
    exists: bool


class WatchSettingsUpdate(BaseModel):
    enabled: bool = False
    watch_dir: str = ""
    recursive: bool = True
    interval_seconds: int = Field(default=10, ge=2, le=3600)


class StorageSettingsRead(BaseModel):
    transcript_dir: str
    summary_dir: str
    transcript_exists: bool
    summary_exists: bool


class StorageSettingsUpdate(BaseModel):
    transcript_dir: str = ""
    summary_dir: str = ""


def _mask_secret(value: str) -> str:
    if not value:
        return ""
    if len(value) <= 8:
        return "*" * len(value)
    return f"{value[:4]}...{value[-4:]}"


def _read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _write_env(path: Path, updates: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    existing_lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    seen: set[str] = set()
    output: list[str] = []

    for line in existing_lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            output.append(line)
            continue
        key = stripped.split("=", 1)[0].strip()
        if key in updates:
            output.append(f"{key}={updates[key]}")
            seen.add(key)
        else:
            output.append(line)

    for key, value in updates.items():
        if key not in seen:
            output.append(f"{key}={value}")

    path.write_text("\n".join(output) + "\n", encoding="utf-8")


def _settings_payload() -> LLMSettingsRead:
    settings = get_settings()
    return LLMSettingsRead(
        provider=settings.normalized_llm_provider,
        base_url=settings.resolved_llm_base_url,
        model=settings.resolved_llm_model,
        configured=bool(settings.resolved_llm_api_key),
        api_key_masked=_mask_secret(settings.resolved_llm_api_key),
        mimo_thinking=settings.mimo_thinking,
        max_completion_tokens=settings.llm_max_completion_tokens,
        temperature=settings.resolved_llm_temperature,
        top_p=settings.resolved_llm_top_p,
        providers=LLM_PROVIDER_DEFAULTS,
    )


@router.get("/llm")
def get_llm_settings() -> LLMSettingsRead:
    return _settings_payload()


@router.put("/llm")
def update_llm_settings(payload: LLMSettingsUpdate) -> LLMSettingsRead:
    settings = get_settings()
    env_path = Path(".env")
    current_env = _read_env(env_path)

    updates = {
        "LLM_PROVIDER": payload.provider,
        "LLM_BASE_URL": payload.base_url,
        "LLM_MODEL": payload.model,
        "LLM_MAX_COMPLETION_TOKENS": str(payload.max_completion_tokens),
        "LLM_TEMPERATURE": "" if payload.temperature is None else str(payload.temperature),
        "LLM_TOP_P": "" if payload.top_p is None else str(payload.top_p),
        "MIMO_THINKING": payload.mimo_thinking,
    }

    if payload.api_key:
        if payload.provider == "mimo":
            updates["MIMO_API_KEY"] = payload.api_key
        else:
            updates["LLM_API_KEY"] = payload.api_key
    else:
        if payload.provider == "mimo":
            updates["MIMO_API_KEY"] = current_env.get("MIMO_API_KEY", settings.mimo_api_key)
        else:
            updates["LLM_API_KEY"] = current_env.get("LLM_API_KEY", settings.llm_api_key)

    _write_env(env_path, updates)
    get_settings.cache_clear()
    return _settings_payload()


@router.post("/llm/test")
def test_llm_connectivity() -> dict[str, object]:
    settings = get_settings()
    if not settings.resolved_llm_api_key:
        return {
            "ok": False,
            "provider": settings.normalized_llm_provider,
            "base_url": settings.resolved_llm_base_url,
            "model": settings.resolved_llm_model,
            "message": "API key is not configured.",
        }

    started = perf_counter()
    try:
        request = {
            "model": settings.resolved_llm_model,
            "messages": [
                {"role": "system", "content": "You are a connectivity checker."},
                {"role": "user", "content": "Reply with OK."},
            ],
            "max_completion_tokens": 16,
            "temperature": settings.resolved_llm_temperature,
        }
        if settings.resolved_llm_top_p is not None:
            request["top_p"] = settings.resolved_llm_top_p
        if settings.normalized_llm_provider == "mimo":
            request["extra_body"] = {"thinking": {"type": settings.mimo_thinking}}

        client = OpenAI(
            api_key=settings.resolved_llm_api_key,
            base_url=settings.resolved_llm_base_url,
            timeout=30,
        )
        response = client.chat.completions.create(**request)
        content = response.choices[0].message.content or ""
        return {
            "ok": True,
            "provider": settings.normalized_llm_provider,
            "base_url": settings.resolved_llm_base_url,
            "model": settings.resolved_llm_model,
            "latency_ms": round((perf_counter() - started) * 1000),
            "message": content[:200],
        }
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail={
                "ok": False,
                "provider": settings.normalized_llm_provider,
                "base_url": settings.resolved_llm_base_url,
                "model": settings.resolved_llm_model,
                "latency_ms": round((perf_counter() - started) * 1000),
                "message": str(exc),
            },
        ) from exc


def _watch_settings_payload() -> WatchSettingsRead:
    settings = get_settings()
    watch_dir = str(settings.resolved_watch_dir) if settings.resolved_watch_dir else ""
    return WatchSettingsRead(
        enabled=settings.watch_enabled,
        watch_dir=watch_dir,
        recursive=settings.watch_recursive,
        interval_seconds=settings.watch_interval_seconds,
        exists=bool(settings.resolved_watch_dir and settings.resolved_watch_dir.is_dir()),
    )


@router.get("/watch")
def get_watch_settings() -> WatchSettingsRead:
    return _watch_settings_payload()


@router.put("/watch")
def update_watch_settings(payload: WatchSettingsUpdate) -> WatchSettingsRead:
    watch_dir = payload.watch_dir.strip()
    if payload.enabled and not watch_dir:
        raise HTTPException(status_code=400, detail="启用监控前请填写录音目录")
    if watch_dir:
        path = Path(watch_dir).expanduser().resolve()
        if not path.exists() or not path.is_dir():
            raise HTTPException(status_code=400, detail="录音目录不存在或不是文件夹")
        watch_dir = str(path)

    _write_env(
        Path(".env"),
        {
            "WATCH_ENABLED": "true" if payload.enabled else "false",
            "WATCH_DIR": watch_dir,
            "WATCH_RECURSIVE": "true" if payload.recursive else "false",
            "WATCH_INTERVAL_SECONDS": str(payload.interval_seconds),
        },
    )
    get_settings.cache_clear()
    return _watch_settings_payload()


def _storage_settings_payload() -> StorageSettingsRead:
    settings = get_settings()
    return StorageSettingsRead(
        transcript_dir=str(settings.resolved_transcript_dir),
        summary_dir=str(settings.resolved_summary_dir),
        transcript_exists=settings.resolved_transcript_dir.is_dir(),
        summary_exists=settings.resolved_summary_dir.is_dir(),
    )


@router.get("/storage")
def get_storage_settings() -> StorageSettingsRead:
    return _storage_settings_payload()


@router.put("/storage")
def update_storage_settings(payload: StorageSettingsUpdate) -> StorageSettingsRead:
    transcript_dir = payload.transcript_dir.strip()
    summary_dir = payload.summary_dir.strip()
    if not transcript_dir or not summary_dir:
        raise HTTPException(status_code=400, detail="转写和摘要保存目录都不能为空")

    transcript_path = Path(transcript_dir).expanduser().resolve()
    summary_path = Path(summary_dir).expanduser().resolve()
    try:
        transcript_path.mkdir(parents=True, exist_ok=True)
        summary_path.mkdir(parents=True, exist_ok=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"无法创建保存目录：{exc}") from exc
    if not transcript_path.is_dir() or not summary_path.is_dir():
        raise HTTPException(status_code=400, detail="保存路径必须是文件夹")

    _write_env(
        Path(".env"),
        {
            "TRANSCRIPT_DIR": str(transcript_path),
            "SUMMARY_DIR": str(summary_path),
        },
    )
    get_settings.cache_clear()
    return _storage_settings_payload()
