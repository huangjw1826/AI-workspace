"""
Export naming service - 导出文件名生成规则

为转写和摘要的导出文件生成规范化、安全的文件名。
规则：{录音名}_{类型}_{模板}_{时间戳}[_{后缀}].{扩展名}
所有文件名经过严格清洗，确保跨平台/云同步兼容（无特殊字符、emoji、控制字符）。
"""

import re
from datetime import datetime
from pathlib import Path


# ---------------------------------------------------------------------------
# 文件名安全规则
# ---------------------------------------------------------------------------
# 允许的字符集：
#   - 中文字符 (CJK Unified Ideographs + Ext-A + Compatibility + 全角半角符号)
#   - 英文字母 a-z A-Z
#   - 数字 0-9
#   - 安全符号：- _ . ( )
# 其他所有字符（emoji、特殊标点、控制字符等）一律替换为 _
_CJK_SAFE = (
    "一-鿿"     # CJK Unified Ideographs
    "㐀-䶿"     # CJK Extension A
    "豈-﫿"     # CJK Compatibility Ideographs
    "　-〿"     # CJK Symbols and Punctuation
    "＀-￯"     # Halfwidth and Fullwidth Forms
)
_SAFE_CHARS_RE = re.compile(
    rf"[^a-zA-Z0-9_\-\.\(\){_CJK_SAFE}]+"
)
# 连续多个下划线压缩为一个
_COLLAPSE_UNDERSCORES_RE = re.compile(r"_{2,}")
# 连续空白字符
_WHITESPACE_RE = re.compile(r"\s+")
# 非法文件名字符（Windows 传统非法字符 + 控制字符）
_LEGACY_INVALID_RE = re.compile(r'[<>:"/\\|?*\x00-\x1f]+')

# Windows 保留文件名（不区分大小写）
_WINDOWS_RESERVED_NAMES = {
    "con", "prn", "aux", "nul",
    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
}

# 文件名最大长度（不含扩展名），为路径总长留足余量
_MAX_STEM_LENGTH = 120


def _strip_emoji_prefix(name: str) -> str:
    """去除字符串开头的 emoji 和装饰符号。

    模板名如 "📋 会议纪要" → "会议纪要"
    """
    # 匹配开头的 emoji / 特殊符号 + 可选空格
    cleaned = re.sub(
        r"^[\U0001F300-\U0001FAFF☀-➿✀-➿️\U0001F900-\U0001F9FF"
        r"\U0001FA00-\U0001FA6F\U0001FA70-\U0001FAFF\U0001F600-\U0001F64F"
        r"\U0001F680-\U0001F6FF‍♀-♟⏏⏩-⏺"
        r"⤴⤵▪▫▶◀◻-◾\U00002B00-\U00002BFF"
        r"\U0001F000-\U0001F02F\U0001F0A0-\U0001F0FF\U0001F100-\U0001F64F"
        r"\U0001F780-\U0001F7FF⌚-⌛⏩-⏳⏸-⏺"
        r"◻-◾☀-⟯⤴-⤵]+\s*",
        "", name,
    )
    return cleaned.strip() or name


def safe_filename_part(value: str | None, fallback: str = "录音") -> str:
    """清理字符串为安全的文件名片段（跨平台/云同步兼容）。

    规则：
    1. 去除开头 emoji/装饰符号
    2. 替换所有非安全字符为下划线（保留中英文字母数字和 - _ . ( )）
    3. 压缩连续下划线
    4. 去除首尾空格、点号、下划线、连字符
    5. 限制最大长度
    6. Windows 保留名自动添加后缀

    Args:
        value: 原始字符串
        fallback: 空值时的回退名称

    Returns:
        安全的文件名片段
    """
    # Step 1: 去除开头 emoji
    cleaned = _strip_emoji_prefix(value or "")

    # Step 2: 替换非法字符为下划线
    cleaned = _LEGACY_INVALID_RE.sub("_", cleaned)
    cleaned = _WHITESPACE_RE.sub(" ", cleaned)
    cleaned = _SAFE_CHARS_RE.sub("_", cleaned)

    # Step 3: 压缩连续下划线
    cleaned = _COLLAPSE_UNDERSCORES_RE.sub("_", cleaned)

    # Step 4: 去除首尾特殊字符
    cleaned = cleaned.strip(" ._-")

    # Step 5: 截断过长的文件名主干
    if len(cleaned) > _MAX_STEM_LENGTH:
        cleaned = cleaned[:_MAX_STEM_LENGTH].rstrip(" ._-")

    if not cleaned:
        return fallback

    # Step 6: Windows 保留名检查
    if cleaned.lower() in _WINDOWS_RESERVED_NAMES:
        cleaned = f"{cleaned}_file"

    return cleaned


def recording_stem(filename: str | None, fallback: str = "录音") -> str:
    """提取录音文件名的主干（不含扩展名），经安全清洗。

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
    safe_ext = safe_filename_part(extension, "txt")
    return f"{stem}_转写_{filename_timestamp(created_at)}.{safe_ext}"


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
        template_name: 摘要模板名（如 "会议纪要"，自动去除 emoji 前缀）
        created_at: 摘要创建时间
        extension: 文件扩展名
        unique_suffix: 可选唯一后缀（用于区分多次摘要）

    Returns:
        规范化的摘要导出文件名
    """
    stem = recording_stem(recording_filename)
    template = safe_filename_part(template_name, "摘要")
    suffix = f"_{safe_filename_part(unique_suffix, '')}" if unique_suffix else ""
    safe_ext = safe_filename_part(extension, "txt")
    return f"{stem}_摘要_{template}_{filename_timestamp(created_at)}{suffix}.{safe_ext}"
