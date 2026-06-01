import shutil
import sqlite3
from pathlib import Path
from time import perf_counter

from fastapi import APIRouter, HTTPException, Query
from openai import OpenAI
from pydantic import BaseModel, Field
from sqlalchemy import create_engine as sa_create_engine
from sqlmodel import Session as SaSession
from sqlalchemy import text

from app.config import LLM_PROVIDER_DEFAULTS, get_settings

router = APIRouter(prefix="/api/settings", tags=["settings"])


class LLMSettingsRead(BaseModel):
    provider: str
    base_url: str
    model: str
    configured: bool
    api_key_masked: str
    mimo_thinking: str
    max_completion_tokens: int
    temperature: float | None
    top_p: float | None
    providers: dict[str, dict[str, object]]


class LLMSettingsUpdate(BaseModel):
    provider: str = Field(pattern="^(deepseek|tongyi|qwen|mimo)$")
    api_key: str = ""
    base_url: str = ""
    model: str = ""
    mimo_thinking: str = Field(default="disabled", pattern="^(enabled|disabled)$")
    max_completion_tokens: int = Field(default=2048, ge=1, le=131072)
    temperature: float | None = Field(default=None, ge=0, le=1.5)
    top_p: float | None = Field(default=None, ge=0.01, le=1.0)


class WatchSettingsRead(BaseModel):
    enabled: bool
    watch_dir: str
    recursive: bool
    interval_seconds: int
    stable_count: int
    exists: bool


class WatchSettingsUpdate(BaseModel):
    enabled: bool = False
    watch_dir: str = ""
    recursive: bool = True
    interval_seconds: int = Field(default=10, ge=2, le=3600)
    stable_count: int = Field(default=2, ge=2, le=20)


class StorageSettingsRead(BaseModel):
    data_dir: str
    transcript_dir: str
    summary_dir: str
    transcript_exists: bool
    summary_exists: bool


class StorageSettingsUpdate(BaseModel):
    data_dir: str = ""
    transcript_dir: str = ""
    summary_dir: str = ""


def _mask_secret(value: str) -> str:
    if not value:
        return ""
    if len(value) <= 8:
        return "*" * len(value)
    return f"{value[:4]}...{value[-4:]}"


