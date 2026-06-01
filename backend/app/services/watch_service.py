"""
Watch service - 目录监控服务

定时扫描指定目录，自动发现新音频文件并入库。
实现文件稳定性检测、SHA-256 去重和监控事件审计。

监控逻辑：
1. 按配置间隔（默认 10 秒）定时扫描
2. 文件稳定性检测：大小和修改时间连续 2 次扫描一致才处理
3. force_stable 模式（手动扫描）：等待文件年龄至少 2 秒
4. 通过 content_hash 去重：已处理文件不再重复导入
5. 每个文件产生一条 WatchEvent 记录（imported/duplicate_skipped/skipped/error）
"""

import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from sqlmodel import Session, select

from app.config import get_settings
from app.db.database import engine
from app.models import Recording, WatchEvent
from app.services.file_service import audio_suffix, content_hash, is_supported_audio

SYNC_IGNORE_SUFFIXES = {".tmp", ".temp", ".part", ".crdownload", ".download"}
SYNC_IGNORE_PREFIXES = ("~$",)
SYNC_CONFLICT_KEYWORDS = ("conflicted copy",)


def _is_sync_temp_file(path: Path) -> bool:
    name = path.name
    if name.startswith(".") or name.startswith("._"):
        return True
    for prefix in SYNC_IGNORE_PREFIXES:
        if name.startswith(prefix):
            return True
    suffix_lower = path.suffix.lower()
    if suffix_lower in SYNC_IGNORE_SUFFIXES:
        return True
    name_lower = name.lower()
    for keyword in SYNC_CONFLICT_KEYWORDS:
        if keyword in name_lower:
            return True
    return False


@dataclass
class FileSnapshot:
    """文件快照 — 记录文件大小、修改时间和稳定计数。

    连续 scan 中相同的大小和修改时间会增加 stable_count，
    达到 2 次后认为文件稳定，可以处理。
    """
    size: int
    mtime: float
    stable_count: int = 1


