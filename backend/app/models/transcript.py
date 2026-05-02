from typing import Optional
from uuid import uuid4

from sqlmodel import Field, SQLModel


class TranscriptSegment(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    recording_id: str = Field(index=True)
    start_time: float
    end_time: float
    speaker: str = "speaker_1"
    text: str
    sequence: int = 0


class TranscriptSegmentRead(SQLModel):
    id: str
    recording_id: str
    start_time: float
    end_time: float
    speaker: str
    text: str
    sequence: int