def _read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _write_env(path: Path, updates: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    existing_lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    seen: set[str] = set()
    output: list[str] = []

    for line in existing_lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            output.append(line)
            continue
        key = stripped.split("=", 1)[0].strip()
        if key in updates:
            output.append(f"{key}={updates[key]}")
            seen.add(key)
        else:
            output.append(line)

    for key, value in updates.items():
        if key not in seen:
            output.append(f"{key}={value}")

    path.write_text("\n".join(output) + "\n", encoding="utf-8")


def _settings_payload() -> LLMSettingsRead:
    settings = get_settings()
    return LLMSettingsRead(
        provider=settings.normalized_llm_provider,
        base_url=settings.resolved_llm_base_url,
        model=settings.resolved_llm_model,
        configured=bool(settings.resolved_llm_api_key),
        api_key_masked=_mask_secret(settings.resolved_llm_api_key),
        mimo_thinking=settings.mimo_thinking,
        max_completion_tokens=settings.llm_max_completion_tokens,
        temperature=settings.resolved_llm_temperature,
        top_p=settings.resolved_llm_top_p,
        providers=LLM_PROVIDER_DEFAULTS,
    )


@router.get("/llm")
def get_llm_settings() -> LLMSettingsRead:
    return _settings_payload()


@router.put("/llm")
def update_llm_settings(payload: LLMSettingsUpdate) -> LLMSettingsRead:
    settings = get_settings()
    env_path = Path(".env")
    current_env = _read_env(env_path)

    updates = {
        "LLM_PROVIDER": payload.provider,
        "LLM_BASE_URL": payload.base_url,
        "LLM_MODEL": payload.model,
        "LLM_MAX_COMPLETION_TOKENS": str(payload.max_completion_tokens),
        "LLM_TEMPERATURE": "" if payload.temperature is None else str(payload.temperature),
        "LLM_TOP_P": "" if payload.top_p is None else str(payload.top_p),
        "MIMO_THINKING": payload.mimo_thinking,
    }

    if payload.api_key:
        if payload.provider == "mimo":
            updates["MIMO_API_KEY"] = payload.api_key
        else:
            updates["LLM_API_KEY"] = payload.api_key
    else:
        if payload.provider == "mimo":
            updates["MIMO_API_KEY"] = current_env.get("MIMO_API_KEY", settings.mimo_api_key)
        else:
            updates["LLM_API_KEY"] = current_env.get("LLM_API_KEY", settings.llm_api_key)

    _write_env(env_path, updates)
    get_settings.cache_clear()
    return _settings_payload()


@router.post("/llm/test")
def test_llm_connectivity() -> dict[str, object]:
    settings = get_settings()
    if not settings.resolved_llm_api_key:
        return {
            "ok": False,
            "provider": settings.normalized_llm_provider,
            "base_url": settings.resolved_llm_base_url,
            "model": settings.resolved_llm_model,
            "message": "API key is not configured.",
        }

    started = perf_counter()
    try:
        request = {
            "model": settings.resolved_llm_model,
            "messages": [
                {"role": "system", "content": "You are a connectivity checker."},
                {"role": "user", "content": "Reply with OK."},
            ],
            "max_completion_tokens": 16,
            "temperature": settings.resolved_llm_temperature,
        }
        if settings.resolved_llm_top_p is not None:
            request["top_p"] = settings.resolved_llm_top_p
        if settings.normalized_llm_provider == "mimo":
            request["extra_body"] = {"thinking": {"type": settings.mimo_thinking}}

        client = OpenAI(
            api_key=settings.resolved_llm_api_key,
            base_url=settings.resolved_llm_base_url,
            timeout=30,
        )
        response = client.chat.completions.create(**request)
        content = response.choices[0].message.content or ""
        return {
            "ok": True,
            "provider": settings.normalized_llm_provider,
            "base_url": settings.resolved_llm_base_url,
            "model": settings.resolved_llm_model,
            "latency_ms": round((perf_counter() - started) * 1000),
            "message": content[:200],
        }
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail={
                "ok": False,
                "provider": settings.normalized_llm_provider,
                "base_url": settings.resolved_llm_base_url,
                "model": settings.resolved_llm_model,
                "latency_ms": round((perf_counter() - started) * 1000),
                "message": str(exc),
            },
        ) from exc


def _watch_settings_payload() -> WatchSettingsRead:
    settings = get_settings()
    watch_dir = str(settings.resolved_watch_dir) if settings.resolved_watch_dir else ""
    return WatchSettingsRead(
        enabled=settings.watch_enabled,
        watch_dir=watch_dir,
        recursive=settings.watch_recursive,
        interval_seconds=settings.watch_interval_seconds,
        stable_count=settings.watch_stable_count,
        exists=bool(settings.resolved_watch_dir and settings.resolved_watch_dir.is_dir()),
    )


@router.get("/watch")
def get_watch_settings() -> WatchSettingsRead:
    return _watch_settings_payload()


@router.put("/watch")
def update_watch_settings(payload: WatchSettingsUpdate) -> WatchSettingsRead:
    watch_dir = payload.watch_dir.strip()
    if payload.enabled and not watch_dir:
        raise HTTPException(status_code=400, detail="启用监控前请填写录音目录")
    if watch_dir:
        path = Path(watch_dir).expanduser().resolve()
        if not path.exists() or not path.is_dir():
            raise HTTPException(status_code=400, detail="录音目录不存在或不是文件夹")
        watch_dir = str(path)

    _write_env(
        Path(".env"),
        {
            "WATCH_ENABLED": "true" if payload.enabled else "false",
            "WATCH_DIR": watch_dir,
            "WATCH_RECURSIVE": "true" if payload.recursive else "false",
            "WATCH_INTERVAL_SECONDS": str(payload.interval_seconds),
            "WATCH_STABLE_COUNT": str(payload.stable_count),
        },
    )
    get_settings.cache_clear()
    return _watch_settings_payload()


