"""
SSE (Server-Sent Events) service - 实时事件推送系统

基于 sse-starlette 实现的任务状态实时推送，用于：
- 前端实时显示任务进度条和状态变化
- Android 端接收任务状态更新

架构：SSEService（单例）→ 管理多个 SSEClient → 每个客户端独立的 asyncio.Queue
"""
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
    """SSE 事件类型枚举。

    定义 6 种任务事件类型，客户端可据此过滤和处理不同状态变化。
    """
    TASK_STARTED = "task.started"       # 任务开始执行
    TASK_PROCESSING = "task.processing" # 任务处理中
    TASK_PROGRESS = "task.progress"     # 进度更新
    TASK_COMPLETED = "task.completed"   # 任务成功完成
    TASK_FAILED = "task.failed"         # 任务执行失败
    TASK_CANCELLED = "task.cancelled"   # 任务被取消


@dataclass
class TaskEvent:
    """SSE 事件数据载体。

    包含任务 ID、关联录音、进度、消息和时间戳。
    支持可选的 data 字典用于传递额外信息（如 result_path、error）。
    """
    event_id: str
    event_type: TaskEventType
    task_id: str
    recording_id: str
    progress: int
    message: str
    timestamp: str
    data: Optional[dict[str, Any]] = None

    def to_sse_data(self) -> dict[str, Any]:
        """转换为 SSE 客户端可消费的字典格式（event_type 转为字符串值）。"""
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
    """单个 SSE 客户端连接。

    每个浏览器/设备连接对应一个 SSEClient 实例，维护独立的 asyncio.Queue。
    客户端断开时通过 close() 标记，定期清理任务会移除过期客户端。
    """

    def __init__(self, client_id: str, request: Request) -> None:
        self.client_id = client_id
        self.request = request
        self.queue: asyncio.Queue[TaskEvent] = asyncio.Queue()
        self._closed = False

    async def put_event(self, event: TaskEvent) -> None:
        """向客户端队列投递事件。已关闭的客户端不接收。"""
        if not self._closed:
            await self.queue.put(event)

    async def close(self) -> None:
        """关闭客户端连接，投递关闭信号。"""
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
        """检查客户端是否已关闭。"""
        return self._closed


class SSEService:
    """SSE 服务单例 — 管理所有客户端的注册、注销和广播。

    特性：
    - 双重检查锁（DCL）实现线程安全的单例初始化
    - 定时清理（60 秒）：移除已断开的客户端避免内存泄漏
    - 广播容错：单个客户端投递失败不会影响其他客户端
    - 心跳机制：30 秒无事件时发送空心跳保活连接
    """

    _instance: Optional["SSEService"] = None
    _lock: asyncio.Lock = asyncio.Lock()

    def __init__(self) -> None:
        self.clients: dict[str, SSEClient] = {}
        self._cleanup_task: Optional[asyncio.Task] = None

    @classmethod
    async def get_instance(cls) -> "SSEService":
        """获取 SSE 服务单例（DCL 线程安全初始化）。"""
        if cls._instance is None:
            async with cls._lock:
                if cls._instance is None:
                    cls._instance = SSEService()
                    cls._instance._start_cleanup_task()
        return cls._instance

    def _start_cleanup_task(self) -> None:
        """启动定期清理任务（每 60 秒移除已断开的客户端）。"""
        async def cleanup_loop() -> None:
            while True:
                await asyncio.sleep(60)
                await self._cleanup_stale_clients()

        self._cleanup_task = asyncio.create_task(cleanup_loop())

    async def _cleanup_stale_clients(self) -> None:
        """清理已断开或已关闭的客户端连接。"""
        stale = [
            cid for cid, client in self.clients.items()
            if client.request is None or client.is_closed()
        ]
        for cid in stale:
            del self.clients[cid]
            logger.debug("Cleaned up stale SSE client: %s", cid)

    async def register_client(self, client: SSEClient) -> str:
        """注册新客户端，返回分配的 client_id。"""
        client_id = str(uuid4())
        self.clients[client_id] = client
        logger.debug("Registered SSE client: %s", client_id)
        return client_id

    async def unregister_client(self, client_id: str) -> None:
        """注销客户端（关闭连接后从注册表移除）。"""
        if client_id in self.clients:
            await self.clients[client_id].close()
            del self.clients[client_id]
            logger.debug("Unregistered SSE client: %s", client_id)

    async def broadcast(self, event: TaskEvent) -> None:
        """广播事件到所有已连接客户端。

        单个客户端投递失败时记录警告并自动注销该客户端。
        """
        for client_id, client in list(self.clients.items()):
            try:
                await client.put_event(event)
            except Exception:
                logger.warning("Failed to send event to client %s", client_id)
                await self.unregister_client(client_id)

    # --- 便捷广播方法 ---

    async def emit_task_started(self, task_id: str, recording_id: str, message: str = "") -> None:
        """发送任务开始事件。"""
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
        """发送任务进度事件。"""
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
        """发送任务完成事件（附带结果文件路径）。"""
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
        """发送任务失败事件（附带错误详情）。"""
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
        """生成 SSE 事件流（用于 EventSourceResponse）。

        以 30 秒为心跳间隔持续从客户端队列读取事件，
        连接关闭或取消时自动注销客户端。
        """
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
                    # 发送心跳保持连接
                    yield {"event": "heartbeat", "data": "{}"}
        except asyncio.CancelledError:
            pass
        finally:
            await self.unregister_client(client_id)


# 全局 SSE 服务单例引用
sse_service: Optional[SSEService] = None


async def get_sse_service() -> SSEService:
    """获取全局 SSE 服务实例（延迟初始化）。"""
    global sse_service
    if sse_service is None:
        sse_service = await SSEService.get_instance()
    return sse_service
