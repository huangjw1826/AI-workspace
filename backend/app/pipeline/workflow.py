"""
Workflow orchestration - 任务执行流水线

定义转写和摘要两种核心任务的完整执行流程。
通用执行模板 (_execute_pipeline) 处理任务认领、SSE 事件推送和错误恢复。
具体业务逻辑由各任务函数注入。
"""

import json
import os
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from sqlalchemy import update as sa_update
from sqlmodel import Session, delete, select

from app.db.database import engine
from app.models import Recording, Summary, Task, TranscriptSegment
from app.config import get_settings
from app.services.asr_service import ASRService
from app.services.audio_service import AudioService
from app.services.export_names import summary_filename
from app.services.file_service import ensure_hydrated
from app.services.runtime_log import get_logger
from app.services.summary_service import SUMMARY_TEMPLATES, SummaryService

logger = get_logger()

_sse_service = None


async def _get_sse_service():
    global _sse_service
    if _sse_service is None:
        from app.services.sse_service import get_sse_service as _get_sse
        _sse_service = await _get_sse()
    return _sse_service


# =============================================================================
# 数据库/任务辅助
# =============================================================================

def _now() -> datetime:
    return datetime.now(timezone.utc)


def _update_task(session: Session, task: Task, **values: object) -> None:
    for key, value in values.items():
        setattr(task, key, value)
    task.updated_at = _now()
    session.add(task)
    session.commit()
    session.refresh(task)


def _claim_task(session: Session, task_id: str, progress: int = 10) -> bool:
    result = session.execute(
        sa_update(Task)
        .where(Task.id == task_id)
        .where(Task.status == "queued")
        .values(status="running", progress=progress, started_at=_now(), updated_at=_now())
    )
    session.commit()
    return result.rowcount > 0


def _is_cancelled(session: Session, task: Task) -> bool:
    session.refresh(task)
    return task.status == "cancelled"


def _finish_cancelled(session: Session, task: Task, recording: Recording | None = None) -> None:
    task.completed_at = task.completed_at or _now()
    task.updated_at = _now()
    session.add(task)
    if recording is not None and recording.status in {"queued", "normalizing", "transcribing"}:
        recording.status = "uploaded"
        recording.updated_at = _now()
        session.add(recording)
    session.commit()


# =============================================================================
# 异步执行辅助
# =============================================================================

def _run_async(coro):
    """在同步上下文中安全执行异步协程（用于 SSE 事件发送）。"""
    import asyncio
    import threading

    def _get_or_create_loop():
        try:
            loop = asyncio.get_event_loop()
            if not loop.is_closed():
                return loop
        except RuntimeError:
            pass
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        return loop

    try:
        loop = _get_or_create_loop()
        if loop.is_running():
            def _run_in_thread():
                new_loop = asyncio.new_event_loop()
                try:
                    asyncio.set_event_loop(new_loop)
                    new_loop.run_until_complete(coro)
                except Exception as e:
                    logger.exception("Async task failed in background thread", exc_info=e)
                finally:
                    if not new_loop.is_closed():
                        new_loop.close()
            threading.Thread(target=_run_in_thread, daemon=True).start()
        else:
            loop.run_until_complete(coro)
    except Exception as e:
        logger.exception("Async task failed", exc_info=e)


# =============================================================================
# SSE 事件发送
# =============================================================================

async def _emit(task_id: str, recording_id: str, event: str, **kwargs: object) -> None:
    try:
        service = await _get_sse_service()
        if event == "started":
            await service.emit_task_started(task_id, recording_id, str(kwargs.get("message", "")))
        elif event == "progress":
            await service.emit_task_progress(task_id, recording_id, int(kwargs.get("progress", 0)), str(kwargs.get("message", "")))
        elif event == "completed":
            await service.emit_task_completed(task_id, recording_id, str(kwargs.get("result_path", "")))
        elif event == "failed":
            await service.emit_task_failed(task_id, recording_id, str(kwargs.get("error_message", "")))
    except Exception:
        logger.warning("Failed to emit SSE %s event: %s", event, task_id)


# =============================================================================
# 通用流水线模板
# =============================================================================

