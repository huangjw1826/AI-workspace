"""
ASR (Automatic Speech Recognition) service - FunASR 语音转写引擎

使用 FunASR 的 paraformer-zh 模型进行离线中文语音转文字。
支持 VAD 语音检测、标点恢复和时间戳对齐。
通过 BoundedSemaphore 控制并发数量，避免 CPU 过载。
"""

from dataclasses import dataclass
from pathlib import Path
import re
import threading
from typing import Any

from app.config import get_settings


@dataclass
class Segment:
    """转写结果片段 - 包含时间轴、文本内容和说话人标签。

    Attributes:
        start_time: 片段开始时间（秒，相对于音频开头）
        end_time: 片段结束时间（秒）
        text: 转写文本内容
        speaker: 说话人标签，默认 "speaker_1"（说话人分离功能未启用）
    """
    start_time: float
    end_time: float
    text: str
    speaker: str = "speaker_1"


class ASRService:
    """FunASR 语音转写服务。

    核心流程：
    1. 加载 FunASR AutoModel（一次性初始化，含 VAD + 标点恢复）
    2. 获取并发信号量槽位（限制同时转写的任务数）
    3. 调用 model.generate() 进行转写
    4. 解析时间戳和文本，生成有序 Segment 列表

    模型采用懒加载策略，首次调用时自动下载并加载到内存。
    并发控制通过类级 BoundedSemaphore 实现，确保进程内最多 N 个转写任务同时运行。
    """

    _semaphore: threading.BoundedSemaphore | None = None
    _semaphore_limit: int | None = None
    _semaphore_lock = threading.Lock()

    def __init__(self) -> None:
        self.settings = get_settings()
        self._model: Any | None = None

    def package_available(self) -> bool:
        """检查 FunASR 包是否已安装。"""
        try:
            import funasr  # noqa: F401
            return True
        except Exception:
            return False

    def _load_model(self) -> Any:
        """加载 FunASR AutoModel（懒加载，首次调用时初始化）。

        模型组合：
        - ASR 模型：paraformer-zh（语音识别）
        - VAD 模型：fsmn-vad（语音活动检测）
        - 标点模型：ct-punc（标点恢复）
        - 说话人模型：cam++（说话人分离，可选）

        模型默认从 Modelscope 下载到 MODEL_DIR，由 MODELSCOPE_CACHE 环境变量指定。
        """
        if self._model is not None:
            return self._model
        from funasr import AutoModel

        kwargs: dict[str, Any] = {
            "model": self.settings.asr_model,
            "vad_model": self.settings.asr_vad_model,
            "punc_model": self.settings.asr_punc_model,
            "device": self.settings.asr_device,
            "model_revision": "master",
            "vad_model_revision": "master",
            "punc_model_revision": "master",
        }

        if self.settings.asr_enable_diarization:
            kwargs["spk_model"] = self.settings.asr_spk_model
            kwargs["spk_model_revision"] = "master"

        self._model = AutoModel(**kwargs)
        return self._model

    def transcribe(self, audio_path: Path) -> list[Segment]:
        """对音频文件执行语音转写。

        流程：
        1. 检查 FunASR 可用性
        2. 获取并发信号量槽位（等待空闲）
        3. 加载模型并执行 generate
        4. 从结果中提取时间戳和文本
        5. 按句子边界 + 时间戳对齐生成有序片段列表
        6. 如启用说话人分离，从 sentences 中提取 speaker 标签

        Args:
            audio_path: 归一化后的音频文件路径（16kHz 单声道 WAV）

        Returns:
            按时间顺序排列的 Segment 列表，失败时返回单个空文本片段

        Raises:
            RuntimeError: FunASR 未安装
        """
        if not self.package_available():
            raise RuntimeError("FunASR is not installed. Run backend dependency setup first.")

        with self._asr_slot():
            model = self._load_model()
            result = model.generate(input=str(audio_path), batch_size_s=300)
        if not result:
            return []

        first = result[0] if isinstance(result, list) else result

        if self.settings.asr_enable_diarization:
            sentences = first.get("sentence_info", []) if isinstance(first, dict) else []
            if sentences:
                segments: list[Segment] = []
                for sent in sentences:
                    speaker_id = sent.get("spk", 0)
                    speaker = f"speaker_{int(speaker_id) + 1}" if speaker_id is not None else "speaker_1"
                    segments.append(Segment(
                        start_time=round(float(sent.get("start", 0)) / 1000, 3),
                        end_time=round(float(sent.get("end", 0)) / 1000, 3),
                        text=str(sent.get("text", "")).strip(),
                        speaker=speaker,
                    ))
                return [s for s in segments if s.text]

        text = first.get("text", "") if isinstance(first, dict) else str(first)
        timestamps = first.get("timestamp", []) if isinstance(first, dict) else []

        if timestamps and isinstance(timestamps, list):
            segments = self._segments_from_text_and_timestamps(text, timestamps)
            if segments:
                return segments

        return [Segment(start_time=0, end_time=0, text=text)]

    def _segments_from_text_and_timestamps(
        self,
        text: str,
        timestamps: list[Any],
    ) -> list[Segment]:
        """将 FunASR 输出的文本和时间戳转换为有序片段列表。

        策略：
        1. 过滤有效时间戳（list/tuple 至少 2 个元素）
        2. 按标点符号将文本拆分为句子
        3. 利用有效字符数（排除空格和标点）将时间戳映射到每个句子
        4. 相邻片段之间不留空隙，确保时间轴连续

        Args:
            text: FunASR 的完整转写文本
            timestamps: FunASR 的时间戳列表，每项为 [start_ms, end_ms]

        Returns:
            按时间顺序排列的 Segment 列表，已过滤空文本片段
        """
        valid_timestamps = [
            item for item in timestamps if isinstance(item, (list, tuple)) and len(item) >= 2
        ]
        if not text.strip() or not valid_timestamps:
            return []

        chunks = self._split_text(text)
        segments: list[Segment] = []
        cursor = 0
        last_end = 0.0

        for chunk in chunks:
            timed_count = self._timed_char_count(chunk)
            if timed_count <= 0:
                # 纯标点/空格片段：合并到上一个片段
                if segments:
                    segments[-1].text += chunk
                continue

            start_index = min(cursor, len(valid_timestamps) - 1)
            end_index = min(cursor + timed_count - 1, len(valid_timestamps) - 1)
            start_ms = float(valid_timestamps[start_index][0])
            end_ms = float(valid_timestamps[end_index][1])
            start_time = max(last_end, start_ms / 1000)
            end_time = max(start_time, end_ms / 1000)

            segments.append(
                Segment(
                    start_time=round(start_time, 3),
                    end_time=round(end_time, 3),
                    text=chunk.strip(),
                )
            )
            cursor += timed_count
            last_end = end_time

        # 最后片段的时间戳可能不完整，用最后有效时间戳补充
        if cursor < len(valid_timestamps) and segments:
            segments[-1].end_time = round(float(valid_timestamps[-1][1]) / 1000, 3)

        return [segment for segment in segments if segment.text]

    def _split_text(self, text: str, max_chars: int = 120) -> list[str]:
        """按标点符号拆分文本为句子，超过 max_chars 的句子进一步切分。

        支持的句子分隔符：。！？!?；;
        """
        sentences = [
            item.strip()
            for item in re.findall(r".+?(?:[。！？!?；;]|$)", text)
            if item.strip()
        ]
        chunks: list[str] = []
        for sentence in sentences:
            if len(sentence) <= max_chars:
                chunks.append(sentence)
                continue
            for start in range(0, len(sentence), max_chars):
                chunk = sentence[start : start + max_chars].strip()
                if chunk:
                    chunks.append(chunk)
        return chunks

    def _timed_char_count(self, text: str) -> int:
        """计算文本中的有效字符数（排除空格、标点和特殊符号）。

        用于将 FunASR 的时间戳映射到对应字符位置。
        排除的字符：\\s，。！？!?；;：:、,.…—-
        """
        return sum(1 for char in text if not re.match(r"[\s，。！？!?；;：:、,.…—-]", char))

    def _asr_slot(self) -> threading.BoundedSemaphore:
        """获取 ASR 并发信号量。

        通过类级 BoundedSemaphore 控制同时运行的转写任务数，
        由 asr_max_concurrency 配置项决定上限，默认值为 1。
        使用线程锁保证信号量初始化的原子性。
        """
        limit = max(1, int(self.settings.asr_max_concurrency))
        with self._semaphore_lock:
            if self.__class__._semaphore is None or self.__class__._semaphore_limit != limit:
                self.__class__._semaphore = threading.BoundedSemaphore(limit)
                self.__class__._semaphore_limit = limit
            return self.__class__._semaphore
