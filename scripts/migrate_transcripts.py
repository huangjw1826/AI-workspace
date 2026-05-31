"""
迁移同步脚本 - 将已有转写 JSON 和摘要 MD 文件与数据库中的录音重新关联。

适用场景：
- 从另一台机器迁移了录音文件、转写结果和摘要文件
- 录音已通过目录监控入库（获得了新的 UUID），但旧的转写/摘要数据
  使用的是旧 UUID，导致无法关联同步

使用方法：
    cd d:\\AI-workspace
    .\\.venv\\Scripts\\python.exe scripts\\migrate_transcripts.py [--dry-run]

工作原理：
    1. 扫描 SUMMARY_DIR 下的 MD 文件，解析 旧recording_id → 文件名 的映射
    2. 扫描 TRANSCRIPT_DIR 下的 JSON 文件：
       - UUID 命名（如 55cfa25f-...json）：通过步骤1的映射找到对应录音
       - 文件名命名（如 xxx.transcribe.json）：通过文件名匹配录音
    3. 将转写片段写入数据库，更新录音状态
    4. 扫描 SUMMARY_DIR 下的 MD 文件，解析并写入 Summary 记录
    5. 将 UUID 命名的 JSON 文件重命名为 {新recording_id}.json
"""

import ast
import json
import os
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

_backend_dir = Path(__file__).resolve().parent.parent / "backend"
sys.path.insert(0, str(_backend_dir))
os.chdir(str(_backend_dir))

from sqlmodel import Session, select
from app.config import get_settings
from app.db.database import engine
from app.models.recording import Recording
from app.models.transcript import TranscriptSegment
from app.models.summary import Summary

TEMPLATE_NAME_TO_MODE = {
    "结构化摘要": "structured_summary",
    "会议纪要": "meeting_minutes",
    "待办事项": "action_items",
    "决策与风险": "decisions_risks",
    "管理层简报": "executive_brief",
    "转写内容规整": "polished_transcript",
}

SUMMARY_FILENAME_RE = re.compile(
    r"^(.+?)_摘要_(.+?)_(\d{8}-\d{6})_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.md$"
)
NONSTD_SUMMARY_RE = re.compile(
    r"^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-([a-z_]+)\.md$"
)
UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
LOG_LINE_RE = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} - ") 


def build_lookup(session: Session) -> dict[str, Recording]:
    recordings = session.exec(select(Recording)).all()
    lookup: dict[str, Recording] = {}
    for rec in recordings:
        stem = Path(rec.filename).stem
        lookup[stem] = rec
    return lookup


def build_uuid_mapping(summary_dir: Path) -> dict[str, str]:
    mapping: dict[str, str] = {}
    if not summary_dir.exists():
        return mapping
    for f in summary_dir.glob("*.md"):
        m = SUMMARY_FILENAME_RE.match(f.name)
        if m:
            stem = m.group(1)
            old_uuid = m.group(4)
            if old_uuid not in mapping:
                mapping[old_uuid] = stem
            continue
        m = NONSTD_SUMMARY_RE.match(f.name)
        if m:
            old_uuid = m.group(1)
            if old_uuid not in mapping:
                mapping[old_uuid] = ""
    return mapping


def parse_transcribe_json(file_path: Path):
    raw = file_path.read_text(encoding="utf-8")
    lines = raw.splitlines(True)
    content_start = 0
    for i, line in enumerate(lines):
        if LOG_LINE_RE.match(line):
            content_start = i + 1
            continue
        stripped = line.lstrip()
        if stripped and stripped[0] in "{[进行":
            content_start = i
            break

    trimmed = "".join(lines[content_start:]).strip()
    if not trimmed:
        return None

    try:
        return json.loads(trimmed)
    except json.JSONDecodeError:
        pass

    try:
        return ast.literal_eval(trimmed)
    except (ValueError, SyntaxError):
        pass

    if trimmed[0] == "{":
        obj_start = trimmed.find("{")
        if obj_start > 0:
            try:
                return json.loads(trimmed[obj_start:])
            except json.JSONDecodeError:
                pass

    return None


def parse_summary_filename(filename: str):
    m = SUMMARY_FILENAME_RE.match(filename)
    if not m:
        return None
    return {
        "stem": m.group(1),
        "template_name": m.group(2),
        "timestamp": m.group(3),
        "old_recording_id": m.group(4),
    }


