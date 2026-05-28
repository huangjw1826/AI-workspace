"""
File service - 文件操作工具函数

提供音频文件检测、格式判断和内容哈希计算等基础文件操作。
"""

from hashlib import sha256
from pathlib import Path


# 支持的音频文件扩展名
SUPPORTED_AUDIO_EXTENSIONS = {"wav", "mp3", "m4a", "flac", "aac", "ogg"}


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

    Args:
        path: 文件路径

    Returns:
        SHA-256 哈希的十六进制字符串
    """
    digest = sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
