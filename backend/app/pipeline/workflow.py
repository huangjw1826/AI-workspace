"""
Workflow orchestration - 任务执行流水线

定义转写和摘要两种核心任务的完整执行流程，包括：
- 音频归一化 → ASR 转写 → 结果保存
- 转写拼接 → LLM 摘要 → 结果保存

任务执行在线程中运行，通过 SSE 事件实时推送进度。
每个阶段检查取消标志，支持用户主动中止任务。
"""

import json
from datetime import datetime, timezone
from pathlib import Path

from sqlmodel import Session, delete, select

from app.db.database import engine
from app.models import Recording, Summary, Task, TranscriptSegment
from app.config import get_settings
from app.services.asr_service import ASRService
from app.services.audio_service import AudioService
from app.services.export_names import summary_filename
from app.services.runtime_log import get_logger
from app.services.summary_service import SUMMARY_TEMPLATES, SummaryService

logger = get_logger()

_sse_service = None


async def _get_sse_service():
    """获取 SSE 服务单例（延迟导入避免循环依赖）。"""
    global _sse_service
    if _sse_service is None:
        from app.services.sse_service import get_sse_service as _get_sse
        _sse_service = await _get_sse()
    return _sse_service


def _now() -> datetime:
    """当前 UTC 时间，统一时间源。"""
    return datetime.now(timezone.utc)


def _update_task(session: Session, task: Task, **values: object) -> None:
    """批量更新任务字段并提交数据库。

    Args:
        session: SQLModel 数据库会话
        task: 要更新的任务实体对象
        **values: 要更新的字段名和值（如 status="running", progress=50）
    """
    for key, value in values.items():
        setattr(task, key, value)
    task.updated_at = _now()
    session.add(task)
    session.commit()
    session.refresh(task)


def _is_cancelled(session: Session, task: Task) -> bool:
    """检查任务是否已被用户取消（刷新后从数据库读取最新状态）。"""
    session.refresh(task)
    return task.status == "cancelled"


def _finish_cancelled(session: Session, task: Task, recording: Recording | None = None) -> None:
    """处理任务取消：将任务标记为 cancelled，录音状态恢复到 uploaded。

    仅当录音仍在处理过程中（queued/normalizing/transcribing）时才恢复，
    避免覆盖已完成或出错的状态。
    """
    task.completed_at = task.completed_at or _now()
    task.updated_at = _now()
    session.add(task)
    if recording is not None and recording.status in {"queued", "normalizing", "transcribing"}:
        recording.status = "uploaded"
        recording.updated_at = _now()
        session.add(recording)
    session.commit()


def _run_async_task(coro):
    """
    在同步上下文中安全执行异步协程。

    策略（按场景）：
    - 无事件循环 → 创建新循环执行
    - 事件循环存在且空闲 → 直接 run_until_complete
    - 事件循环正在运行（FastAPI 线程池场景）→ 在新线程中创建独立循环执行

    主要用于 SSE 事件发送，SSE 发送失败不会影响主任务流程。
    """
    import asyncio
    import threading

    def get_or_create_loop():
        try:
            loop = asyncio.get_event_loop()
            if not loop.is_closed():
                return loop
        except RuntimeError:
            pass
        new_loop = asyncio.new_event_loop()
        asyncio.set_event_loop(new_loop)
        return new_loop

    try:
        loop = get_or_create_loop()
        if loop.is_running():
            # 事件循环正在运行，在独立线程中创建新循环执行
            def run_in_thread():
                new_loop = None
                try:
                    new_loop = asyncio.new_event_loop()
                    asyncio.set_event_loop(new_loop)
                    new_loop.run_until_complete(coro)
                except Exception as e:
                    logger.exception("Async task failed in background thread", exc_info=e)
                finally:
                    if new_loop is not None and not new_loop.is_closed():
                        new_loop.close()
            thread = threading.Thread(target=run_in_thread, daemon=True)
            thread.start()
        else:
            try:
                loop.run_until_complete(coro)
            except Exception as e:
                logger.exception("Async task failed", exc_info=e)
    except Exception as e:
        logger.exception("Async task failed", exc_info=e)


# --- SSE 事件发送辅助函数 ---
# 每个函数独立捕获异常，确保 SSE 推送失败不影响主任务流程。


async def _emit_task_started(task_id: str, recording_id: str, message: str) -> None:
    """发送任务开始 SSE 事件。"""
    try:
        service = await _get_sse_service()
        await service.emit_task_started(task_id, recording_id, message)
    except Exception:
        logger.warning("Failed to emit task started event: %s", task_id)


async def _emit_task_progress(task_id: str, recording_id: str, progress: int, message: str) -> None:
    """发送任务进度 SSE 事件。"""
    try:
        service = await _get_sse_service()
        await service.emit_task_progress(task_id, recording_id, progress, message)
    except Exception:
        logger.warning("Failed to emit task progress event: %s", task_id)


async def _emit_task_completed(task_id: str, recording_id: str, result_path: str) -> None:
    """发送任务完成 SSE 事件。"""
    try:
        service = await _get_sse_service()
        await service.emit_task_completed(task_id, recording_id, result_path)
    except Exception:
        logger.warning("Failed to emit task completed event: %s", task_id)


async def _emit_task_failed(task_id: str, recording_id: str, error_message: str) -> None:
    """发送任务失败 SSE 事件。"""
    try:
        service = await _get_sse_service()
        await service.emit_task_failed(task_id, recording_id, error_message)
    except Exception:
        logger.warning("Failed to emit task failed event: %s", task_id)


# =====================================================================
# 核心工作任务
# =====================================================================


