from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Recording(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    filename: str
    original_path: str
    normalized_path: Optional[str] = None
    duration_seconds: Optional[float] = None
    file_size_bytes: Optional[int] = None
    source_mtime: Optional[float] = None
    format: str
    content_hash: Optional[str] = Field(default=None, index=True)
    source_type: str = "upload"
    source_path: Optional[str] = None
    tags: str = ""
    status: str = "uploaded"
    error_message: Optional[str] = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
