import React from "react";
import { Clock3, FileAudio, Loader2, Pause, Play, SkipBack, SkipForward, Sparkles, XCircle } from "lucide-react";
import {
  formatDate,
  formatDuration,
  formatSize,
  taskLabel,
} from "../../lib/format";
import { apiUrl } from "../../lib/api";
import { useAppStore } from "../../stores/appStore";
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

const SPEED_OPTIONS = [0.5, 0.75, 1, 1.25, 1.5, 2];

export function RecordingDetailPanel({
  selected,
  detailTab,
  setDetailTab,
  summaryMode,
  setSummaryMode,
  summaryTemplates,
  activeTask,
  busy,
  allTags,
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
  allTags: string[];
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
  const [isPlaying, setIsPlaying] = React.useState(false);
  const [playbackRate, setPlaybackRate] = React.useState(1);
  const [editingSegmentId, setEditingSegmentId] = React.useState<string | null>(null);
  const [segmentDraft, setSegmentDraft] = React.useState("");
  const [tagDraft, setTagDraft] = React.useState("");
  const [summaryPickerOpen, setSummaryPickerOpen] = React.useState(false);
  const [expandedSummaryIds, setExpandedSummaryIds] = React.useState<string[]>([]);
  const [tagSuggestions, setTagSuggestions] = React.useState<string[]>([]);
  const [showTagSuggestions, setShowTagSuggestions] = React.useState(false);

  const setPlaybackPosition = useAppStore((s) => s.setPlaybackPosition);
  const getPlaybackPosition = useAppStore((s) => s.getPlaybackPosition);

  // Restore playback position when selecting a recording
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
    setIsPlaying(false);
    if (audioRef.current) {
      audioRef.current.pause();
      const savedPos = getPlaybackPosition(selected.recording.id);
      audioRef.current.currentTime = savedPos;
      setCurrentTime(savedPos);
    }
  }, [selected?.recording.id, selected?.recording.tags, selected?.summaries.length]);

  // Save playback position periodically and on unmount
  React.useEffect(() => {
    if (!selected) return;
    const interval = window.setInterval(() => {
      if (audioRef.current && currentTime > 0) {
        setPlaybackPosition(selected.recording.id, currentTime);
      }
    }, 3000);
    return () => {
      window.clearInterval(interval);
      if (audioRef.current && selected) {
        setPlaybackPosition(selected.recording.id, audioRef.current.currentTime);
      }
    };
  }, [selected?.recording.id, currentTime]);

  // Keyboard shortcuts
  React.useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (!selected || !audioRef.current) return;
      // Don't intercept when typing in inputs
      const tag = (e.target as HTMLElement).tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;

      switch (e.key) {
        case " ":
          e.preventDefault();
          if (audioRef.current.paused) {
            audioRef.current.play();
          } else {
            audioRef.current.pause();
          }
          break;
        case "ArrowLeft":
          e.preventDefault();
          audioRef.current.currentTime = Math.max(0, audioRef.current.currentTime - 5);
          break;
        case "ArrowRight":
          e.preventDefault();
          audioRef.current.currentTime = Math.min(
            audioRef.current.duration || 0,
            audioRef.current.currentTime + 5
          );
          break;
        case "[":
          e.preventDefault();
          setPlaybackRate((r) => {
            const idx = SPEED_OPTIONS.indexOf(r);
            const next = SPEED_OPTIONS[Math.max(0, idx - 1)];
            if (audioRef.current) audioRef.current.playbackRate = next;
            return next;
          });
          break;
        case "]":
          e.preventDefault();
          setPlaybackRate((r) => {
            const idx = SPEED_OPTIONS.indexOf(r);
            const next = SPEED_OPTIONS[Math.min(SPEED_OPTIONS.length - 1, idx + 1)];
            if (audioRef.current) audioRef.current.playbackRate = next;
            return next;
          });
          break;
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selected]);

  // Sync playbackRate to audio element
  React.useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

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
        {/* Compact hero: icon + filename/meta + status inline */}
        <header className="detail-hero">
          <div className="detail-hero-icon">
            <FileAudio size={14} strokeWidth={2.5} />
          </div>
          <div className="detail-hero-info">
            <h2 title={selected.recording.filename}>{selected.recording.filename}</h2>
            <span className="detail-hero-meta">
              {formatSize(selected.recording.file_size_bytes)} · {formatDuration(selected.recording.duration_seconds)}
            </span>
            <StatusBadge status={selected.recording.status} />
            <span className="detail-meta-tag">
              {selected.recording.source_type === "watch" ? "监控" : "上传"}
            </span>
          </div>
        </header>

        {/* Action buttons */}
        <div className="detail-actions">
          <button className="btn btn-secondary btn-sm" disabled={busy} onClick={runTranscription}>
            {busy ? <Loader2 className="spin" size={13} /> : <FileAudio size={13} />}
            转写
          </button>
          <button
            className="btn btn-primary btn-sm"
            disabled={busy || selected.segments.length === 0}
            onClick={() => setSummaryPickerOpen(true)}
          >
            <Sparkles size={13} />
            摘要（{activeTemplate?.name ?? "结构化"}）
          </button>
        </div>

        {/* Audio + waveform in compact grid */}
        <section className="audio-section" aria-label="音频播放">
          <div className="audio-header">
            <span>音频</span>
            <strong>{formatDuration(currentTime)} / {formatDuration(selected.recording.duration_seconds)}</strong>
          </div>
          <div className="waveform">
            {Array.from({ length: 36 }, (_, i) => (
              <div
                key={i}
                className={`wave-bar${currentTime > 0 ? " active" : ""}`}
                style={{
                  animationDelay: `${(i * 0.07).toFixed(2)}s`,
                }}
              />
            ))}
          </div>
          {/* Custom player controls */}
          <div className="player-controls">
            <button
              className="btn btn-icon btn-sm"
              onClick={() => {
                if (audioRef.current) audioRef.current.currentTime = Math.max(0, audioRef.current.currentTime - 10);
              }}
              title="后退 10 秒"
            >
              <SkipBack size={14} />
            </button>
            <button
              className="btn btn-icon btn-sm player-play-btn"
              onClick={() => {
                if (!audioRef.current) return;
                if (audioRef.current.paused) {
                  audioRef.current.play();
                } else {
                  audioRef.current.pause();
                }
              }}
              title={isPlaying ? "暂停 (空格)" : "播放 (空格)"}
            >
              {isPlaying ? <Pause size={16} /> : <Play size={16} />}
            </button>
            <button
              className="btn btn-icon btn-sm"
              onClick={() => {
                if (audioRef.current) {
                  audioRef.current.currentTime = Math.min(
                    audioRef.current.duration || 0,
                    audioRef.current.currentTime + 10
                  );
                }
              }}
              title="前进 10 秒"
            >
              <SkipForward size={14} />
            </button>
            <div className="player-speed">
              {SPEED_OPTIONS.map((rate) => (
                <button
                  key={rate}
                  className={`btn btn-ghost btn-sm${playbackRate === rate ? " active" : ""}`}
                  onClick={() => {
                    setPlaybackRate(rate);
                    if (audioRef.current) audioRef.current.playbackRate = rate;
                  }}
                  title={`${rate}x 倍速`}
                >
                  {rate}x
                </button>
              ))}
            </div>
          </div>
          {/* Hidden native audio element for actual playback */}
          <audio
            ref={audioRef}
            preload="metadata"
            src={audioSrc}
            onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
            onLoadedMetadata={(e) => {
              setCurrentTime(e.currentTarget.currentTime);
              // Restore playback rate
              e.currentTarget.playbackRate = playbackRate;
            }}
            onPlay={() => setIsPlaying(true)}
            onPause={() => setIsPlaying(false)}
            className="audio-element-hidden"
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
                {(() => {
                  const work = summaryTemplates.filter((t) => t.category === "work");
                  const life = summaryTemplates.filter((t) => t.category === "life");
                  const general = summaryTemplates.filter((t) => !t.category || t.category === "general");
                  const groups: { label: string; items: typeof summaryTemplates }[] = [];
                  if (work.length) groups.push({ label: "💼 工作场景", items: work });
                  if (life.length) groups.push({ label: "🏠 生活场景", items: life });
                  if (general.length) groups.push({ label: "📋 通用", items: general });
                  return groups.map((group) => (
                    <div key={group.label} className="template-group">
                      <div className="template-group-label">{group.label}</div>
                      {group.items.map((t) => (
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
                  ));
                })()}
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
                <div className="tag-input-wrapper">
                  <input
                    className="form-input"
                    value={tagDraft}
                    placeholder="用逗号分隔标签"
                    onChange={(e) => {
                      setTagDraft(e.target.value);
                      // Show suggestions based on last typed tag
                      const parts = e.target.value.split(",");
                      const current = parts[parts.length - 1].trim().toLowerCase();
                      if (current && allTags.length > 0) {
                        const existing = new Set(parts.slice(0, -1).map((t) => t.trim().toLowerCase()));
                        const suggestions = allTags.filter(
                          (t) => t.toLowerCase().includes(current) && !existing.has(t.toLowerCase())
                        ).slice(0, 8);
                        setTagSuggestions(suggestions);
                        setShowTagSuggestions(suggestions.length > 0);
                      } else {
                        setShowTagSuggestions(false);
                      }
                    }}
                    onBlur={() => {
                      // Delay hide to allow click on suggestion
                      setTimeout(() => setShowTagSuggestions(false), 200);
                    }}
                    onFocus={() => {
                      if (tagSuggestions.length > 0) setShowTagSuggestions(true);
                    }}
                  />
                  {showTagSuggestions && (
                    <div className="tag-suggestions">
                      {tagSuggestions.map((tag) => (
                        <button
                          key={tag}
                          className="tag-suggestion"
                          onMouseDown={(e) => {
                            e.preventDefault();
                            const parts = tagDraft.split(",");
                            parts[parts.length - 1] = " " + tag;
                            const newDraft = parts.join(",");
                            setTagDraft(newDraft);
                            setShowTagSuggestions(false);
                          }}
                        >
                          {tag}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
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
