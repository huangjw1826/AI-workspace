"""
修复脚本 - 将暂留在 {recording_id}.json 中的转写数据导入数据库。

第一次执行 migrate_transcripts.py 时，Stage 1 的转写数据被写入 JSON 文件
但因异常回滚没有进入数据库。本脚本将这些数据补入库。
"""

import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

_backend_dir = Path(__file__).resolve().parent.parent / "backend"
sys.path.insert(0, str(_backend_dir))
os.chdir(str(_backend_dir))

from sqlmodel import Session, select, delete
from app.config import get_settings
from app.db.database import engine
from app.models.recording import Recording
from app.models.transcript import TranscriptSegment
from app.models.summary import Summary

UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)


def main():
    settings = get_settings()
    transcript_dir = settings.resolved_transcript_dir

    with Session(engine) as session:
        recordings = session.exec(select(Recording)).all()
        rec_map = {r.id: r for r in recordings}

        imported = 0
        skipped = 0

        for f in sorted(transcript_dir.glob("*.json")):
            rid = f.stem
            if not UUID_RE.match(rid):
                continue
            if rid not in rec_map:
                continue

            recording = rec_map[rid]

            has_segments = session.exec(
                select(TranscriptSegment).where(
                    TranscriptSegment.recording_id == rid
                ).limit(1)
            ).first()

            if has_segments:
                print(f"  [跳过] {recording.filename} - 已有转写数据")
                skipped += 1
                continue

            data = json.loads(f.read_text(encoding="utf-8"))
            if not isinstance(data, list) or not data:
                print(f"  [跳过] {recording.filename} - JSON 格式异常")
                skipped += 1
                continue

            seg_count = 0
            for idx, seg in enumerate(data):
                segment = TranscriptSegment(
                    recording_id=rid,
                    start_time=float(seg.get("start_time", 0)),
                    end_time=float(seg.get("end_time", 0)),
                    speaker=str(seg.get("speaker", "speaker_1")),
                    text=str(seg.get("text", "")),
                    sequence=idx,
                )
                session.add(segment)
                seg_count += 1

            recording.status = "transcribed"
            recording.updated_at = datetime.now(timezone.utc)
            session.add(recording)
            print(f"  [完成] {recording.filename} ({seg_count} 个片段)")
            imported += 1

        for recording in recordings:
            if recording.status != "transcribed":
                continue
            has_summary = session.exec(
                select(Summary).where(Summary.recording_id == recording.id).limit(1)
            ).first()
            if has_summary:
                recording.status = "completed"
                recording.updated_at = datetime.now(timezone.utc)
                session.add(recording)
                print(f"  [状态] {recording.filename} -> completed")

        # 修复只有 1 个片段的异常录音 (2026年01月19日 16点37分)
        bad_recording = None
        for r in recordings:
            if r.filename == "2026年01月19日 16点37分.m4a":
                bad_recording = r
                break

        if bad_recording and bad_recording.status in ("transcribed", "completed"):
            seg_count = session.exec(
                select(TranscriptSegment).where(
                    TranscriptSegment.recording_id == bad_recording.id
                )
            ).all()
            if len(seg_count) <= 1:
                session.exec(
                    delete(TranscriptSegment).where(
                        TranscriptSegment.recording_id == bad_recording.id
                    )
                )
                bad_recording.status = "uploaded"
                bad_recording.updated_at = datetime.now(timezone.utc)
                session.add(bad_recording)
                print(f"\n  [修复] {bad_recording.filename} - 清除了异常的 1 个片段，需要重新转写")

        session.commit()

    print(f"\n导入完成: {imported} 条转写, 跳过 {skipped} 条")


if __name__ == "__main__":
    main()