class DirectoryWatcher:
    """目录监控器 — 异步后台任务，定时扫描并入库新音频。

    使用 asyncio.create_task 运行后台循环，支持配置的热更新
    （每次循环读取最新 settings）。

    重要：监控只负责发现并入库新音频，不会自动触发转写或摘要，
    避免误处理大量文件。
    """

    def __init__(self) -> None:
        self._snapshots: dict[str, FileSnapshot] = {}
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        """启动监控循环（幂等，不会重复启动）。"""
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self.run())

    async def stop(self) -> None:
        """停止监控循环，等待任务优雅退出。"""
        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        self._task = None

    async def run(self) -> None:
        """后台监控循环：按配置间隔定时扫描。

        每次循环读取最新 settings，支持运行时配置变更。
        扫描异常被静默吞掉，不影响后续扫描。
        """
        while True:
            settings = get_settings()
            interval = max(2, int(settings.watch_interval_seconds or 10))
            if settings.watch_enabled:
                try:
                    await asyncio.to_thread(self.scan_once, False)
                except Exception:
                    pass
            await asyncio.sleep(interval)

    def scan_once(self, force_stable: bool = True) -> list[WatchEvent]:
        """执行一次目录扫描。

        Args:
            force_stable: True=手动触发（要求文件年龄>=2秒），False=自动扫描（要求稳定2次）

        Returns:
            本次扫描产生的 WatchEvent 列表
        """
        settings = get_settings()
        watch_dir = settings.resolved_watch_dir
        if watch_dir is None:
            return []
        if not watch_dir.exists() or not watch_dir.is_dir():
            with Session(engine) as session:
                event = WatchEvent(
                    file_path=str(watch_dir),
                    filename=watch_dir.name,
                    status="error",
                    reason="监控目录不存在或不是文件夹",
                )
                session.add(event)
                session.commit()
                session.refresh(event)
                return [event]

        files = watch_dir.rglob("*") if settings.watch_recursive else watch_dir.glob("*")
        events: list[WatchEvent] = []
        for path in files:
            if not path.is_file():
                continue
            if _is_sync_temp_file(path):
                continue
            event = self._process_path(path, force_stable=force_stable)
            if event is not None:
                events.append(event)
        return events

    def _process_path(self, path: Path, force_stable: bool) -> WatchEvent | None:
        """处理单个文件路径：稳定性检测 → 格式校验 → 去重 → 入库。

        Args:
            path: 文件路径
            force_stable: 手动扫描模式

        Returns:
            WatchEvent 或 None（文件不稳定/已处理/无需处理）
        """
        stat = path.stat()
        snapshot_key = str(path.resolve())
        previous = self._snapshots.get(snapshot_key)

        # 文件稳定性检测：大小和修改时间未变化则增加稳定计数
        if previous and previous.size == stat.st_size and previous.mtime == stat.st_mtime:
            stable_count = previous.stable_count + 1
        else:
            stable_count = 1
        self._snapshots[snapshot_key] = FileSnapshot(stat.st_size, stat.st_mtime, stable_count)

        # 手动扫描：文件创建至少 2 秒
        # 自动扫描：需要连续 stable_count 次以上稳定（默认 2，同步盘建议 ≥3）
        age_seconds = datetime.now(timezone.utc).timestamp() - stat.st_mtime
        threshold = get_settings().watch_stable_count
        if not force_stable and stable_count < threshold:
            return None
        if force_stable and age_seconds < 2:
            return None

        # 格式检查
        if not is_supported_audio(path):
            return self._record_event_once(
                path=path,
                status="skipped",
                reason="不支持的文件格式",
                file_size=stat.st_size,
                file_mtime=stat.st_mtime,
            )

        # 去重检查（SHA-256 内容哈希）
        digest = content_hash(path)
        with Session(engine) as session:
            existing_event = session.exec(
                select(WatchEvent)
                .where(WatchEvent.file_path == str(path))
                .where(WatchEvent.content_hash == digest)
                .where(WatchEvent.status.in_(["imported", "duplicate_skipped"]))
            ).first()
            if existing_event is not None:
                return None

            existing_recording = session.exec(select(Recording).where(Recording.content_hash == digest)).first()
            if existing_recording is not None:
                # 文件已存在，记录重复跳过事件
                event = WatchEvent(
                    file_path=str(path),
                    filename=path.name,
                    status="duplicate_skipped",
                    reason="文件内容已处理过",
                    recording_id=existing_recording.id,
                    duplicate_of_id=existing_recording.id,
                    content_hash=digest,
                    file_size=stat.st_size,
                    file_mtime=stat.st_mtime,
                )
                session.add(event)
                session.commit()
                session.refresh(event)
                return event

            # 创建新录音记录
            suffix = audio_suffix(path)
            recording = Recording(
                filename=path.name,
                original_path=str(path),
                format=suffix,
                content_hash=digest,
                file_size_bytes=stat.st_size,
                source_mtime=stat.st_mtime,
                source_type="watch",
                source_path=str(path),
            )
            session.add(recording)
            session.commit()
            session.refresh(recording)

            event = WatchEvent(
                file_path=str(path),
                filename=path.name,
                status="imported",
                reason="已加入录音列表",
                recording_id=recording.id,
                content_hash=digest,
                file_size=stat.st_size,
                file_mtime=stat.st_mtime,
            )
            session.add(event)
            session.commit()
            session.refresh(event)
            return event

    def _record_event_once(self, path: Path, status: str, reason: str,
                           file_size: int | None = None, file_mtime: float | None = None) -> WatchEvent | None:
        """记录事件（去重：同一路径+同一状态+同一 mtime 不重复记录）。"""
        with Session(engine) as session:
            existing = session.exec(
                select(WatchEvent)
                .where(WatchEvent.file_path == str(path))
                .where(WatchEvent.file_mtime == file_mtime)
                .where(WatchEvent.status == status)
            ).first()
            if existing is not None:
                return None
            event = WatchEvent(
                file_path=str(path),
                filename=path.name,
                status=status,
                reason=reason,
                file_size=file_size,
                file_mtime=file_mtime,
            )
            session.add(event)
            session.commit()
            session.refresh(event)
            return event


# 全局目录监控器实例
watcher = DirectoryWatcher()
