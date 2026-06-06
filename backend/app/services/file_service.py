"""
File service - 文件操作工具函数

提供音频文件检测、格式判断、内容哈希计算和同步盘占位文件水合等基础文件操作。
"""

import time
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path


SUPPORTED_AUDIO_EXTENSIONS = {"wav", "mp3", "m4a", "flac", "aac", "ogg"}

HYDRATE_TIMEOUT = 120
HYDRATE_RETRY_DELAY = 2.0


def ensure_hydrated(path: Path, timeout: float = HYDRATE_TIMEOUT, label: str = "") -> None:
    """强制水合同步盘占位文件（触发按需下载），带重试和超时机制。

    同步盘（OneDrive/Dropbox/iCloud）的 Files On-Demand 功能会将文件"释放空间"，
    只保留占位符。首次读取占位符时，操作系统会触发云端下载（hydration），
    这个过程可能因网络延迟或同步客户端繁忙而失败。

    本函数通过尝试打开并读取文件来触发水合，失败时自动重试。

    Args:
        path: 文件路径
        timeout: 最大等待时间（秒），默认 120 秒
        label: 描述标签（用于日志）

    Raises:
        OSError: 超时后仍无法读取文件
    """
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            with path.open("rb") as f:
                f.read(1)
            return
        except OSError as e:
            last_error = e
            desc = f" ({label})" if label else ""
            if time.monotonic() + HYDRATE_RETRY_DELAY < deadline:
                time.sleep(HYDRATE_RETRY_DELAY)
    desc = f" ({label})" if label else ""
    raise OSError(
        f"Failed to hydrate file{desc} after {timeout:.0f}s: {path}\n"
        f"Last error: {last_error}\n"
        f"Hint: Check network connection and sync client status."
    )


def audio_suffix(path: Path) -> str:
    """获取文件的小写音频格式扩展名（不含点号）。

    Args:
        path: 文件路径

    Returns:
        小写扩展名（如 'mp3'、'wav'）
    """
    return path.suffix.lower().lstrip(".")


def is_supported_audio(path: Path) -> bool:
    """检查文件是否为支持的音频格式。

    Args:
        path: 文件路径

    Returns:
        True 如果扩展名在 SUPPORTED_AUDIO_EXTENSIONS 中
    """
    return audio_suffix(path) in SUPPORTED_AUDIO_EXTENSIONS


def content_hash(path: Path) -> str:
    """计算文件内容的 SHA-256 哈希值。

    用于音频文件去重，每次读取 1MB 分块避免大文件内存溢出。
    自动处理同步盘占位文件：读取失败时重试等待水合。

    Args:
        path: 文件路径

    Returns:
        SHA-256 哈希的十六进制字符串
    """
    ensure_hydrated(path, label=f"content_hash:{path.name}")

    digest = sha256()
    deadline = time.monotonic() + HYDRATE_TIMEOUT
    last_error: Exception | None = None

    while time.monotonic() < deadline:
        try:
            with path.open("rb") as file:
                for chunk in iter(lambda: file.read(1024 * 1024), b""):
                    digest.update(chunk)
            return digest.hexdigest()
        except OSError as e:
            last_error = e
            time.sleep(HYDRATE_RETRY_DELAY)

    raise OSError(
        f"Failed to hash file after {HYDRATE_TIMEOUT:.0f}s: {path}\n"
        f"Last error: {last_error}"
    )


def file_creation_time(path: Path) -> datetime:
    """获取文件的修改时间（UTC），作为录音的创建时间。

    录音文件通常不会被修改，因此 st_mtime 能准确反映录音时间。

    Args:
        path: 文件路径

    Returns:
        文件的修改时间（UTC datetime）
    """
    return datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
