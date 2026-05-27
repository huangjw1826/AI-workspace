import asyncio
import json
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

from fastapi import Request
from sse_starlette.sse import EventSourceResponse

from app.services.runtime_log import get_logger

logger = get_logger()


class TaskEventType(str, Enum):
    TASK_STARTED = "task.started"
    TASK_PROCESSING = "task.processing"
    TASK_PROGRESS = "task.progress"
    TASK_COMPLETED = "task.completed"
    TASK_FAILED = "task.failed"
    TASK_CANCELLED = "task.cancelled"


@dataclass
class TaskEvent:
    event_id: str
    event_type: TaskEventType
    task_id: str
    recording_id: str
    progress: int
    message: str
    timestamp: str
    data: Optional[dict[str, Any]] = None

    def to_sse_data(self) -> dict[str, Any]:
        result = {
            "event_id": self.event_id,
            "event_type": self.event_type.value,
            "task_id": self.task_id,
            "recording_id": self.recording_id,
            "progress": self.progress,
            "message": self.message,
            "timestamp": self.timestamp,
        }
        if self.data:
            result["data"] = self.data
        return result


class SSEClient:
    def __init__(self, client_id: str, request: Request) -> None:
        self.client_id = client_id
        self.request = request
        self.queue: asyncio.Queue[TaskEvent] = asyncio.Queue()
        self._closed = False

    async def put_event(self, event: TaskEvent) -> None:
        if not self._closed:
            await self.queue.put(event)

    async def close(self) -> None:
        self._closed = True
        await self.queue.put(TaskEvent(
            event_id=str(uuid4()),
            event_type=TaskEventType.TASK_CANCELLED,
            task_id="",
            recording_id="",
            progress=0,
            message="Connection closed",
            timestamp=datetime.now(timezone.utc).isoformat(),
        ))

    def is_closed(self) -> bool:
        return self._closed


class SSEService:
    _instance: Optional["SSEService"] = None
    _lock: asyncio.Lock = asyncio.Lock()

    def __init__(self) -> None:
        self.clients: dict[str, SSEClient] = {}
        self._cleanup_task: Optional[asyncio.Task] = None

    @classmethod
    async def get_instance(cls) -> "SSEService":
        if cls._instance is None:
            async with cls._lock:
                if cls._instance is None:
                    cls._instance = SSEService()
                    cls._instance._start_cleanup_task()
        return cls._instance

    def _start_cleanup_task(self) -> None:
        async def cleanup_loop() -> None:
            while True:
                await asyncio.sleep(60)
                await self._cleanup_stale_clients()

        self._cleanup_task = asyncio.create_task(cleanup_loop())

    async def _cleanup_stale_clients(self) -> None:
        stale = [
            cid for cid, client in self.clients.items()
            if client.request is None or client.is_closed()
        ]
        for cid in stale:
            del self.clients[cid]
            logger.debug("Cleaned up stale SSE client: %s", cid)

    async def register_client(self, client: SSEClient) -> str:
        client_id = str(uuid4())
        self.clients[client_id] = client
        logger.debug("Registered SSE client: %s", client_id)
        return client_id

    async def unregister_client(self, client_id: str) -> None:
        if client_id in self.clients:
            await self.clients[client_id].close()
            del self.clients[client_id]
            logger.debug("Unregistered SSE client: %s", client_id)

    async def broadcast(self, event: TaskEvent) -> None:
        for client_id, client in list(self.clients.items()):
            try:
                await client.put_event(event)
            except Exception:
                logger.warning("Failed to send event to client %s", client_id)
                await self.unregister_client(client_id)

    async def emit_task_started(self, task_id: str, recording_id: str, message: str = "") -> None:
        event = TaskEvent(
            event_id=str(uuid4()),
            event_type=TaskEventType.TASK_STARTED,
            task_id=task_id,
            recording_id=recording_id,
            progress=0,
            message=message or "Task started",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        await self.broadcast(event)

    async def emit_task_progress(self, task_id: str, recording_id: str, progress: int, message: str = "") -> None:
        event = TaskEvent(
            event_id=str(uuid4()),
            event_type=TaskEventType.TASK_PROGRESS,
            task_id=task_id,
            recording_id=recording_id,
            progress=progress,
            message=message or f"Progress: {progress}%",
            timestamp=datetime.now(timezone.utc).isoformat(),
        )
        await self.broadcast(event)

    async def emit_task_completed(self, task_id: str, recording_id: str, result_path: str = "") -> None:
        event = TaskEvent(
            event_id=str(uuid4()),
            event_type=TaskEventType.TASK_COMPLETED,
            task_id=task_id,
            recording_id=recording_id,
            progress=100,
            message="Task completed",
            timestamp=datetime.now(timezone.utc).isoformat(),
            data={"result_path": result_path} if result_path else None,
        )
        await self.broadcast(event)

    async def emit_task_failed(self, task_id: str, recording_id: str, error_message: str) -> None:
        event = TaskEvent(
            event_id=str(uuid4()),
            event_type=TaskEventType.TASK_FAILED,
            task_id=task_id,
            recording_id=recording_id,
            progress=0,
            message=f"Task failed: {error_message}",
            timestamp=datetime.now(timezone.utc).isoformat(),
            data={"error": error_message},
        )
        await self.broadcast(event)

    async def get_client_events(self, client_id: str):
        if client_id not in self.clients:
            return

        client = self.clients[client_id]
        try:
            while not client.is_closed():
                try:
                    event = await asyncio.wait_for(client.queue.get(), timeout=30)
                    yield {
                        "event": event.event_type.value,
                        "data": json.dumps(event.to_sse_data(), ensure_ascii=False),
                    }
                except asyncio.TimeoutError:
                    yield {"event": "heartbeat", "data": "{}"}
        except asyncio.CancelledError:
            pass
        finally:
            await self.unregister_client(client_id)


sse_service: Optional[SSEService] = None


async def get_sse_service() -> SSEService:
    global sse_service
    if sse_service is None:
        sse_service = await SSEService.get_instance()
    return sse_service
