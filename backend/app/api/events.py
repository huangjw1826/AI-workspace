"""
SSE（Server-Sent Events）事件推送 API 模块

提供实时的服务器推送事件流，用于：
- 转写任务进度实时推送
- 摘要任务进度实时推送
- 系统状态变更通知

前端通过 EventSource API 订阅 /api/events 端点，
收到事件后更新 UI 中的进度条和状态标签。
"""

from fastapi import APIRouter, Request
from sse_starlette import EventSourceResponse

from app.services.sse_service import SSEClient, get_sse_service

router = APIRouter(tags=["events"])


@router.get("/api/events")
async def sse_events(request: Request) -> EventSourceResponse:
    """SSE 事件流订阅端点。

    前端使用 EventSource API 连接到此端点，接收实时事件推送。
    连接建立后自动注册客户端，断开时自动注销清理。

    Args:
        request: FastAPI 请求对象

    Returns:
        EventSourceResponse: SSE 事件流（持续连接，不会正常返回）
    """
    service = await get_sse_service()
    client = SSEClient(client_id="", request=request)
    client_id = await service.register_client(client)

    async def event_generator():
        """事件生成器：从队列中不断获取事件并推送给客户端。"""
        async for event in service.get_client_events(client_id):
            yield event

    return EventSourceResponse(event_generator())


@router.post("/api/events/test")
async def test_event() -> dict:
    """发送测试事件（用于调试 SSE 连接）。

    向所有已连接客户端广播一条测试事件，验证 SSE 推送是否正常工作。

    Returns:
        dict: {"status": "ok", "message": "Test event sent"}
    """
    service = await get_sse_service()
    from app.services.sse_service import TaskEvent, TaskEventType
    from uuid import uuid4
    from datetime import datetime, timezone

    await service.broadcast(TaskEvent(
        event_id=str(uuid4()),
        event_type=TaskEventType.TASK_STARTED,
        task_id="test-task",
        recording_id="test-recording",
        progress=50,
        message="测试事件",
        timestamp=datetime.now(timezone.utc).isoformat(),
    ))
    return {"status": "ok", "message": "测试事件已发送"}
