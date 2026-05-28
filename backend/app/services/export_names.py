"""
Export naming service - 导出文件名生成规则

为转写和摘要的导出文件生成规范化、安全的文件名。
规则：{录音名}_{类型}_{模板}_{时间戳}[_{后缀}].{扩展名}
文件名中的非法字符自动替换为下划线。
"""

import re
from datetime import datetime
from pathlib import Path


# 匹配文件名中不允许的字符：< > : " / \ | ? * 和 ASCII 控制字符
INVALID_FILENAME_RE = re.compile(r'[<>:"/\\|?*\x00-\x1f]+')
# 匹配连续空白字符
WHITESPACE_RE = re.compile(r"\s+")


def safe_filename_part(value: str | None, fallback: str = "录音") -> str:
    """清理字符串为安全的文件名片段。

    替换非法字符为下划线，合并连续空白，去除首尾空格/点号/下划线。

    Args:
        value: 原始字符串
        fallback: 空值时的回退名称

    Returns:
        安全的文件名片段
    """
    cleaned = INVALID_FILENAME_RE.sub("_", value or "")
    cleaned = WHITESPACE_RE.sub(" ", cleaned).strip(" ._")
    return cleaned or fallback


def recording_stem(filename: str | None, fallback: str = "录音") -> str:
    """提取录音文件名的主干（不含扩展名）。

    Args:
        filename: 完整文件名
        fallback: 空值回退

    Returns:
        安全的文件名主干
    """
    return safe_filename_part(Path(filename or fallback).stem, fallback)


def filename_timestamp(value: datetime | None) -> str:
    """生成时间戳字符串（YYYYMMDD-HHMMSS 格式，本地时区）。

    Args:
        value: 时间对象，None 时使用当前时间

    Returns:
        格式化时间戳字符串
    """
    timestamp = value or datetime.now().astimezone()
    return timestamp.astimezone().strftime("%Y%m%d-%H%M%S")


def transcript_filename(recording_filename: str | None, created_at: datetime | None, extension: str) -> str:
    """生成转写导出文件名。

    格式：{录音名}_转写_{时间戳}.{扩展名}

    Args:
        recording_filename: 原始录音文件名
        created_at: 录音创建时间
        extension: 文件扩展名（不含点号）

    Returns:
        规范化的转写导出文件名
    """
    stem = recording_stem(recording_filename)
    return f"{stem}_转写_{filename_timestamp(created_at)}.{extension}"


def summary_filename(
    recording_filename: str | None,
    template_name: str | None,
    created_at: datetime | None,
    extension: str,
    unique_suffix: str | None = None,
) -> str:
    """生成摘要导出文件名。

    格式：{录音名}_摘要_{模板}_{时间戳}[_{后缀}].{扩展名}

    Args:
        recording_filename: 原始录音文件名
        template_name: 摘要模板名（如 "会议纪要"）
        created_at: 摘要创建时间
        extension: 文件扩展名
        unique_suffix: 可选唯一后缀（用于区分多次摘要）

    Returns:
        规范化的摘要导出文件名
    """
    stem = recording_stem(recording_filename)
    template = safe_filename_part(template_name, "摘要")
    suffix = f"_{safe_filename_part(unique_suffix, '')}" if unique_suffix else ""
    return f"{stem}_摘要_{template}_{filename_timestamp(created_at)}{suffix}.{extension}"