def run_transcription_task(task_id: str) -> None:
    """执行转写任务的完整流程。

    阶段流程：
    1. 检查取消 → 更新状态为 running → 发送开始事件 (progress: 10)
    2. 音频归一化：转换为单声道 16kHz WAV (progress: 35)
    3. 检查取消 → FunASR 语音转写 (progress: 70)
    4. 检查取消 → 删除旧片段 → 保存新转写片段到数据库和 JSON 备份
    5. 录音状态 → transcribed → 发送完成事件 (progress: 100)

    每个阶段完成后检查取消标志，用户取消时恢复录音状态。

    Args:
        task_id: 任务唯一标识（UUID）
    """
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            _run_async_task(_emit_task_failed(task_id, "", "Recording not found"))
            return

        try:
            # 阶段 0: 取消预检
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            # 阶段 1: 开始
            _update_task(session, task, status="running", progress=10, started_at=_now())
            _run_async_task(_emit_task_started(task_id, recording.id, "Task started"))
            recording.status = "normalizing"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()

            # 阶段 2: 音频归一化
            audio_service = AudioService()
            source = Path(recording.original_path)
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
            _run_async_task(_emit_task_progress(task_id, recording.id, 35, "Processing audio"))

            # 阶段 3: FunASR 转写（最耗时步骤）
            segments = ASRService().transcribe(normalized)
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            _run_async_task(_emit_task_progress(task_id, recording.id, 70, "Transcribing"))

            # 阶段 4: 保存结果（先清旧数据，再批量写入）
            session.exec(delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording.id))
            for index, segment in enumerate(segments):
                session.add(
                    TranscriptSegment(
                        recording_id=recording.id,
                        start_time=segment.start_time,
                        end_time=segment.end_time,
                        speaker=segment.speaker,
                        text=segment.text,
                        sequence=index,
                    )
                )
            # 备份到 JSON 文件
            transcript_path = get_settings().resolved_transcript_dir / f"{recording.id}.json"
            transcript_path.write_text(
                json.dumps([segment.__dict__ for segment in segments], ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

            # 阶段 5: 完成
            recording.status = "transcribed"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _run_async_task(_emit_task_completed(task_id, recording.id, str(transcript_path)))
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(transcript_path),
                completed_at=_now(),
            )
        except Exception as exc:
            logger.exception("Transcription task failed: %s", task_id)
            recording.status = "error"
            recording.error_message = str(exc)
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _update_task(
                session,
                task,
                status="error",
                error_message=str(exc),
                completed_at=_now(),
            )
            _run_async_task(_emit_task_failed(task_id, recording.id, str(exc)))


def run_summary_task(task_id: str, mode: str = "summary") -> None:
    """执行摘要任务的完整流程。

    阶段流程：
    1. 检查取消 → 更新状态为 running (progress: 20)
    2. 从数据库读取转写片段并按 sequence 排序拼接
    3. 检查转写是否为空（未转写则报错）
    4. 调用 LLM 生成摘要 (progress: 50)
    5. 检查取消 → 保存摘要到数据库和 Markdown 文件
    6. 录音状态 → completed (progress: 100)

    Args:
        task_id: 任务唯一标识（UUID）
        mode: 摘要模板类型（如 meeting_minutes、action_items 等），默认 "summary"
    """
    with Session(engine) as session:
        task = session.get(Task, task_id)
        if task is None:
            return
        recording = session.get(Recording, task.recording_id)
        if recording is None:
            _update_task(session, task, status="error", error_message="Recording not found")
            _run_async_task(_emit_task_failed(task_id, "", "Recording not found"))
            return

        try:
            # 阶段 0: 取消预检
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return
            # 阶段 1: 开始
            _update_task(session, task, status="running", progress=20, started_at=_now())
            _run_async_task(_emit_task_started(task_id, recording.id, f"Summary task started ({mode})"))

            # 阶段 2: 读取并拼接转写文本
            segments = session.exec(
                select(TranscriptSegment)
                .where(TranscriptSegment.recording_id == recording.id)
                .order_by(TranscriptSegment.sequence)
            ).all()
            transcript = "\n".join(segment.text for segment in segments if segment.text.strip())
            if not transcript:
                raise RuntimeError("Transcript is empty. Run transcription first.")

            # 阶段 3: LLM 生成摘要
            _run_async_task(_emit_task_progress(task_id, recording.id, 50, "Generating summary"))
            content = SummaryService().generate(transcript, mode=mode)
            if _is_cancelled(session, task):
                _finish_cancelled(session, task, recording)
                return

            # 阶段 4: 保存结果
            summary = Summary(recording_id=recording.id, mode=mode, content=content)
            session.add(summary)

            template = SUMMARY_TEMPLATES.get(mode)
            template_name = str(template["name"]) if template else mode
            summary_path = get_settings().resolved_summary_dir / summary_filename(
                recording.filename,
                template_name,
                summary.created_at,
                "md",
                recording.id,
            )
            summary_path.write_text(content, encoding="utf-8")

            # 阶段 5: 完成
            recording.status = "completed"
            recording.updated_at = _now()
            session.add(recording)
            session.commit()
            _run_async_task(_emit_task_completed(task_id, recording.id, str(summary_path)))
            _update_task(
                session,
                task,
                status="completed",
                progress=100,
                result_path=str(summary_path),
                completed_at=_now(),
            )
        except Exception as exc:
            logger.exception("Summary task failed: %s", task_id)
            _update_task(
                session,
                task,
                status="error",
                error_message=str(exc),
                completed_at=_now(),
            )
            _run_async_task(_emit_task_failed(task_id, recording.id if recording else "", str(exc)))
