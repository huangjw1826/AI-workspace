"""
Configuration management via environment variables (.env file).

Uses pydantic-settings for type-safe configuration loading with validation.
All paths are relative to backend/ and resolved at first access via properties.
"""

import os
from functools import lru_cache
from pathlib import Path

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


# 大模型提供商预设：定义每个提供商的默认接口地址、模型名和参数
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
    """应用配置 - 从 .env 文件和系统环境变量加载。

    所有路径配置相对于 backend/ 目录，通过 resolved_* 属性返回绝对路径。
    LLM 配置支持多级回退：显式配置 > 提供商预设 > 默认值。
    """

    # --- 应用基本配置 ---
    app_env: str = "local"
    app_host: str = "127.0.0.1"
    app_port: int = 8000

    # --- 数据目录路径 ---
    data_dir: Path = Path("../data")
    model_dir: Path = Path("../models/funasr")
    log_dir: Path = Path("../logs")
    transcript_dir: Path = Path("../data/transcripts")
    summary_dir: Path = Path("../data/summaries")

    # --- FFmpeg 配置 ---
    ffmpeg_bin: str = "ffmpeg"
    ffmpeg_timeout_seconds: int = 600

    # --- ASR（语音转写）配置 ---
    asr_device: str = "cpu"
    asr_model: str = "paraformer-zh"
    asr_vad_model: str = "fsmn-vad"
    asr_punc_model: str = "ct-punc"
    asr_timestamp_model: str = "fa-zh"
    asr_enable_diarization: bool = False
    asr_spk_model: str = "cam++"
    asr_max_concurrency: int = 1

    # --- LLM（大模型摘要）配置 ---
    llm_provider: str = "deepseek"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = ""
    llm_max_completion_tokens: int = 2048
    llm_timeout_seconds: int = 60
    llm_retry_attempts: int = 3
    llm_temperature: float | None = None
    llm_top_p: float | None = None
    mimo_api_key: str = ""
    mimo_thinking: str = "disabled"

    # --- 目录监控配置 ---
    watch_enabled: bool = False
    watch_dir: str = ""
    watch_recursive: bool = True
    watch_interval_seconds: int = 10
    watch_stable_count: int = 2

    # --- 安全配置 ---
    api_token: str = ""
    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173"

    # --- Cloudflare Tunnel 远程访问配置 ---
    remote_access_enabled: bool = True
    remote_access_provider: str = "cloudflare"
    remote_access_hostname: str = ""
    remote_access_tunnel_name: str = "ai-recorder"
    remote_access_tunnel_id: str = ""
    remote_access_config_path: str = ""
    remote_access_pid_path: str = ""
    remote_access_log_path: str = ""
    remote_access_auto_start: bool = True

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    @field_validator("llm_temperature", "llm_top_p", mode="before")
    @classmethod
    def empty_float_as_none(cls, value: object) -> object:
        """将空字符串转为 None，避免 pydantic float 解析报错。"""
        if value == "":
            return None
        return value

    # --- 路径解析属性 ---

    @property
    def resolved_data_dir(self) -> Path:
        """数据根目录的绝对路径。"""
        return self.data_dir.resolve()

    @property
    def resolved_model_dir(self) -> Path:
        """FunASR 模型缓存目录的绝对路径。"""
        return self.model_dir.resolve()

    @property
    def resolved_log_dir(self) -> Path:
        """日志目录的绝对路径。"""
        return self.log_dir.resolve()

    @property
    def resolved_transcript_dir(self) -> Path:
        """转写 JSON 文件存储目录的绝对路径。"""
        return self.transcript_dir.expanduser().resolve()

    @property
    def resolved_summary_dir(self) -> Path:
        """摘要 Markdown 文件存储目录的绝对路径。"""
        return self.summary_dir.expanduser().resolve()

    @property
    def cors_origin_list(self) -> list[str]:
        """解析 CORS 允许的域名列表。"""
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def resolved_watch_dir(self) -> Path | None:
        """监控目录的绝对路径，未配置时返回 None。"""
        if not self.watch_dir.strip():
            return None
        return Path(self.watch_dir).expanduser().resolve()

    # --- LLM 配置解析属性 ---

    @property
    def normalized_llm_provider(self) -> str:
        """归一化的大模型提供商标识（小写，去空格）。"""
        provider = self.llm_provider.strip().lower()
        return provider or "deepseek"

    @property
    def llm_provider_defaults(self) -> dict[str, object]:
        """当前提供商的预设参数（base_url、model、temperature、top_p）。"""
        return LLM_PROVIDER_DEFAULTS.get(
            self.normalized_llm_provider,
            LLM_PROVIDER_DEFAULTS["deepseek"],
        )

    @property
    def resolved_llm_api_key(self) -> str:
        """解析后的 API Key：MiMo 用专用字段，其他用通用字段。"""
        if self.normalized_llm_provider == "mimo" and self.mimo_api_key.strip():
            return self.mimo_api_key.strip()
        return self.llm_api_key.strip()

    @property
    def resolved_llm_base_url(self) -> str:
        """解析后的 API 地址：显式配置优先，否则用提供商预设。"""
        if self.llm_base_url.strip():
            return self.llm_base_url.strip()
        return str(self.llm_provider_defaults["base_url"])

    @property
    def resolved_llm_model(self) -> str:
        """解析后的模型名：显式配置优先，否则用提供商预设。"""
        if self.llm_model.strip():
            return self.llm_model.strip()
        return str(self.llm_provider_defaults["model"])

    @property
    def resolved_llm_temperature(self) -> float | None:
        """解析后的 temperature：显式配置优先，否则用提供商预设。"""
        if self.llm_temperature is not None:
            return self.llm_temperature
        value = self.llm_provider_defaults.get("temperature")
        return float(value) if value is not None else None

    @property
    def resolved_llm_top_p(self) -> float | None:
        """解析后的 top_p：显式配置优先，否则用提供商预设。"""
        if self.llm_top_p is not None:
            return self.llm_top_p
        value = self.llm_provider_defaults.get("top_p")
        return float(value) if value is not None else None


@lru_cache
def get_settings() -> Settings:
    """获取应用配置单例（带缓存）。

    执行初始化操作：
    1. 设置 Modelscope 模型缓存路径
    2. 将 FFmpeg 所在目录加入系统 PATH
    3. 确保所有数据目录存在（自动创建）
    """
    settings = Settings()
    os.environ.setdefault("MODELSCOPE_CACHE", str(settings.resolved_model_dir))
    ffmpeg_path = Path(settings.ffmpeg_bin)
    if ffmpeg_path.is_file():
        ffmpeg_dir = str(ffmpeg_path.parent)
        current_path = os.environ.get("PATH", "")
        if ffmpeg_dir not in current_path.split(os.pathsep):
            os.environ["PATH"] = ffmpeg_dir + os.pathsep + current_path
    # 确保所有必要目录存在
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
