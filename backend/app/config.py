import os
from functools import lru_cache
from pathlib import Path

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


LLM_PROVIDER_DEFAULTS = {
    "deepseek": {
        "base_url": "https://api.deepseek.com",
        "model": "deepseek-chat",
        "temperature": 0.2,
        "top_p": None,
    },
    "tongyi": {
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
        "temperature": 0.2,
        "top_p": None,
    },
    "qwen": {
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
        "temperature": 0.2,
        "top_p": None,
    },
    "mimo": {
        "base_url": "https://token-plan-cn.xiaomimimo.com/v1",
        "model": "mimo-v2.5",
        "temperature": 1.0,
        "top_p": 0.95,
    },
}


class Settings(BaseSettings):
    app_env: str = "local"
    app_host: str = "127.0.0.1"
    app_port: int = 8000

    data_dir: Path = Path("../data")
    model_dir: Path = Path("../models/funasr")
    log_dir: Path = Path("../logs")
    transcript_dir: Path = Path("../data/transcripts")
    summary_dir: Path = Path("../data/summaries")
    ffmpeg_bin: str = "ffmpeg"

    asr_device: str = "cpu"
    asr_model: str = "paraformer-zh"
    asr_vad_model: str = "fsmn-vad"
    asr_punc_model: str = "ct-punc"
    asr_timestamp_model: str = "fa-zh"
    asr_enable_diarization: bool = False
    asr_max_concurrency: int = 1

    llm_provider: str = "deepseek"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = ""
    llm_max_completion_tokens: int = 2048
    llm_temperature: float | None = None
    llm_top_p: float | None = None
    mimo_api_key: str = ""
    mimo_thinking: str = "disabled"

    watch_enabled: bool = False
    watch_dir: str = ""
    watch_recursive: bool = True
    watch_interval_seconds: int = 10

    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    @field_validator("llm_temperature", "llm_top_p", mode="before")
    @classmethod
    def empty_float_as_none(cls, value: object) -> object:
        if value == "":
            return None
        return value

    @property
    def resolved_data_dir(self) -> Path:
        return self.data_dir.resolve()

    @property
    def resolved_model_dir(self) -> Path:
        return self.model_dir.resolve()

    @property
    def resolved_log_dir(self) -> Path:
        return self.log_dir.resolve()

    @property
    def resolved_transcript_dir(self) -> Path:
        return self.transcript_dir.expanduser().resolve()

    @property
    def resolved_summary_dir(self) -> Path:
        return self.summary_dir.expanduser().resolve()

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def resolved_watch_dir(self) -> Path | None:
        if not self.watch_dir.strip():
            return None
        return Path(self.watch_dir).expanduser().resolve()

    @property
    def normalized_llm_provider(self) -> str:
        provider = self.llm_provider.strip().lower()
        return provider or "deepseek"

    @property
    def llm_provider_defaults(self) -> dict[str, object]:
        return LLM_PROVIDER_DEFAULTS.get(
            self.normalized_llm_provider,
            LLM_PROVIDER_DEFAULTS["deepseek"],
        )

    @property
    def resolved_llm_api_key(self) -> str:
        if self.normalized_llm_provider == "mimo" and self.mimo_api_key.strip():
            return self.mimo_api_key.strip()
        return self.llm_api_key.strip()

    @property
    def resolved_llm_base_url(self) -> str:
        if self.llm_base_url.strip():
            return self.llm_base_url.strip()
        return str(self.llm_provider_defaults["base_url"])

    @property
    def resolved_llm_model(self) -> str:
        if self.llm_model.strip():
            return self.llm_model.strip()
        return str(self.llm_provider_defaults["model"])

    @property
    def resolved_llm_temperature(self) -> float | None:
        if self.llm_temperature is not None:
            return self.llm_temperature
        value = self.llm_provider_defaults.get("temperature")
        return float(value) if value is not None else None

    @property
    def resolved_llm_top_p(self) -> float | None:
        if self.llm_top_p is not None:
            return self.llm_top_p
        value = self.llm_provider_defaults.get("top_p")
        return float(value) if value is not None else None


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    os.environ.setdefault("MODELSCOPE_CACHE", str(settings.resolved_model_dir))
    ffmpeg_path = Path(settings.ffmpeg_bin)
    if ffmpeg_path.is_file():
        ffmpeg_dir = str(ffmpeg_path.parent)
        current_path = os.environ.get("PATH", "")
        if ffmpeg_dir not in current_path.split(os.pathsep):
            os.environ["PATH"] = ffmpeg_dir + os.pathsep + current_path
    for path in [
        settings.resolved_data_dir,
        settings.resolved_model_dir,
        settings.resolved_log_dir,
        settings.resolved_data_dir / "recordings",
        settings.resolved_data_dir / "normalized",
        settings.resolved_transcript_dir,
        settings.resolved_summary_dir,
    ]:
        path.mkdir(parents=True, exist_ok=True)
    return settings
