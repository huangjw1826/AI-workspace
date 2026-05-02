from fastapi import APIRouter, Depends
from sqlmodel import Session, select

from app.db.database import get_session
from app.models import WatchEvent
from app.services.watch_service import watcher

router = APIRouter(prefix="/api/watch", tags=["watch"])


@router.post("/scan")
def scan_watch_dir() -> dict[str, object]:
    events = watcher.scan_once(force_stable=True)
    return {"count": len(events), "events": events}


@router.get("/events")
def list_watch_events(limit: int = 50, session: Session = Depends(get_session)) -> list[WatchEvent]:
    safe_limit = min(max(limit, 1), 200)
    return session.exec(select(WatchEvent).order_by(WatchEvent.created_at.desc()).limit(safe_limit)).all()
