import React from "react";
import { Clock3, FileAudio, Loader2, Sparkles, XCircle } from "lucide-react";
import {
  formatDate,
  formatDuration,
  formatSize,
  taskLabel,
  toggleValue,
} from "../../lib/format";
import { apiUrl } from "../../lib/api";
import type { DetailTab } from "../../lib/viewTypes";
import type { ExportFormat, RecordingDetail, SummaryTemplate, Task } from "../../lib/types";
import { ExportButtons } from "../ui/ExportButtons";
import { StatusBadge } from "../ui/StatusBadge";
import { SummaryCard } from "./SummaryCard";

export function RecordingDetailPanel(props: {
  selected: RecordingDetail | null;
  detailTab: DetailTab;
  setDetailTab: (tab: DetailTab) => void;
  summaryMode: string;
  setSummaryMode: (mode: string) => void;
  summaryTemplates: SummaryTemplate[];
  activeTask: Task | null;
  busy: boolean;
  runTranscription: () => void;
  runSummary: (mode: string) => void;
  cancelActiveTask: () => void;
  updateTranscriptSegment: (segmentId: string, text: string) => void;
  updateRecordingTags: (tags: string[]) => void;
  downloadTranscript: (format: ExportFormat) => void;
  downloadSummary: (summaryId: string, format: ExportFormat) => void;
  deleteSummary: (summaryId: string) => void;
}) {
  const { selected } = props;
  const audioRef = React.useRef<HTMLAudioElement | null>(null);
  const [currentTime, setCurrentTime] = React.useState(0);
  const [editingSegmentId, setEditingSegmentId] = React.useState<string | null>(null);
  const [segmentDraft, setSegmentDraft] = React.useState("");
  const [tagDraft, setTagDraft] = React.useState("");
  const [summaryPickerOpen, setSummaryPickerOpen] = React.useState(false);
  const [expandedSummaryIds, setExpandedSummaryIds] = React.useState<string[]>([]);

  React.useEffect(() => {
    if (!selected) {
      setExpandedSummaryIds([]);
      return;
    }
    const latest = [...selected.summaries].sort(
      (a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
    )[0];
    setExpandedSummaryIds((ids) => {
      const currentIds = new Set(selected.summaries.map((summary) => summary.id));
      const retained = ids.filter((id) => currentIds.has(id));
      if (retained.length > 0) return retained;
      return latest ? [latest.id] : [];
    });
    setEditingSegmentId(null);
    setSegmentDraft("");
    setTagDraft(selected.recording.tags);
    setCurrentTime(0);
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
    }
  }, [selected?.recording.id, selected?.recording.tags, selected?.summaries.length]);

  if (!selected) {
    return <aside className="detail-panel empty-panel">选择一条录音查看详情</aside>;
  }

  const activeTemplate = props.summaryTemplates.find((template) => template.id === props.summaryMode);
  const audioSrc = apiUrl(`/api/recordings/${selected.recording.id}/audio`);
  const sortedSummaries = [...selected.summaries].sort(
    (a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
  );
  const latestSummaryId = sortedSummaries[0]?.id;
  const canCancelTask = props.activeTask && !["completed", "error", "cancelled"].includes(props.activeTask.status);

  function toggleSummary(summaryId: string) {
    setExpandedSummaryIds((ids) => toggleValue(ids, summaryId));
  }

  function seekTo(startTime: number) {
    if (!audioRef.current) return;
    audioRef.current.currentTime = Math.max(0, startTime);
    audioRef.current.play().catch(() => undefined);
  }

  function isActiveSegment(startTime: number, endTime: number) {
    if (endTime <= startTime) return Math.abs(currentTime - startTime) < 0.75;
    return currentTime >= startTime && currentTime <= endTime;
  }

  function startSegmentEdit(segmentId: string, text: string) {
    setEditingSegmentId(segmentId);
    setSegmentDraft(text);
  }

  function saveSegmentEdit(segmentId: string) {
    props.updateTranscriptSegment(segmentId, segmentDraft);
    setEditingSegmentId(null);
  }

  function saveTags() {
    props.updateRecordingTags(tagDraft.split(/[,\n]/));
  }

  return (
    <aside className="detail-panel">
      <div className="detail-fixed-region">
      <header className="detail-hero">
        <div className="detail-title">
          <div className="detail-icon"><Sparkles size={18} /></div>
          <div>
            <h2>{selected.recording.filename}</h2>
            <p>{formatSize(selected.recording.file_size_bytes)} · {formatDuration(selected.recording.duration_seconds)}</p>
          </div>
        </div>
        <div className="detail-meta">
          <StatusBadge status={selected.recording.status} />
          <span>{selected.recording.source_type === "watch" ? "目录监控" : "上传"}</span>
        </div>
      </header>
      <div className="ai-action-panel">
        <div>
          <span>AI 处理</span>
          <strong>{selected.segments.length > 0 ? "可生成摘要" : "等待转写"}</strong>
        </div>
        <div className="detail-actions">
          <button className="primary" disabled={props.busy} onClick={props.runTranscription}>{props.busy ? <Loader2 className="spin" size={15} /> : <FileAudio size={15} />} 转写</button>
          <button className="primary" disabled={props.busy || selected.segments.length === 0} onClick={() => setSummaryPickerOpen(true)}>
            <Sparkles size={15} /> 摘要
          </button>
        </div>
      </div>
      <p className="muted">当前摘要模板：{activeTemplate?.name ?? "结构化摘要"}</p>
      <section className="audio-player-panel" aria-label="音频播放">
        <div>
          <span>音频播放</span>
          <strong>{formatDuration(currentTime)} / {formatDuration(selected.recording.duration_seconds)}</strong>
        </div>
        <audio
          ref={audioRef}
          controls
          preload="metadata"
          src={audioSrc}
          onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
          onLoadedMetadata={(event) => setCurrentTime(event.currentTarget.currentTime)}
        />
      </section>
      {summaryPickerOpen && (
        <div className="summary-picker" role="dialog" aria-modal="true" aria-label="选择摘要模板">
          <div className="summary-picker-card">
            <div className="section-head">
              <h3>选择摘要类型</h3>
              <button className="secondary compact" onClick={() => setSummaryPickerOpen(false)}>关闭</button>
            </div>
            <div className="template-list">
              {props.summaryTemplates.map((template) => (
                <button
                  key={template.id}
                  className={template.id === props.summaryMode ? "template-option active" : "template-option"}
                  onClick={() => {
                    props.setSummaryMode(template.id);
                    setSummaryPickerOpen(false);
                    props.runSummary(template.id);
                  }}
                >
                  <strong>{template.name}</strong>
                  <span>{template.description}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
      {props.activeTask && (
        <div className="task">
          <span>{taskLabel(props.activeTask)}</span>
          <progress value={props.activeTask.progress} max={100} />
          <div className="task-footer">
            <span>{props.activeTask.status} {props.activeTask.progress}%</span>
            {canCancelTask && (
              <button className="secondary compact" disabled={props.busy} onClick={props.cancelActiveTask}>
                <XCircle size={14} /> 取消
              </button>
            )}
          </div>
        </div>
      )}
      <div className="tabs">
        <button className={props.detailTab === "transcript" ? "active" : ""} onClick={() => props.setDetailTab("transcript")}>转写</button>
        <button className={props.detailTab === "summary" ? "active" : ""} onClick={() => props.setDetailTab("summary")}>摘要</button>
        <button className={props.detailTab === "info" ? "active" : ""} onClick={() => props.setDetailTab("info")}>信息</button>
      </div>
      </div>
      <div className="detail-scroll-region">
      {props.detailTab === "transcript" && (
        <section className="tab-body">
          <div className="section-head"><h3>转写内容</h3>{selected.segments.length > 0 && <ExportButtons onExport={props.downloadTranscript} formats={["md", "txt", "json", "srt", "docx"]} />}</div>
          {selected.segments.length === 0 ? <p className="muted">还没有转写结果。</p> : selected.segments.map((segment) => {
            const active = isActiveSegment(segment.start_time, segment.end_time);
            const editing = editingSegmentId === segment.id;
            return (
              <article className={active ? "segment active" : "segment"} key={segment.id}>
                <div className="segment-toolbar">
                  <button className="segment-time" onClick={() => seekTo(segment.start_time)}>
                    <Clock3 size={13} /> {formatDuration(segment.start_time)} - {formatDuration(segment.end_time)}
                  </button>
                  {!editing && <button className="secondary compact" onClick={() => startSegmentEdit(segment.id, segment.text)}>编辑</button>}
                </div>
                {editing ? (
                  <div className="segment-editor">
                    <textarea value={segmentDraft} onChange={(event) => setSegmentDraft(event.target.value)} />
                    <div className="button-row">
                      <button className="primary compact" disabled={props.busy || !segmentDraft.trim()} onClick={() => saveSegmentEdit(segment.id)}>保存</button>
                      <button className="secondary compact" onClick={() => setEditingSegmentId(null)}>取消</button>
                    </div>
                  </div>
                ) : (
                  <p>{segment.text}</p>
                )}
              </article>
            );
          })}
        </section>
      )}
      {props.detailTab === "summary" && (
        <section className="tab-body">
          <div className="section-head">
            <h3>摘要结果</h3>
            {sortedSummaries.length > 1 && (
              <div className="summary-tools">
                <button onClick={() => setExpandedSummaryIds(latestSummaryId ? [latestSummaryId] : [])}>展开最新</button>
                <button onClick={() => setExpandedSummaryIds([])}>全部收起</button>
              </div>
            )}
          </div>
          {sortedSummaries.length === 0 ? <p className="muted">转写完成后可以生成摘要。</p> : sortedSummaries.map((summary) => {
            const templateName = props.summaryTemplates.find((template) => template.id === summary.mode)?.name ?? summary.mode;
            const expanded = expandedSummaryIds.includes(summary.id);
            return (
              <SummaryCard
                key={summary.id}
                summary={summary}
                templateName={templateName}
                isLatest={summary.id === latestSummaryId}
                expanded={expanded}
                busy={props.busy}
                onToggle={() => toggleSummary(summary.id)}
                onExport={(format) => props.downloadSummary(summary.id, format)}
                onDelete={() => props.deleteSummary(summary.id)}
              />
            );
          })}
        </section>
      )}
      {props.detailTab === "info" && (
        <section className="tab-body info-list">
          <div className="tag-editor">
            <label>
              <span>标签</span>
              <input value={tagDraft} placeholder="用逗号分隔标签" onChange={(event) => setTagDraft(event.target.value)} />
            </label>
            <button className="secondary compact" disabled={props.busy} onClick={saveTags}>保存标签</button>
          </div>
          <InfoRow label="文件名" value={selected.recording.filename} />
          <InfoRow label="格式" value={selected.recording.format} />
          <InfoRow label="大小" value={formatSize(selected.recording.file_size_bytes)} />
          <InfoRow label="时长" value={formatDuration(selected.recording.duration_seconds)} />
          <InfoRow label="来源" value={selected.recording.source_type === "watch" ? "目录监控" : "上传"} />
          <InfoRow label="来源路径" value={selected.recording.source_path || "--"} />
          <InfoRow label="原始文件" value={selected.recording.original_path} />
          <InfoRow label="源文件时间" value={formatDate(selected.recording.source_mtime)} />
          <InfoRow label="创建时间" value={formatDate(selected.recording.created_at)} />
          <InfoRow label="内容指纹" value={selected.recording.content_hash || "--"} />
        </section>
      )}
      </div>
    </aside>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="info-row"><span>{label}</span><strong>{value}</strong></div>;
}
