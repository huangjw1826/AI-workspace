from fastapi import APIRouter, Request
from sse_starlette import EventSourceResponse

from app.services.sse_service import SSEClient, get_sse_service

router = APIRouter(tags=["events"])


@router.get("/api/events")
async def sse_events(request: Request) -> EventSourceResponse:
    service = await get_sse_service()
    client = SSEClient(client_id="", request=request)
    client_id = await service.register_client(client)

    async def event_generator():
        async for event in service.get_client_events(client_id):
            yield event

    return EventSourceResponse(event_generator())


@router.post("/api/events/test")
async def test_event() -> dict:
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
        message="Test event",
        timestamp=datetime.now(timezone.utc).isoformat(),
    ))
    return {"status": "ok", "message": "Test event sent"}