def migrate_uuid_transcripts(
    session: Session,
    transcript_dir: Path,
    uuid_mapping: dict[str, str],
    recording_lookup: dict[str, Recording],
    dry_run: bool,
    stats: dict,
):
    for f in sorted(transcript_dir.glob("*.json")):
        stem = f.stem
        if not UUID_RE.match(stem):
            continue

        old_uuid = stem
        filename_stem = uuid_mapping.get(old_uuid)
        if filename_stem is None:
            print(f"  [跳过] {f.name} - 无法从摘要文件中找到映射关系")
            stats["skipped_transcripts"].append(f.name)
            continue

        if not filename_stem:
            print(f"  [跳过] {f.name} - 有非标准摘要文件但无法确定对应录音，请手动确认")
            stats["skipped_transcripts"].append(f.name)
            continue

        recording = recording_lookup.get(filename_stem)
        if recording is None:
            print(f"  [跳过] {f.name} - 数据库中没有匹配的录音: {filename_stem}")
            stats["skipped_transcripts"].append(f.name)
            continue

        if recording.status in ("transcribed", "completed"):
            print(f"  [跳过] {f.name} - 录音已转写: {recording.filename}")
            stats["skipped_transcripts"].append(f.name)
            continue

        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"  [错误] {f.name} - JSON 解析失败: {e}")
            stats["errors"].append(f.name)
            continue

        if not isinstance(data, list):
            print(f"  [跳过] {f.name} - 格式不是列表")
            stats["skipped_transcripts"].append(f.name)
            continue

        if dry_run:
            print(f"  [预览] {f.name} -> {recording.filename} ({len(data)} 个片段)")
            stats["transcripts_migrated"] += 1
            continue

        segments = []
        for idx, seg in enumerate(data):
            segment = TranscriptSegment(
                recording_id=recording.id,
                start_time=float(seg.get("start_time", 0)),
                end_time=float(seg.get("end_time", 0)),
                speaker=str(seg.get("speaker", "speaker_1")),
                text=str(seg.get("text", "")),
                sequence=idx,
            )
            session.add(segment)
            segments.append(segment)

        recording.status = "transcribed"
        recording.updated_at = datetime.now(timezone.utc)
        session.add(recording)
        session.flush()

        new_path = transcript_dir / f"{recording.id}.json"
        json_str = json.dumps(
            [{"start_time": s.start_time, "end_time": s.end_time,
              "speaker": s.speaker, "text": s.text}
             for s in segments],
            ensure_ascii=False,
            indent=2,
        )
        new_path.write_text(json_str, encoding="utf-8")

        print(f"  [完成] {f.name} -> {recording.filename} ({len(segments)} 个片段)")
        stats["transcripts_migrated"] += 1


