from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlmodel import Session

from app.api.auth import LocalBypassTokenMiddleware
from app.api.events import router as events_router
from app.api.filesystem import router as filesystem_router
from app.api.health import router as health_router
from app.api.recordings import router as recordings_router
from app.api.settings import router as settings_router
from app.api.summary import export_router as summary_export_router
from app.api.summary import router as summary_router
from app.api.tasks import router as tasks_router
from app.api.tokens import router as tokens_router
from app.api.transcribe import router as transcribe_router
from app.api.watch import router as watch_router
from app.config import get_settings
from app.db.database import engine, init_db
from app.middleware.exception_handler import ExceptionHandlerMiddleware
from app.middleware.security_headers import SecurityHeadersMiddleware
from app.services.runtime_log import configure_logging
from app.services.task_service import recover_interrupted_tasks
from app.services.watch_service import watcher


PROJECT_ROOT = Path(__file__).resolve().parents[2]
FRONTEND_DIST = PROJECT_ROOT / "frontend" / "dist"


def register_frontend_routes(app: FastAPI) -> None:
    if not FRONTEND_DIST.is_dir():
        return

    assets_dir = FRONTEND_DIST / "assets"
    if assets_dir.is_dir():
        app.mount("/assets", StaticFiles(directory=assets_dir), name="frontend-assets")

    @app.get("/", include_in_schema=False)
    def frontend_index() -> FileResponse:
        return FileResponse(FRONTEND_DIST / "index.html")

    @app.get("/{path:path}", include_in_schema=False)
    def frontend_asset(path: str) -> FileResponse:
        requested = (FRONTEND_DIST / path).resolve()
        try:
            requested.relative_to(FRONTEND_DIST.resolve())
        except ValueError:
            return FileResponse(FRONTEND_DIST / "index.html")
        if requested.is_file():
            return FileResponse(requested)
        return FileResponse(FRONTEND_DIST / "index.html")


def create_app() -> FastAPI:
    settings = get_settings()
    # 生产环境禁用 OpenAPI 文档
    app_kwargs = {"title": "AI Recorder", "version": "3.1.0"}
    if settings.app_env == "production":
        app_kwargs.update({
            "docs_url": None,
            "redoc_url": None,
            "openapi_url": None,
        })
    app = FastAPI(**app_kwargs)
    app.add_middleware(SecurityHeadersMiddleware)  # 安全头优先
    app.add_middleware(ExceptionHandlerMiddleware)
    app.add_middleware(LocalBypassTokenMiddleware, api_token=settings.api_token)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(events_router)
    app.include_router(health_router)
    app.include_router(filesystem_router)
    app.include_router(recordings_router)
    app.include_router(transcribe_router)
    app.include_router(summary_router)
    app.include_router(summary_export_router)
    app.include_router(tasks_router)
    app.include_router(settings_router)
    app.include_router(watch_router)
    app.include_router(tokens_router)
    register_frontend_routes(app)

    @app.on_event("startup")
    async def on_startup() -> None:
        configure_logging()
        try:
            init_db()
        except Exception as exc:
            from app.services.runtime_log import get_logger
            get_logger().error("Database initialization failed: %s", exc)

        try:
            with Session(engine) as session:
                recover_interrupted_tasks(session)
        except Exception as exc:
            from app.services.runtime_log import get_logger
            get_logger().error("Task recovery failed: %s", exc)

        try:
            watcher.start()
        except Exception as exc:
            from app.services.runtime_log import get_logger
            get_logger().error("Watcher start failed: %s", exc)

    @app.on_event("shutdown")
    async def on_shutdown() -> None:
        await watcher.stop()

    return app


app = create_app()
