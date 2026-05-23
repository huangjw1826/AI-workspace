from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlmodel import Session

from app.api.health import router as health_router
from app.api.recordings import router as recordings_router
from app.api.settings import router as settings_router
from app.api.summary import export_router as summary_export_router
from app.api.summary import router as summary_router
from app.api.tasks import router as tasks_router
from app.api.transcribe import router as transcribe_router
from app.api.watch import router as watch_router
from app.config import get_settings
from app.db.database import engine, init_db
from app.services.runtime_log import configure_logging
from app.services.task_service import recover_interrupted_tasks
from app.services.watch_service import watcher


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="AI Recorder", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(health_router)
    app.include_router(recordings_router)
    app.include_router(transcribe_router)
    app.include_router(summary_router)
    app.include_router(summary_export_router)
    app.include_router(tasks_router)
    app.include_router(settings_router)
    app.include_router(watch_router)

    @app.on_event("startup")
    async def on_startup() -> None:
        configure_logging()
        init_db()
        with Session(engine) as session:
            recover_interrupted_tasks(session)
        watcher.start()

    @app.on_event("shutdown")
    async def on_shutdown() -> None:
        await watcher.stop()

    return app


app = create_app()
