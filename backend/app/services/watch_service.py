import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from sqlmodel import Session, select

from app.config import get_settings
from app.db.database import engine
from app.models import Recording, WatchEvent
from app.services.file_service import audio_suffix, content_hash, is_supported_audio


@dataclass
class FileSnapshot:
    size: int
    mtime: float
    stable_count: int = 1


class DirectoryWatcher:
    def __init__(self) -> None:
        self._snapshots: dict[str, FileSnapshot] = {}
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self.run())

    async def stop(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        self._task = None

    async def run(self) -> None:
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
            event = self._process_path(path, force_stable=force_stable)
            if event is not None:
                events.append(event)
        return events

    def _process_path(self, path: Path, force_stable: bool) -> WatchEvent | None:
        stat = path.stat()
        snapshot_key = str(path.resolve())
        previous = self._snapshots.get(snapshot_key)
        if previous and previous.size == stat.st_size and previous.mtime == stat.st_mtime:
            stable_count = previous.stable_count + 1
        else:
            stable_count = 1
        self._snapshots[snapshot_key] = FileSnapshot(stat.st_size, stat.st_mtime, stable_count)

        age_seconds = datetime.now(timezone.utc).timestamp() - stat.st_mtime
        if not force_stable and stable_count < 2:
            return None
        if force_stable and age_seconds < 2:
            return None

        if not is_supported_audio(path):
            return self._record_event_once(
                path=path,
                status="skipped",
                reason="不支持的文件格式",
                file_size=stat.st_size,
                file_mtime=stat.st_mtime,
            )

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

    def _record_event_once(
        self,
        path: Path,
        status: str,
        reason: str,
        file_size: int | None = None,
        file_mtime: float | None = None,
    ) -> WatchEvent | None:
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


watcher = DirectoryWatcher()
