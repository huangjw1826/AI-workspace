"""
目录监控 API 模块 - 监控目录管理接口

提供目录监控的手动触发扫描和事件查询功能。
自动扫描由 watch_service.DirectoryWatcher 后台循环驱动，
此模块仅提供手动触发和结果查看接口。
"""

from fastapi import APIRouter, Depends
from sqlmodel import Session, select

from app.db.database import get_session
from app.models import WatchEvent
from app.services.watch_service import watcher

router = APIRouter(prefix="/api/watch", tags=["watch"])


@router.post("/scan")
def scan_watch_dir() -> dict[str, object]:
    """手动触发一次目录扫描（强制稳定模式）。

    与自动扫描的区别：
    - 手动扫描要求文件年龄至少 2 秒（force_stable=True）
    - 自动扫描要求文件连续 stable_count 次扫描保持一致

    Returns:
        dict: 包含扫描到的事件数量和事件列表
    """
    events = watcher.scan_once(force_stable=True)
    return {"count": len(events), "events": events}


@router.get("/events")
def list_watch_events(limit: int = 50, session: Session = Depends(get_session)) -> list[WatchEvent]:
    """获取目录监控事件列表。

    按时间倒序排列，limit 上限 200 条。
    事件类型包括：imported（已入库）、duplicate_skipped（重复跳过）、
    skipped（格式不支持）、error（处理出错）。

    Args:
        limit: 返回事件数量上限（默认 50，范围 1-200）
        session: 数据库会话

    Returns:
        list[WatchEvent]: 监控事件列表
    """
    safe_limit = min(max(limit, 1), 200)
    return session.exec(select(WatchEvent).order_by(WatchEvent.created_at.desc()).limit(safe_limit)).all()
