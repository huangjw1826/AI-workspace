from collections.abc import Generator
from pathlib import Path

from sqlalchemy import inspect, text
from sqlmodel import Session, SQLModel, create_engine

from app.config import get_settings
from app.services.file_service import content_hash

settings = get_settings()
sqlite_path = settings.resolved_data_dir / "app.db"
engine = create_engine(
    f"sqlite:///{sqlite_path.as_posix()}",
    connect_args={"check_same_thread": False},
)


def init_db() -> None:
    SQLModel.metadata.create_all(engine)
    _upgrade_sqlite_schema()


def _upgrade_sqlite_schema() -> None:
    inspector = inspect(engine)
    if "recording" not in inspector.get_table_names():
        return

    existing_columns = {column["name"] for column in inspector.get_columns("recording")}
    column_sql = {
        "content_hash": "ALTER TABLE recording ADD COLUMN content_hash VARCHAR",
        "source_type": "ALTER TABLE recording ADD COLUMN source_type VARCHAR DEFAULT 'upload'",
        "source_path": "ALTER TABLE recording ADD COLUMN source_path VARCHAR",
        "file_size_bytes": "ALTER TABLE recording ADD COLUMN file_size_bytes INTEGER",
        "source_mtime": "ALTER TABLE recording ADD COLUMN source_mtime FLOAT",
    }
    with engine.begin() as connection:
        for column, statement in column_sql.items():
            if column not in existing_columns:
                connection.execute(text(statement))
        connection.execute(text("UPDATE recording SET source_type = 'upload' WHERE source_type IS NULL OR source_type = ''"))
        rows = connection.execute(
            text(
                "SELECT id, original_path FROM recording "
                "WHERE content_hash IS NULL OR content_hash = '' "
                "OR file_size_bytes IS NULL OR source_mtime IS NULL"
            )
        ).fetchall()
        for recording_id, original_path in rows:
            if not original_path:
                continue
            path = Path(original_path)
            if not path.exists() or not path.is_file():
                continue
            stat = path.stat()
            values = {"id": recording_id, "file_size_bytes": stat.st_size, "source_mtime": stat.st_mtime}
            if path.is_file():
                values["content_hash"] = content_hash(path)
            connection.execute(
                text(
                    "UPDATE recording SET "
                    "content_hash = COALESCE(NULLIF(content_hash, ''), :content_hash), "
                    "file_size_bytes = COALESCE(file_size_bytes, :file_size_bytes), "
                    "source_mtime = COALESCE(source_mtime, :source_mtime) "
                    "WHERE id = :id"
                ),
                values,
            )
        _deduplicate_watched_originals(connection)


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        return path.resolve().is_relative_to(parent.resolve())
    except Exception:
        return False


def _deduplicate_watched_originals(connection) -> None:
    data_recordings_dir = settings.resolved_data_dir / "recordings"
    rows = connection.execute(
        text(
            "SELECT id, original_path, source_path, content_hash, file_size_bytes FROM recording "
            "WHERE source_type = 'watch' "
            "AND source_path IS NOT NULL AND source_path != '' "
            "AND original_path IS NOT NULL AND original_path != ''"
        )
    ).fetchall()
    for recording_id, original_path, source_path, expected_hash, file_size_bytes in rows:
        original = Path(original_path)
        source = Path(source_path)
        if original == source:
            continue
        if not _is_relative_to(original, data_recordings_dir):
            continue
        if not original.exists() or not source.exists() or not source.is_file():
            continue
        if not expected_hash:
            continue
        source_stat = source.stat()
        if file_size_bytes is not None and int(file_size_bytes) != source_stat.st_size:
            continue
        if content_hash(source) != expected_hash:
            continue
        connection.execute(
            text(
                "UPDATE recording SET original_path = :source_path, "
                "file_size_bytes = :file_size_bytes, source_mtime = :source_mtime "
                "WHERE id = :id"
            ),
            {
                "id": recording_id,
                "source_path": str(source),
                "file_size_bytes": source_stat.st_size,
                "source_mtime": source_stat.st_mtime,
            },
        )
        try:
            original.unlink(missing_ok=True)
        except Exception:
            pass


def get_session() -> Generator[Session, None, None]:
    with Session(engine) as session:
        yield session