def _storage_settings_payload() -> StorageSettingsRead:
    settings = get_settings()
    return StorageSettingsRead(
        data_dir=str(settings.resolved_data_dir),
        transcript_dir=str(settings.resolved_transcript_dir),
        summary_dir=str(settings.resolved_summary_dir),
        transcript_exists=settings.resolved_transcript_dir.is_dir(),
        summary_exists=settings.resolved_summary_dir.is_dir(),
    )


@router.get("/storage")
def get_storage_settings() -> StorageSettingsRead:
    return _storage_settings_payload()


@router.put("/storage")
def update_storage_settings(payload: StorageSettingsUpdate) -> StorageSettingsRead:
    transcript_dir = payload.transcript_dir.strip()
    summary_dir = payload.summary_dir.strip()
    data_dir = payload.data_dir.strip()
    if not transcript_dir or not summary_dir:
        raise HTTPException(status_code=400, detail="转写和摘要保存目录都不能为空")

    transcript_path = Path(transcript_dir).expanduser().resolve()
    summary_path = Path(summary_dir).expanduser().resolve()
    try:
        transcript_path.mkdir(parents=True, exist_ok=True)
        summary_path.mkdir(parents=True, exist_ok=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"无法创建保存目录：{exc}") from exc
    if not transcript_path.is_dir() or not summary_path.is_dir():
        raise HTTPException(status_code=400, detail="保存路径必须是文件夹")

    env_updates = {
        "TRANSCRIPT_DIR": str(transcript_path),
        "SUMMARY_DIR": str(summary_path),
    }
    if data_dir:
        data_path = Path(data_dir).expanduser().resolve()
        try:
            data_path.mkdir(parents=True, exist_ok=True)
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"无法创建数据目录：{exc}") from exc
        if not data_path.is_dir():
            raise HTTPException(status_code=400, detail="数据目录路径必须是文件夹")
        env_updates["DATA_DIR"] = str(data_path)

    _write_env(Path(".env"), env_updates)
    get_settings.cache_clear()
    return _storage_settings_payload()


class StorageMigrateRequest(BaseModel):
    data_dir: str


def _db_record_counts(db_path: Path) -> dict[str, int]:
    if not db_path.exists():
        return {}
    engine = sa_create_engine(f"sqlite:///{db_path.as_posix()}", connect_args={"check_same_thread": False})
    try:
        with SaSession(engine) as session:
            return {
                "recordings": session.execute(text("SELECT COUNT(*) FROM recording")).scalar() or 0,
                "tasks": session.execute(text("SELECT COUNT(*) FROM task")).scalar() or 0,
                "transcript_segments": session.execute(text("SELECT COUNT(*) FROM transcriptsegment")).scalar() or 0,
                "summaries": session.execute(text("SELECT COUNT(*) FROM summary")).scalar() or 0,
            }
    except Exception:
        return {}
    finally:
        engine.dispose()


def _dir_file_counts(dir_path: Path) -> dict[str, object]:
    """统计目录下的文件和数据库概况。"""
    stats: dict[str, object] = {
        "exists": dir_path.exists(),
        "app_db_size_mb": 0,
        "recording_count": 0,
        "recording_size_mb": 0,
        "normalized_count": 0,
        "normalized_size_mb": 0,
    }
    db_file = dir_path / "app.db"
    if db_file.is_file():
        stats["app_db_size_mb"] = round(db_file.stat().st_size / (1024 * 1024), 1)
    recordings = dir_path / "recordings"
    if recordings.is_dir():
        rec_files = [f for f in recordings.iterdir() if f.is_file()]
        stats["recording_count"] = len(rec_files)
        stats["recording_size_mb"] = round(sum(f.stat().st_size for f in rec_files) / (1024 * 1024), 1)
    normalized = dir_path / "normalized"
    if normalized.is_dir():
        norm_files = [f for f in normalized.iterdir() if f.is_file()]
        stats["normalized_count"] = len(norm_files)
        stats["normalized_size_mb"] = round(sum(f.stat().st_size for f in norm_files) / (1024 * 1024), 1)

    db_counts = _db_record_counts(db_file)
    stats.update(db_counts)
    return stats


