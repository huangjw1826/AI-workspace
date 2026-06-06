import React from "react";
import { Clock3, FileAudio, Loader2, Sparkles, XCircle } from "lucide-react";
import {
  formatDate,
  formatDuration,
  formatSize,
  taskLabel,
} from "../../lib/format";
import { apiUrl } from "../../lib/api";
import type { DetailTab } from "../../lib/viewTypes";
import type {
  ExportFormat,
  RecordingDetail,
  SummaryTemplate,
  Task,
} from "../../lib/types";
import { ExportButtons } from "../ui/ExportButtons";
import { StatusBadge } from "../ui/StatusBadge";
import { SummaryCard } from "./SummaryCard";

export function RecordingDetailPanel({
  selected,
  detailTab,
  setDetailTab,
  summaryMode,
  setSummaryMode,
  summaryTemplates,
  activeTask,
  busy,
  runTranscription,
  runSummary,
  cancelActiveTask,
  updateTranscriptSegment,
  updateRecordingTags,
  downloadTranscript,
  downloadSummary,
  deleteSummary,
}: {
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
      const currentIds = new Set(selected.summaries.map((s) => s.id));
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
    return (
      <aside className="detail-panel">
        <div className="detail-empty">
          <div className="detail-empty-icon">
            <FileAudio size={24} strokeWidth={1.5} />
          </div>
          <p>选择一条录音查看详情</p>
        </div>
      </aside>
    );
  }

  const activeTemplate = summaryTemplates.find((t) => t.id === summaryMode);
  const audioSrc = apiUrl(`/api/recordings/${selected.recording.id}/audio`);
  const sortedSummaries = [...selected.summaries].sort(
    (a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
  );
  const latestSummaryId = sortedSummaries[0]?.id;
  const canCancelTask = activeTask && !["completed", "error", "cancelled"].includes(activeTask.status);

  function toggleSummary(summaryId: string) {
    setExpandedSummaryIds((ids) =>
      ids.includes(summaryId) ? ids.filter((id) => id !== summaryId) : [...ids, summaryId]
    );
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
    updateTranscriptSegment(segmentId, segmentDraft);
    setEditingSegmentId(null);
  }

  function saveTags() {
    updateRecordingTags(tagDraft.split(/[,\n]/));
  }

  const speakerSet = new Set(selected.segments.map((s) => s.speaker));
  const showSpeaker = speakerSet.size > 1;

  return (
    <aside className="detail-panel">
      {/* Fixed Top Region */}
      <div className="detail-top">
        <header className="detail-hero">
          <div className="detail-hero-icon">
            <Sparkles size={17} strokeWidth={2} />
          </div>
          <div>
            <h2>{selected.recording.filename}</h2>
            <p>
              {formatSize(selected.recording.file_size_bytes)} ·{" "}
              {formatDuration(selected.recording.duration_seconds)}
            </p>
          </div>
        </header>

        <div className="detail-meta">
          <StatusBadge status={selected.recording.status} />
          <span className="detail-meta-tag">
            {selected.recording.source_type === "watch" ? "目录监控" : "上传"}
          </span>
        </div>

        <div className="detail-actions">
          <button className="btn btn-secondary btn-sm" disabled={busy} onClick={runTranscription}>
            {busy ? <Loader2 className="spin" size={14} /> : <FileAudio size={14} />}
            转写
          </button>
          <button
            className="btn btn-primary btn-sm"
            disabled={busy || selected.segments.length === 0}
            onClick={() => setSummaryPickerOpen(true)}
          >
            <Sparkles size={14} />
            生成摘要
          </button>
        </div>

        <p className="detail-summary-mode">
          当前模板：{activeTemplate?.name ?? "结构化摘要"}
        </p>

        <section className="audio-section" aria-label="音频播放">
          <div className="audio-header">
            <span>音频播放</span>
            <strong>
              {formatDuration(currentTime)} / {formatDuration(selected.recording.duration_seconds)}
            </strong>
          </div>
          <div className="waveform">
            {Array.from({ length: 40 }, (_, i) => (
              <div
                key={i}
                className={`wave-bar${currentTime > 0 ? " active" : ""}`}
                style={{
                  animationDelay: `${(i * 0.07).toFixed(2)}s`,
                  height: `${4 + Math.random() * 12}px`,
                }}
              />
            ))}
          </div>
          <audio
            ref={audioRef}
            controls
            preload="metadata"
            src={audioSrc}
            onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
            onLoadedMetadata={(e) => setCurrentTime(e.currentTarget.currentTime)}
            className="audio-element"
          />
        </section>

        {/* Summary Picker Modal */}
        {summaryPickerOpen && (
          <div
            className="summary-picker-overlay"
            role="dialog"
            aria-modal="true"
            onClick={(e) => {
              if (e.target === e.currentTarget) setSummaryPickerOpen(false);
            }}
          >
            <div className="summary-picker-card anim-scale-in">
              <div className="summary-picker-header">
                <h3>选择摘要类型</h3>
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => setSummaryPickerOpen(false)}
                >
                  关闭
                </button>
              </div>
              <div className="template-list">
                {summaryTemplates.map((t) => (
                  <button
                    key={t.id}
                    className={`template-option${t.id === summaryMode ? " active" : ""}`}
                    onClick={() => {
                      setSummaryMode(t.id);
                      setSummaryPickerOpen(false);
                      runSummary(t.id);
                    }}
                  >
                    <strong>{t.name}</strong>
                    <span>{t.description}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Active Task */}
        {activeTask && (
          <div className="task-card">
            <span className="task-label">{taskLabel(activeTask)}</span>
            <progress value={activeTask.progress} max={100} />
            <div className="task-footer">
              <span>
                {activeTask.status} {activeTask.progress}%
              </span>
              {canCancelTask && (
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={busy}
                  onClick={cancelActiveTask}
                >
                  <XCircle size={14} /> 取消
                </button>
              )}
            </div>
          </div>
        )}

        {/* Tabs */}
        <div className="detail-tabs">
          {(["transcript", "summary", "info"] as DetailTab[]).map((tab) => (
            <button
              key={tab}
              className={`detail-tab${detailTab === tab ? " active" : ""}`}
              onClick={() => setDetailTab(tab)}
            >
              {tab === "transcript" ? "转写" : tab === "summary" ? `摘要 (${selected.summaries.length})` : "信息"}
            </button>
          ))}
        </div>
      </div>

      {/* Scrollable Content */}
      <div className="detail-scroll">
        {/* Transcript Tab */}
        {detailTab === "transcript" && (
          <section className="tab-body">
            <div className="section-head">
              <h3>转写内容</h3>
              {selected.segments.length > 0 && (
                <ExportButtons onExport={downloadTranscript} formats={["md", "txt", "json", "srt", "docx"]} />
              )}
            </div>
            {selected.segments.length === 0 ? (
              <p className="muted">还没有转写结果。</p>
            ) : (
              selected.segments.map((seg) => {
                const active = isActiveSegment(seg.start_time, seg.end_time);
                const editing = editingSegmentId === seg.id;
                return (
                  <article key={seg.id} className={`segment${active ? " active" : ""}`}>
                    <div className="segment-top">
                      {showSpeaker && (
                        <span className="segment-speaker">
                          {seg.speaker.replace("speaker_", "说话人 ")}
                        </span>
                      )}
                      <button className="segment-time" onClick={() => seekTo(seg.start_time)}>
                        <Clock3 size={12} />
                        {formatDuration(seg.start_time)} - {formatDuration(seg.end_time)}
                      </button>
                      {!editing && (
                        <button
                          className="btn btn-ghost btn-sm"
                          onClick={() => startSegmentEdit(seg.id, seg.text)}
                        >
                          编辑
                        </button>
                      )}
                    </div>
                    {editing ? (
                      <div className="segment-editor">
                        <textarea
                          className="form-input"
                          value={segmentDraft}
                          onChange={(e) => setSegmentDraft(e.target.value)}
                        />
                        <div className="segment-editor-actions">
                          <button
                            className="btn btn-primary btn-sm"
                            disabled={busy || !segmentDraft.trim()}
                            onClick={() => saveSegmentEdit(seg.id)}
                          >
                            保存
                          </button>
                          <button
                            className="btn btn-ghost btn-sm"
                            onClick={() => setEditingSegmentId(null)}
                          >
                            取消
                          </button>
                        </div>
                      </div>
                    ) : (
                      <p>{seg.text}</p>
                    )}
                  </article>
                );
              })
            )}
          </section>
        )}

        {/* Summary Tab */}
        {detailTab === "summary" && (
          <section className="tab-body">
            <div className="section-head">
              <h3>摘要结果</h3>
              {sortedSummaries.length > 1 && (
                <div className="summary-tools">
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => setExpandedSummaryIds(latestSummaryId ? [latestSummaryId] : [])}
                  >
                    展开最新
                  </button>
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => setExpandedSummaryIds([])}
                  >
                    全部收起
                  </button>
                </div>
              )}
            </div>
            {sortedSummaries.length === 0 ? (
              <p className="muted">转写完成后可以生成摘要。</p>
            ) : (
              sortedSummaries.map((summary) => {
                const tplName =
                  summaryTemplates.find((t) => t.id === summary.mode)?.name ?? summary.mode;
                return (
                  <SummaryCard
                    key={summary.id}
                    summary={summary}
                    templateName={tplName}
                    isLatest={summary.id === latestSummaryId}
                    expanded={expandedSummaryIds.includes(summary.id)}
                    busy={busy}
                    onToggle={() => toggleSummary(summary.id)}
                    onExport={(fmt) => downloadSummary(summary.id, fmt)}
                    onDelete={() => deleteSummary(summary.id)}
                  />
                );
              })
            )}
          </section>
        )}

        {/* Info Tab */}
        {detailTab === "info" && (
          <section className="tab-body">
            <div className="tag-editor">
              <label className="tag-label">
                <span>标签</span>
                <input
                  className="form-input"
                  value={tagDraft}
                  placeholder="用逗号分隔标签"
                  onChange={(e) => setTagDraft(e.target.value)}
                />
              </label>
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={saveTags}>
                保存
              </button>
            </div>
            <div className="info-list">
              <InfoRow label="文件名" value={selected.recording.filename} />
              <InfoRow label="格式" value={selected.recording.format} />
              <InfoRow label="大小" value={formatSize(selected.recording.file_size_bytes)} />
              <InfoRow label="时长" value={formatDuration(selected.recording.duration_seconds)} />
              <InfoRow label="来源" value={selected.recording.source_type === "watch" ? "目录监控" : "上传"} />
              <InfoRow label="来源路径" value={selected.recording.source_path || "--"} />
              <InfoRow label="原始文件" value={selected.recording.original_path} />
              <InfoRow label="源文件时间" value={formatDate(selected.recording.source_mtime)} />
              <InfoRow label="加入时间" value={formatDate(selected.recording.created_at)} />
              <InfoRow label="内容指纹" value={selected.recording.content_hash || "--"} />
            </div>
          </section>
        )}
      </div>
    </aside>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="info-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
