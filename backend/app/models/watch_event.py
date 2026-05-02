from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class WatchEvent(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    file_path: str = Field(index=True)
    filename: str
    status: str = Field(index=True)
    reason: Optional[str] = None
    recording_id: Optional[str] = Field(default=None, index=True)
    duplicate_of_id: Optional[str] = Field(default=None, index=True)
    content_hash: Optional[str] = Field(default=None, index=True)
    file_size: Optional[int] = None
    file_mtime: Optional[float] = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