@router.get("/storage/migration-preview")
def preview_storage_migration(new_data_dir: str = Query(..., description="新的数据总目录路径")) -> dict[str, object]:
    settings = get_settings()
    old_dir = settings.resolved_data_dir
    new_dir = Path(new_data_dir).expanduser().resolve()

    if old_dir.resolve() == new_dir.resolve():
        raise HTTPException(status_code=400, detail="新旧数据目录相同，无需迁移")

    return {
        "old_dir": str(old_dir),
        "new_dir": str(new_dir),
        "old": _dir_file_counts(old_dir),
        "new": _dir_file_counts(new_dir),
    }


def _merge_databases(old_db: Path, new_db: Path) -> str:
    src = sqlite3.connect(str(old_db))
    dst = sqlite3.connect(str(new_db))
    dst.execute("PRAGMA foreign_keys=OFF")

    table_order = ["recording", "apitoken", "task", "transcriptsegment", "summary", "watchevent", "accesslog"]
    done: list[str] = []

    for table in table_order:
        src_columns = _table_columns(src, table)
        if not src_columns:
            continue
        dst_columns = _table_columns(dst, table)
        common = [c for c in src_columns if c in dst_columns]
        if not common:
            continue

        col_list = ", ".join(f'"{c}"' for c in common)
        placeholders = ", ".join("?" for _ in common)
        src_rows = src.execute(f"SELECT {col_list} FROM {table}").fetchall()
        if not src_rows:
            continue
        dst.executemany(f"INSERT OR IGNORE INTO {table} ({col_list}) VALUES ({placeholders})", src_rows)
        done.append(f"{table}:{len(src_rows)}")

    dst.execute("PRAGMA foreign_keys=ON")
    dst.commit()
    src.close()
    dst.close()
    return f"数据库已合并 ({', '.join(done)} 条)"


def _table_columns(conn: sqlite3.Connection, table: str) -> list[str]:
    rows = conn.execute(f'PRAGMA table_info("{table}")').fetchall()
    return [r[1] for r in rows]


@router.post("/storage/migrate")
def migrate_storage(payload: StorageMigrateRequest) -> dict[str, object]:
    settings = get_settings()
    old_dir = settings.resolved_data_dir
    new_dir = Path(payload.data_dir).expanduser().resolve()

    if old_dir.resolve() == new_dir.resolve():
        raise HTTPException(status_code=400, detail="新旧目录相同，无需迁移")

    new_dir.mkdir(parents=True, exist_ok=True)
    results: list[str] = []

    old_db = old_dir / "app.db"
    new_db = new_dir / "app.db"
    if old_db.exists():
        if not new_db.exists():
            try:
                src_conn = sqlite3.connect(str(old_db))
                dst_conn = sqlite3.connect(str(new_db))
                src_conn.backup(dst_conn)
                src_conn.close()
                dst_conn.close()
                results.append(f"数据库已迁移（{round(old_db.stat().st_size / (1024 * 1024), 1)} MB）")
            except Exception:
                shutil.copy2(old_db, new_db)
                results.append(f"数据库已复制（{round(old_db.stat().st_size / (1024 * 1024), 1)} MB）")
        else:
            merge_result = _merge_databases(old_db, new_db)
            results.append(merge_result)

    for subdir in ["recordings", "normalized"]:
        old_sub = old_dir / subdir
        new_sub = new_dir / subdir
        if not old_sub.is_dir():
            continue
        copied = 0
        skipped = 0
        for f in old_sub.iterdir():
            if not f.is_file():
                continue
            dest = new_sub / f.name
            if not dest.exists():
                new_sub.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dest)
                copied += 1
            else:
                skipped += 1
        label_map = {"recordings": "录音文件", "normalized": "归一化音频"}
        label = label_map.get(subdir, subdir)
        results.append(f"{label}：复制 {copied} 个，跳过 {skipped} 个（已存在）")

    return {"ok": True, "results": results}
