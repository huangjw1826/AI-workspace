"""
Audio service - 音频处理服务

基于 FFmpeg 的音频格式转换和元数据提取。
用于转写前的音频归一化（统一转换为 16kHz 单声道 WAV）。
"""

import json
import re
import subprocess
from pathlib import Path

from app.config import get_settings


class AudioService:
    """音频处理工具 — FFmpeg 操作封装。

    核心功能：
    - normalize: 将任意格式音频转为 16kHz 单声道 WAV
    - duration_seconds: 读取音频时长（优先 soundfile，fallback FFmpeg）
    - ffmpeg_available: 检查 FFmpeg 是否可执行
    """

    def __init__(self) -> None:
        self.settings = get_settings()

    def normalize(self, source: Path, recording_id: str) -> Path:
        """将音频文件归一化为单声道 16kHz WAV。

        命令：ffmpeg -y -i <source> -ac 1 -ar 16000 <output>
        输出到 data/normalized/{recording_id}.wav

        Args:
            source: 原始音频文件路径
            recording_id: 录音记录 ID（用作输出文件名）

        Returns:
            归一化后的 WAV 文件路径

        Raises:
            subprocess.CalledProcessError: FFmpeg 执行失败
            subprocess.TimeoutExpired: 超过 ffmpeg_timeout_seconds 超时
        """
        output = self.settings.resolved_data_dir / "normalized" / f"{recording_id}.wav"
        command = [
            self.settings.ffmpeg_bin,
            "-y",
            "-i",
            str(source),
            "-ac",
            "1",
            "-ar",
            "16000",
            str(output),
        ]
        subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
            timeout=self.settings.ffmpeg_timeout_seconds,
        )
        return output

    def duration_seconds(self, source: Path) -> float | None:
        """获取音频时长（秒）。

        策略：优先使用 soundfile（快速读取文件头，支持 WAV/FLAC/OGG），
        失败时 fallback 到 ffprobe（支持所有格式：AAC/MP3/M4A 等）。

        Args:
            source: 音频文件路径（支持 WAV/MP3/FLAC 等）

        Returns:
            时长秒数，失败返回 None
        """
        # 优先 soundfile（快速，但仅支持部分格式）
        try:
            import soundfile as sf
            return float(sf.info(str(source)).duration)
        except Exception:
            pass

        # fallback: ffprobe（支持所有 FFmpeg 能解码的格式）
        ffprobe_bin = self._ffprobe_path()
        command = [
            ffprobe_bin,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "json",
            str(source),
        ]
        try:
            result = subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                timeout=self.settings.ffmpeg_timeout_seconds,
            )
            payload = json.loads(result.stdout)
            return float(payload["format"]["duration"])
        except Exception:
            return None

    def _ffprobe_path(self) -> str:
        """推导 ffprobe 可执行文件路径（与 ffmpeg 同目录）。"""
        ffmpeg = self.settings.ffmpeg_bin
        if ffmpeg in ("ffmpeg", "ffmpeg.exe"):
            return "ffprobe.exe" if ffmpeg.endswith(".exe") else "ffprobe"
        # 自定义路径：替换文件名中的 ffmpeg 为 ffprobe
        return re.sub(r"ffmpeg", "ffprobe", ffmpeg, flags=re.IGNORECASE)

    def ffmpeg_available(self) -> bool:
        """检查 FFmpeg 是否可用（执行 -version 命令）。"""
        try:
            subprocess.run(
                [self.settings.ffmpeg_bin, "-version"],
                check=True,
                capture_output=True,
                text=True,
                timeout=10,
            )
            return True
        except Exception:
            return False
