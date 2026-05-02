from datetime import datetime, timezone
from uuid import uuid4

from sqlmodel import Field, SQLModel


class Summary(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    recording_id: str = Field(index=True)
    mode: str = "summary"
    content: str
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