def migrate_transcribe_json(
    session: Session,
    transcript_dir: Path,
    recording_lookup: dict[str, Recording],
    dry_run: bool,
    stats: dict,
):
    for f in sorted(transcript_dir.glob("*.transcribe.json")):
        stem = f.stem.replace(".transcribe", "")

        recording = recording_lookup.get(stem)
        if recording is None:
            print(f"  [跳过] {f.name} - 数据库中没有匹配的录音: {stem}")
            stats["skipped_transcripts"].append(f.name)
            continue

        if recording.status in ("transcribed", "completed"):
            print(f"  [跳过] {f.name} - 录音已转写")
            stats["skipped_transcripts"].append(f.name)
            continue

        data = parse_transcribe_json(f)
        if data is None:
            print(f"  [错误] {f.name} - JSON 解析失败（已尝试所有方式）")
            stats["errors"].append(f.name)
            continue

        if not isinstance(data, dict):
            print(f"  [跳过] {f.name} - 格式不是字典")
            stats["skipped_transcripts"].append(f.name)
            continue

        raw_segments = data.get("segments", [])
        if not raw_segments:
            print(f"  [跳过] {f.name} - 没有 segments 数据")
            stats["skipped_transcripts"].append(f.name)
            continue

        if dry_run:
            print(f"  [预览] {f.name} -> {recording.filename} ({len(raw_segments)} 个片段)")
            stats["transcripts_migrated"] += 1
            continue

        segments = []
        for idx, seg in enumerate(raw_segments):
            start_time = 0.0
            end_time = 0.0

            if "start" in seg and "end" in seg:
                start_time = float(seg["start"])
                end_time = float(seg["end"])
            else:
                ts = seg.get("timestamp", [0, 0])
                if isinstance(ts, list) and len(ts) >= 2:
                    try:
                        start_time = float(ts[0])
                        end_time = float(ts[1])
                    except (ValueError, TypeError):
                        start_time = 0.0
                        end_time = 0.0

            segment = TranscriptSegment(
                recording_id=recording.id,
                start_time=start_time,
                end_time=end_time,
                speaker="speaker_1",
                text=str(seg.get("text", "")),
                sequence=idx,
            )
            session.add(segment)
            segments.append(segment)

        recording.status = "transcribed"
        recording.updated_at = datetime.now(timezone.utc)
        session.add(recording)
        session.flush()

        new_path = transcript_dir / f"{recording.id}.json"
        json_str = json.dumps(
            [{"start_time": s.start_time, "end_time": s.end_time,
              "speaker": s.speaker, "text": s.text}
             for s in segments],
            ensure_ascii=False,
            indent=2,
        )
        new_path.write_text(json_str, encoding="utf-8")

        print(f"  [完成] {f.name} -> {recording.filename} ({len(segments)} 个片段)")
        stats["transcripts_migrated"] += 1


def migrate_summaries(
    session: Session,
    summary_dir: Path,
    recording_lookup: dict[str, Recording],
    uuid_mapping: dict[str, str],
    dry_run: bool,
    stats: dict,
):
    if not summary_dir.exists():
        return

    for f in sorted(summary_dir.glob("*.md")):
        if f.name.startswith("."):
            continue

        parsed = parse_summary_filename(f.name)
        if parsed is not None:
            stem = parsed["stem"]
            recording = recording_lookup.get(stem)
            if recording is None:
                print(f"  [跳过] {f.name} - 数据库中没有匹配的录音")
                stats["skipped_summaries"].append(f.name)
                continue

            template_name = parsed["template_name"]
            mode = TEMPLATE_NAME_TO_MODE.get(template_name)
            if mode is None:
                print(f"  [跳过] {f.name} - 未知模板类型: {template_name}")
                stats["skipped_summaries"].append(f.name)
                continue

            ts_match = re.match(r"(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})", parsed["timestamp"])
            if ts_match:
                created_at = datetime(
                    int(ts_match.group(1)), int(ts_match.group(2)), int(ts_match.group(3)),
                    int(ts_match.group(4)), int(ts_match.group(5)), int(ts_match.group(6)),
                    tzinfo=timezone.utc,
                )
            else:
                created_at = datetime.now(timezone.utc)
        else:
            m = NONSTD_SUMMARY_RE.match(f.name)
            if m is None:
                continue
            old_uuid = m.group(1)
            mode = m.group(2)
            if mode not in TEMPLATE_NAME_TO_MODE.values():
                continue

            filename_stem = uuid_mapping.get(old_uuid)
            if not filename_stem:
                print(f"  [跳过] {f.name} - 非标准命名且无法确定对应录音")
                stats["skipped_summaries"].append(f.name)
                continue

            recording = recording_lookup.get(filename_stem)
            if recording is None:
                print(f"  [跳过] {f.name} - 数据库中没有匹配的录音")
                stats["skipped_summaries"].append(f.name)
                continue

            template_name = None
            for k, v in TEMPLATE_NAME_TO_MODE.items():
                if v == mode:
                    template_name = k
                    break
            if template_name is None:
                template_name = mode
            created_at = datetime.now(timezone.utc)

        existing = session.exec(
            select(Summary).where(
                Summary.recording_id == recording.id,
                Summary.mode == mode,
            )
        ).first()
        if existing:
            print(f"  [跳过] {f.name} - 已存在相同模式的摘要")
            stats["skipped_summaries"].append(f.name)
            continue

        try:
            content = f.read_text(encoding="utf-8")
        except Exception as e:
            print(f"  [错误] {f.name} - 读取失败: {e}")
            stats["errors"].append(f.name)
            continue

        if dry_run:
            print(f"  [预览] {f.name} -> {recording.filename} ({template_name})")
            stats["summaries_migrated"] += 1
            continue

        summary = Summary(
            recording_id=recording.id,
            mode=mode,
            content=content,
            created_at=created_at,
        )
        session.add(summary)

        print(f"  [完成] {f.name} -> {recording.filename} ({template_name})")
        stats["summaries_migrated"] += 1