def _execute_pipeline(
    task_id: str,
    task_type: str,
    execute: Callable[[Session, Task, Recording], None],
    *,
    initial_progress: int = 10,
) -> None:
    """通用任务执行模板：加载 → 认领 → 执行 → 完成/错误。

    execute 回调在成功认领后被调用。若回调正常返回，任务标记为 completed；
    若抛出异常，任务标记为 error。
    """
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            _run_async(_emit(task_id, "", "failed", error_message="Recording not found"))
            return

        try:
            # 阶段 0: 原子认领
            if not _claim_task(session, task_id, progress=initial_progress):
                return
            _run_async(_emit(task_id, recording.id, "started", message=f"{task_type} task started"))

            # 阶段 1-N: 业务逻辑（由回调注入）
            execute(session, task, recording)

            # 完成
            _update_task(session, task, status="completed", progress=100, completed_at=_now())
        except Exception as exc:
            logger.exception("%s task failed: %s", task_type, task_id)
            _update_task(
                session, task,
                status="error", error_message=str(exc), completed_at=_now(),
            )
            rid = recording.id if recording else ""
            _run_async(_emit(task_id, rid, "failed", error_message=str(exc)))


# =============================================================================
# 转写任务
# =============================================================================

def run_transcription_task(task_id: str) -> None:
    """执行转写任务：归一化 → FunASR 转写 → 保存到数据库。"""

    def _execute(session: Session, task: Task, recording: Recording) -> None:
        # 归一化音频
        recording.status = "normalizing"
        recording.updated_at = _now()
        session.add(recording)
        session.commit()

        audio_service = AudioService()
        source = Path(recording.original_path)
        ensure_hydrated(source, label=f"audio:{recording.filename}")
        normalized = audio_service.normalize(source, recording.id)
        duration = audio_service.duration_seconds(normalized)

        if _is_cancelled(session, task):
            _finish_cancelled(session, task, recording)
            return

        recording.normalized_path = str(normalized)
        recording.duration_seconds = duration
        recording.status = "transcribing"
        recording.updated_at = _now()
        session.add(recording)
        session.commit()
        _update_task(session, task, progress=35)
        _run_async(_emit(task_id, recording.id, "progress", progress=35, message="Processing audio"))

        # FunASR 转写
        segments = ASRService().transcribe(normalized)
        if _is_cancelled(session, task):
            _finish_cancelled(session, task, recording)
            return
        _run_async(_emit(task_id, recording.id, "progress", progress=70, message="Transcribing"))

        # 保存转写片段
        session.exec(delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording.id))
        for idx, seg in enumerate(segments):
            session.add(TranscriptSegment(
                recording_id=recording.id,
                start_time=seg.start_time,
                end_time=seg.end_time,
                speaker=seg.speaker,
                text=seg.text,
                sequence=idx,
            ))

        recording.status = "transcribed"
        recording.updated_at = _now()
        session.add(recording)
        session.commit()

    _execute_pipeline(task_id, "transcription", _execute, initial_progress=10)


# =============================================================================
# 摘要任务
# =============================================================================

def run_summary_task(task_id: str, mode: str = "summary") -> None:
    """执行摘要任务：拼接转写 → LLM 生成 → 保存到数据库。"""

    def _execute(session: Session, task: Task, recording: Recording) -> None:
        # 读取并拼接转写文本
        segments = session.exec(
            select(TranscriptSegment)
            .where(TranscriptSegment.recording_id == recording.id)
            .order_by(TranscriptSegment.sequence)
        ).all()
        transcript = "\n".join(s.text for s in segments if s.text.strip())
        if not transcript:
            raise RuntimeError("Transcript is empty. Run transcription first.")

        # LLM 生成摘要
        _run_async(_emit(task_id, recording.id, "progress", progress=50, message="Generating summary"))
        content = SummaryService().generate(transcript, mode=mode)
        if _is_cancelled(session, task):
            _finish_cancelled(session, task, recording)
            return

        # 保存摘要
        summary = Summary(recording_id=recording.id, mode=mode, content=content)
        session.add(summary)

        # Markdown 文件备份
        template = SUMMARY_TEMPLATES.get(mode)
        template_name = str(template["name"]) if template else mode
        summary_path = get_settings().resolved_summary_dir / summary_filename(
            recording.filename, template_name, summary.created_at, "md", recording.id,
        )
        _atomic_write(summary_path, content)

        recording.status = "completed"
        recording.updated_at = _now()
        session.add(recording)
        session.commit()

    _execute_pipeline(task_id, "summary", _execute, initial_progress=20)


# =============================================================================
# 文件写入
# =============================================================================

def _atomic_write(path: Path, content: str, max_retries: int = 8, retry_delay: float = 0.75) -> None:
    """通过临时文件 + 原子重命名写入文本，避免同步盘文件锁定。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(f".__tmp_{uuid.uuid4().hex[:8]}__")
    for attempt in range(max_retries):
        try:
            tmp_path.write_text(content, encoding="utf-8")
            break
        except OSError:
            if attempt == max_retries - 1:
                raise
            time.sleep(retry_delay)
    for attempt in range(max_retries):
        try:
            os.replace(str(tmp_path), str(path))
            return
        except OSError:
            if attempt == max_retries - 1:
                raise
            time.sleep(retry_delay)
