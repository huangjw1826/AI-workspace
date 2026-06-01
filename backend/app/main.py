"""
AI Recorder 后端应用入口

FastAPI 应用工厂，负责：
- 组装中间件（安全头、异常处理、认证、CORS）
- 注册所有 API 路由（10 个功能模块）
- 挂载前端静态文件（生产模式 SPA）
- 启动生命周期管理（数据库初始化、任务恢复、监控/远程访问启停）
"""

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text
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
from app.services.remote_access import remote_access_manager
from app.services.task_service import recover_interrupted_tasks
from app.services.watch_service import watcher


PROJECT_ROOT = Path(__file__).resolve().parents[2]
FRONTEND_DIST = PROJECT_ROOT / "frontend" / "dist"


def register_frontend_routes(app: FastAPI) -> None:
    """注册前端 SPA 静态文件路由。

    如果 frontend/dist 存在，挂载 /assets 静态目录，并配置 catch-all 路由
    将所有非 API 请求回退到 index.html（支持 React Router SPA）。
    包含路径遍历保护：resolve 后检查是否仍在 FRONTEND_DIST 内。
    """
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
    """创建并配置 FastAPI 应用实例。

    中间件注册顺序（重要）：
    1. SecurityHeadersMiddleware - 安全响应头（最外层）
    2. ExceptionHandlerMiddleware - 统一异常处理
    3. LocalBypassTokenMiddleware - API Token 认证（本地请求免检）
    4. CORSMiddleware - CORS 跨域

    启动时依次执行：日志配置 → 数据库初始化 → 中断任务恢复 → 目录监控启动 → 远程访问启动
    关闭时：停止监控 → 停止远程访问
    """
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

    # 安全头优先注册（最外层中间件）
    app.add_middleware(SecurityHeadersMiddleware)
    app.add_middleware(ExceptionHandlerMiddleware)
    app.add_middleware(LocalBypassTokenMiddleware, api_token=settings.api_token)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册 API 路由（按功能模块）
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

    # 挂载前端 SPA（必须在所有 API 路由之后）
    register_frontend_routes(app)

    @app.on_event("startup")
    async def on_startup() -> None:
        """应用启动事件：按依赖顺序初始化各子系统。

        各步骤独立 try/except，单个子系统失败不影响应用启动，
        错误仅记录日志。恢复中断任务将遗留的 queued/running 任务标记为 error。
        """
        configure_logging()
        logger = None

        try:
            init_db()
        except Exception as exc:
            from app.services.runtime_log import get_logger
            logger = get_logger()
            logger.error("Database initialization failed: %s", exc)

        try:
            with Session(engine) as session:
                recover_interrupted_tasks(session)
        except Exception as exc:
            if not logger:
                from app.services.runtime_log import get_logger
                logger = get_logger()
            logger.error("Task recovery failed: %s", exc)

        try:
            watcher.start()
        except Exception as exc:
            if not logger:
                from app.services.runtime_log import get_logger
                logger = get_logger()
            logger.error("Watcher start failed: %s", exc)

        if settings.remote_access_enabled and settings.remote_access_auto_start:
            try:
                if not logger:
                    from app.services.runtime_log import get_logger
                    logger = get_logger()
                logger.info("Attempting to start remote access service...")
                await remote_access_manager.start()
                status = remote_access_manager.status
                if status.running:
                    logger.info("Remote access service started successfully")
                    if status.hostname:
                        logger.info("Remote access hostname: %s", status.hostname)
                else:
                    logger.warning("Remote access service failed to start: %s", status.error)
            except Exception as exc:
                if not logger:
                    from app.services.runtime_log import get_logger
                    logger = get_logger()
                logger.error("Failed to start remote access service: %s", exc)

    @app.on_event("shutdown")
    async def on_shutdown() -> None:
        """应用关闭事件：按相反顺序停止各子系统。

        先停止目录监控（防止新文件入库），再停止远程访问，
        确保所有后台任务优雅退出。
        """
        from app.services.runtime_log import get_logger
        logger = get_logger()

        await watcher.stop()

        try:
            with engine.begin() as connection:
                connection.execute(text("PRAGMA wal_checkpoint(TRUNCATE)"))
        except Exception as exc:
            logger.error("WAL checkpoint failed: %s", exc)

        if settings.remote_access_enabled:
            try:
                logger.info("Stopping remote access service...")
                await remote_access_manager.stop()
                logger.info("Remote access service stopped successfully")
            except Exception as exc:
                logger.error("Failed to stop remote access service: %s", exc)

    return app


app = create_app()