def update_completed_status(session: Session, recording_lookup: dict[str, Recording], dry_run: bool):
    for recording in recording_lookup.values():
        if recording.status != "transcribed":
            continue

        has_summary = session.exec(
            select(Summary).where(Summary.recording_id == recording.id).limit(1)
        ).first()

        if has_summary:
            if dry_run:
                print(f"  [预览] 状态更新: {recording.filename} -> completed")
            else:
                recording.status = "completed"
                recording.updated_at = datetime.now(timezone.utc)
                session.add(recording)
                print(f"  [完成] 状态更新: {recording.filename} -> completed")


def backup_database():
    settings = get_settings()
    db_path = settings.resolved_data_dir / "app.db"
    if not db_path.exists():
        return
    backup_path = db_path.with_suffix(f".db.backup-{datetime.now().strftime('%Y%m%d-%H%M%S')}")
    shutil.copy2(db_path, backup_path)
    print(f"数据库已备份到: {backup_path}")
    return backup_path


def main():
    dry_run = "--dry-run" in sys.argv

    print("=" * 60)
    print("录音转写/摘要迁移工具")
    if dry_run:
        print(">>> 预览模式 (--dry-run)，不会实际修改数据 <<<")
    print("=" * 60)

    settings = get_settings()
    transcript_dir = settings.resolved_transcript_dir
    summary_dir = settings.resolved_summary_dir

    print(f"\n转写目录: {transcript_dir}")
    print(f"摘要目录: {summary_dir}")

    stats = {
        "transcripts_migrated": 0,
        "summaries_migrated": 0,
        "skipped_transcripts": [],
        "skipped_summaries": [],
        "errors": [],
    }

    if not dry_run:
        backup_database()

    with Session(engine) as session:
        recording_lookup = build_lookup(session)
        print(f"\n数据库中有 {len(recording_lookup)} 条录音记录")

        uuid_mapping = build_uuid_mapping(summary_dir)
        print(f"从摘要文件中解析到 {len(uuid_mapping)} 个 旧UUID→文件名 映射")

        print("\n--- 阶段 1: 迁移 UUID 命名的转写 JSON ---")
        migrate_uuid_transcripts(
            session, transcript_dir, uuid_mapping,
            recording_lookup, dry_run, stats,
        )

        print("\n--- 阶段 2: 迁移文件名命名的转写 JSON (.transcribe.json) ---")
        migrate_transcribe_json(
            session, transcript_dir, recording_lookup, dry_run, stats,
        )

        print("\n--- 阶段 3: 迁移摘要 MD 文件 ---")
        migrate_summaries(
            session, summary_dir, recording_lookup, uuid_mapping, dry_run, stats,
        )

        print("\n--- 阶段 4: 更新录音状态 ---")
        update_completed_status(session, recording_lookup, dry_run)

        if not dry_run:
            session.commit()
            print("\n所有更改已提交到数据库。")
        else:
            session.rollback()
            print("\n预览完成，未修改任何数据。")

    print("\n" + "=" * 60)
    print("迁移统计")
    print("=" * 60)
    print(f"  转写已迁移:     {stats['transcripts_migrated']}")
    print(f"  摘要已迁移:     {stats['summaries_migrated']}")
    print(f"  转写跳过:       {len(stats['skipped_transcripts'])}")
    for name in stats["skipped_transcripts"]:
        print(f"    - {name}")
    print(f"  摘要跳过:       {len(stats['skipped_summaries'])}")
    for name in stats["skipped_summaries"]:
        print(f"    - {name}")
    print(f"  错误:           {len(stats['errors'])}")
    for name in stats["errors"]:
        print(f"    - {name}")
    print("=" * 60)

    if not dry_run:
        print("\n如需回滚，可恢复备份的数据库文件。")
    print("完成。")


if __name__ == "__main__":
    main()
