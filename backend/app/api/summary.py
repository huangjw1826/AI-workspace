import re
from urllib.parse import quote

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query, Response
from pydantic import BaseModel, Field
from sqlmodel import Session, select

from app.config import get_settings
from app.db.database import get_session
from app.models import Recording, Summary, Task, TranscriptSegment
from app.pipeline.workflow import run_summary_task
from app.services.docx_export import build_docx
from app.services.export_names import summary_filename
from app.services.summary_service import SUMMARY_TEMPLATES
from app.services.task_service import create_or_get_task

router = APIRouter(prefix="/api/summary", tags=["summary"])
export_router = APIRouter(prefix="/api/summaries", tags=["summary"])


class BatchSummaryRequest(BaseModel):
    recording_ids: list[str] = Field(min_length=1, max_length=200)


@router.get("/templates")
def list_summary_templates() -> list[dict[str, str]]:
    return [
        {
            "id": str(template["id"]),
            "name": str(template["name"]),
            "description": str(template["description"]),
        }
        for template in SUMMARY_TEMPLATES.values()
    ]


@router.post("/batch")
def summarize_batch(
    payload: BatchSummaryRequest,
    background_tasks: BackgroundTasks,
    mode: str = "summary",
    session: Session = Depends(get_session),
) -> list[Task]:
    if mode not in SUMMARY_TEMPLATES:
        raise HTTPException(status_code=400, detail="Unknown summary template")
    tasks: list[Task] = []
    for recording_id in payload.recording_ids:
        recording = session.get(Recording, recording_id)
        if recording is None:
            continue
        task, created = create_or_get_task(session, recording, f"summary:{mode}")
        if created:
            background_tasks.add_task(run_summary_task, task.id, mode)
        tasks.append(task)
    return tasks


@router.post("/{recording_id}")
def summarize(
    recording_id: str,
    background_tasks: BackgroundTasks,
    mode: str = "summary",
    session: Session = Depends(get_session),
) -> Task:
    recording = session.get(Recording, recording_id)
    if recording is None:
        raise HTTPException(status_code=404, detail="Recording not found")
    if mode not in SUMMARY_TEMPLATES:
        raise HTTPException(status_code=400, detail="未知的摘要模板")
    task, created = create_or_get_task(session, recording, f"summary:{mode}")
    if created:
        background_tasks.add_task(run_summary_task, task.id, mode)
    return task


def _download_response(content: str, filename: str, media_type: str) -> Response:
    encoded = quote(filename)
    return Response(
        content=content,
        media_type=f"{media_type}; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{encoded}"},
    )


def _download_bytes_response(content: bytes, filename: str, media_type: str) -> Response:
    encoded = quote(filename)
    return Response(
        content=content,
        media_type=media_type,
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{encoded}"},
    )


def _markdown_to_text(content: str) -> str:
    text = re.sub(r"^#{1,6}\s*", "", content, flags=re.MULTILINE)
    text = re.sub(r"\*\*(.*?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    return text.strip() + "\n"


def _delete_summary_files(summary: Summary, recording: Recording | None) -> None:
    settings = get_settings()
    title = recording.filename if recording else summary.recording_id
    template = SUMMARY_TEMPLATES.get(summary.mode)
    template_name = str(template["name"]) if template else summary.mode
    candidates = {
        settings.resolved_summary_dir / summary_filename(title, template_name, summary.created_at, "md", summary.recording_id),
        settings.resolved_summary_dir / summary_filename(title, template_name, summary.created_at, "md"),
        settings.resolved_summary_dir / f"{summary.recording_id}-{summary.mode}.md",
        settings.resolved_data_dir / "summaries" / f"{summary.recording_id}-{summary.mode}.md",
    }
    for path in candidates:
        try:
            path.unlink(missing_ok=True)
        except Exception:
            pass


@export_router.get("/{summary_id}/export")
def export_summary(
    summary_id: str,
    format: str = Query("md", pattern="^(md|txt|docx)$"),
    session: Session = Depends(get_session),
) -> Response:
    summary = session.get(Summary, summary_id)
    if summary is None:
        raise HTTPException(status_code=404, detail="摘要不存在")
    recording = session.get(Recording, summary.recording_id)
    title = recording.filename if recording else summary.recording_id
    template = SUMMARY_TEMPLATES.get(summary.mode)
    template_name = template["name"] if template else summary.mode

    if format == "md":
        content = f"# {title} 摘要\n\n- 模板：{template_name}\n\n{summary.content.strip()}\n"
        filename = summary_filename(title, str(template_name), summary.created_at, "md")
        return _download_response(content, filename, "text/markdown")

    if format == "docx":
        filename = summary_filename(title, str(template_name), summary.created_at, "docx")
        return _download_bytes_response(
            build_docx(
                f"{title} Summary",
                [f"Template: {template_name}", "", *_markdown_to_text(summary.content).splitlines()],
            ),
            filename,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )

    content = f"{title} 摘要\n模板：{template_name}\n\n{_markdown_to_text(summary.content)}"
    filename = summary_filename(title, str(template_name), summary.created_at, "txt")
    return _download_response(content, filename, "text/plain")


@export_router.delete("/{summary_id}")
def delete_summary(summary_id: str, session: Session = Depends(get_session)) -> dict[str, str]:
    summary = session.get(Summary, summary_id)
    if summary is None:
        raise HTTPException(status_code=404, detail="摘要不存在")

    recording = session.get(Recording, summary.recording_id)
    _delete_summary_files(summary, recording)
    recording_id = summary.recording_id
    session.delete(summary)
    session.commit()

    remaining_summary = session.exec(select(Summary).where(Summary.recording_id == recording_id)).first()
    if recording is not None and remaining_summary is None and recording.status == "completed":
        has_segments = session.exec(select(TranscriptSegment).where(TranscriptSegment.recording_id == recording_id)).first()
        recording.status = "transcribed" if has_segments is not None else "uploaded"
        session.add(recording)
        session.commit()

    return {"message": "摘要已删除"}
