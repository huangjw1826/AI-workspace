import React from "react";
import {
  CheckSquare,
  Clock3,
  Database,
  FileAudio,
  Filter,
  Search,
  Sparkles,
  Tag,
  Trash2,
  TrendingUp,
} from "lucide-react";
import { MetricCard } from "../components/recording/MetricCard";
import { RecordingDetailPanel } from "../components/recording/RecordingDetailPanel";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  clampPercent,
  formatDate,
  formatDuration,
  formatSize,
  statusLabel,
  toggleValue,
} from "../lib/format";
import type { DetailTab, LibraryFilters, SortKey } from "../lib/viewTypes";
import type {
  ExportFormat,
  Recording,
  RecordingDetail,
  SummaryTemplate,
  Task,
} from "../lib/types";

const STATUS_OPTIONS = [
  { value: "uploaded", label: "待转写" },
  { value: "queued", label: "排队中" },
  { value: "normalizing", label: "处理中" },
  { value: "transcribing", label: "转写中" },
  { value: "transcribed", label: "已转写" },
  { value: "completed", label: "已摘要" },
  { value: "cancelled", label: "已取消" },
  { value: "error", label: "错误" },
];

const SOURCE_OPTIONS = [
  { value: "upload", label: "上传" },
  { value: "watch", label: "目录监控" },
];

export function LibraryPage({
  recordings,
  filteredRecordings,
  selected,
  selectedIds,
  setSelectedIds,
  detailTab,
  setDetailTab,
  summaryMode,
  setSummaryMode,
  summaryTemplates,
  activeTask,
  busy,
  draftFilters,
  appliedFilters,
  sortKey,
  setDraftFilters,
  setSortKey,
  applyFilters,
  resetFilters,
  clearAppliedQuery,
  clearAppliedTag,
  clearAppliedStatus,
  clearAppliedSource,
  searchMatchPreviews,
  allTags,
  selectRecording,
  handleDelete,
  runTranscription,
  runSummary,
  runBatchTranscription,
  runBatchSummary,
  deleteSelectedRecordings,
  cancelActiveTask,
  updateTranscriptSegment,
  updateRecordingTags,
  downloadTranscript,
  downloadSummary,
  deleteSummary,
}: {
  recordings: Recording[];
  filteredRecordings: Recording[];
  selected: RecordingDetail | null;
  selectedIds: string[];
  setSelectedIds: React.Dispatch<React.SetStateAction<string[]>>;
  detailTab: DetailTab;
  setDetailTab: (tab: DetailTab) => void;
  summaryMode: string;
  setSummaryMode: (mode: string) => void;
  summaryTemplates: SummaryTemplate[];
  activeTask: Task | null;
  busy: boolean;
  draftFilters: LibraryFilters;
  appliedFilters: LibraryFilters;
  sortKey: SortKey;
  setDraftFilters: React.Dispatch<React.SetStateAction<LibraryFilters>>;
  setSortKey: React.Dispatch<React.SetStateAction<SortKey>>;
  applyFilters: () => void;
  resetFilters: () => void;
  clearAppliedQuery: () => void;
  clearAppliedTag: () => void;
  clearAppliedStatus: (status: string) => void;
  clearAppliedSource: (source: string) => void;
  searchMatchPreviews: Record<string, string[]>;
  allTags: string[];
  selectRecording: (id: string) => void;
  handleDelete: (recordingId: string, event: React.MouseEvent) => void;
  runTranscription: () => void;
  runSummary: (mode: string) => void;
  runBatchTranscription: () => void;
  runBatchSummary: () => void;
  deleteSelectedRecordings: () => void;
  cancelActiveTask: () => void;
  updateTranscriptSegment: (segmentId: string, text: string) => void;
  updateRecordingTags: (tags: string[]) => void;
  downloadTranscript: (format: ExportFormat) => void;
  downloadSummary: (summaryId: string, format: ExportFormat) => void;
  deleteSummary: (summaryId: string) => void;
}) {
  const stats = React.useMemo(
    () => ({
      total: recordings.length,
      pending: recordings.filter((r) => r.status === "uploaded").length,
      transcribed: recordings.filter((r) =>
        ["transcribed", "completed"].includes(r.status)
      ).length,
      completed: recordings.filter((r) => r.status === "completed").length,
      errors: recordings.filter((r) => r.status === "error").length,
      totalSize: recordings.reduce((s, r) => s + (r.file_size_bytes ?? 0), 0),
      totalDuration: recordings.reduce((s, r) => s + (r.duration_seconds ?? 0), 0),
    }),
    [recordings]
  );

  const completionRate =
    stats.total > 0 ? clampPercent((stats.completed / stats.total) * 100) : 0;

  const draftFilterCount =
    (draftFilters.query.trim() ? 1 : 0) +
    (draftFilters.tag.trim() ? 1 : 0) +
    draftFilters.statuses.length +
    draftFilters.sources.length;

  const visibleIds = filteredRecordings.map((r) => r.id);
  const allVisibleSelected =
    visibleIds.length > 0 && visibleIds.every((id) => selectedIds.includes(id));

  function toggleRecording(id: string) {
    setSelectedIds((ids) => toggleValue(ids, id));
  }

  function toggleVisibleRecordings() {
    setSelectedIds((ids) => {
      const visibleSet = new Set(visibleIds);
      if (allVisibleSelected) return ids.filter((id) => !visibleSet.has(id));
      return Array.from(new Set([...ids, ...visibleIds]));
    });
  }

  return (
    <>
      {/* Metrics */}
      <section className="metrics stagger" aria-label="录音统计">
        <MetricCard
          label="全部录音"
          value={String(stats.total)}
          hint={`总大小 ${formatSize(stats.totalSize)}`}
          icon={<Database size={32} />}
        />
        <MetricCard
          label="待处理"
          value={String(stats.pending)}
          hint={`已转写 ${stats.transcribed} 条`}
          icon={<Clock3 size={32} />}
        />
        <MetricCard
          label="摘要完成率"
          value={`${completionRate}%`}
          hint={`已摘要 ${stats.completed} 条`}
          icon={<Sparkles size={32} />}
          progress={completionRate}
        />
        <MetricCard
          label="累计时长"
          value={formatDuration(stats.totalDuration)}
          hint={stats.errors > 0 ? `${stats.errors} 条错误` : "运行正常"}
          icon={<TrendingUp size={32} />}
        />
      </section>

      <div className="library-layout">
        {/* Left Panel */}
        <section className="main-panel anim-slide-up">
          {/* Toolbar */}
          <div className="toolbar">
            <div className="toolbar-row">
              <div className="search-box search-box-wide">
                <Search size={15} />
                <input
                  value={draftFilters.query}
                  placeholder="搜索文件名、转写、摘要内容..."
                  onChange={(e) =>
                    setDraftFilters((d) => ({ ...d, query: e.target.value }))
                  }
                  onKeyDown={(e) => {
                    if (e.key === "Enter") applyFilters();
                  }}
                />
              </div>
              <div className="search-box search-box-tag">
                <Tag size={15} />
                <input
                  value={draftFilters.tag}
                  placeholder="标签"
                  onChange={(e) =>
                    setDraftFilters((d) => ({ ...d, tag: e.target.value }))
                  }
                  onKeyDown={(e) => {
                    if (e.key === "Enter") applyFilters();
                  }}
                />
              </div>
              <select
                className="form-select"
                value={sortKey}
                onChange={(e) => setSortKey(e.target.value as SortKey)}
              >
                <option value="created_desc">最新优先</option>
                <option value="created_asc">最旧优先</option>
                <option value="duration_desc">时长最长</option>
                <option value="size_desc">文件最大</option>
              </select>
              <button className="btn btn-primary" onClick={applyFilters}>
                <Filter size={15} />
                查询{draftFilterCount > 0 ? ` ${draftFilterCount}` : ""}
              </button>
              <button className="btn btn-ghost" onClick={resetFilters}>
                重置
              </button>
            </div>

            <div className="filter-groups">
              <fieldset className="filter-fieldset">
                <legend>状态</legend>
                <div className="pill-group">
                  {STATUS_OPTIONS.map((opt) => (
                    <label key={opt.value} className="pill">
                      <input
                        type="checkbox"
                        checked={draftFilters.statuses.includes(opt.value)}
                        onChange={() =>
                          setDraftFilters((d) => ({
                            ...d,
                            statuses: toggleValue(d.statuses, opt.value),
                          }))
                        }
                      />
                      {opt.label}
                    </label>
                  ))}
                </div>
              </fieldset>
              <fieldset className="filter-fieldset">
                <legend>来源</legend>
                <div className="pill-group">
                  {SOURCE_OPTIONS.map((opt) => (
                    <label key={opt.value} className="pill">
                      <input
                        type="checkbox"
                        checked={draftFilters.sources.includes(opt.value)}
                        onChange={() =>
                          setDraftFilters((d) => ({
                            ...d,
                            sources: toggleValue(d.sources, opt.value),
                          }))
                        }
                      />
                      {opt.label}
                    </label>
                  ))}
                </div>
              </fieldset>
            </div>
          </div>

          {/* Result strip */}
          <div className="result-strip">
            <span>
              显示 {filteredRecordings.length} / {recordings.length} 条录音
            </span>
            <div className="filter-chips">
              {appliedFilters.query.trim() && (
                <button className="filter-chip" onClick={clearAppliedQuery}>
                  关键词：{appliedFilters.query}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              )}
              {appliedFilters.tag.trim() && (
                <button className="filter-chip" onClick={clearAppliedTag}>
                  标签：{appliedFilters.tag}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              )}
              {appliedFilters.statuses.map((s) => (
                <button key={s} className="filter-chip" onClick={() => clearAppliedStatus(s)}>
                  状态：{statusLabel(s)}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              ))}
              {appliedFilters.sources.map((s) => (
                <button key={s} className="filter-chip" onClick={() => clearAppliedSource(s)}>
                  来源：{s === "watch" ? "目录监控" : "上传"}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              ))}
            </div>
          </div>

          {/* Batch bar */}
          {selectedIds.length > 0 && (
            <div className="batch-bar">
              <span className="batch-count">
                <CheckSquare size={15} />
                已选择 {selectedIds.length} 条
              </span>
              <button
                className="btn btn-secondary btn-sm"
                disabled={busy}
                onClick={runBatchTranscription}
              >
                批量转写
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={busy}
                onClick={runBatchSummary}
              >
                批量摘要
              </button>
              <div className="batch-spacer" />
              <button
                className="btn btn-icon danger"
                disabled={busy}
                onClick={deleteSelectedRecordings}
                title="批量删除"
              >
                <Trash2 size={15} />
              </button>
            </div>
          )}

          {/* Recording List */}
          <div className="recording-list">
            <div className="recording-list-header">
              <span
                className={`custom-check${allVisibleSelected ? " checked" : ""}`}
                onClick={toggleVisibleRecordings}
              />
              <span>录音文件</span>
              <span className="sort-hint">
                {sortKey === "created_desc"
                  ? "最新优先"
                  : sortKey === "created_asc"
                  ? "最旧优先"
                  : sortKey === "duration_desc"
                  ? "时长最长"
                  : "文件最大"}
                {" · "}共 {filteredRecordings.length} 条
              </span>
            </div>

            {filteredRecordings.map((rec) => (
              <div
                key={rec.id}
                className={`recording-item${selected?.recording.id === rec.id ? " active" : ""}`}
                onClick={() => selectRecording(rec.id)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === "Enter") selectRecording(rec.id);
                }}
              >
                <span
                  className={`custom-check${selectedIds.includes(rec.id) ? " checked" : ""}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleRecording(rec.id);
                  }}
                />
                <div className="item-icon">
                  <FileAudio size={16} />
                </div>
                <div className="item-body">
                  <div className="item-title-row">
                    <span className="item-name">{rec.filename}</span>
                    <StatusBadge status={rec.status} />
                    <span className="item-source-tag">
                      {rec.source_type === "watch" ? "监控" : "上传"}
                    </span>
                  </div>
                  <div className="item-meta-row">
                    <span>{formatSize(rec.file_size_bytes)}</span>
                    <span className="meta-dot" />
                    <span>{formatDuration(rec.duration_seconds)}</span>
                    <span className="meta-dot" />
                    <span>{formatDate(rec.source_mtime)}</span>
                  </div>
                  {searchMatchPreviews[rec.id]?.length > 0 &&
                    appliedFilters.query.trim() && (
                      <div className="match-snippets">
                        {searchMatchPreviews[rec.id].map((snippet, i) => (
                          <div key={i} className="match-snippet" title={snippet}>
                            {snippet}
                          </div>
                        ))}
                      </div>
                    )}
                </div>
                <div className="item-actions">
                  <button
                    className="btn btn-icon danger"
                    disabled={busy}
                    onClick={(e) => handleDelete(rec.id, e)}
                    title="删除"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Right Panel: Detail */}
        <RecordingDetailPanel
          selected={selected}
          detailTab={detailTab}
          setDetailTab={setDetailTab}
          summaryMode={summaryMode}
          setSummaryMode={setSummaryMode}
          summaryTemplates={summaryTemplates}
          activeTask={activeTask}
          busy={busy}
          allTags={allTags}
          runTranscription={runTranscription}
          runSummary={runSummary}
          cancelActiveTask={cancelActiveTask}
          updateTranscriptSegment={updateTranscriptSegment}
          updateRecordingTags={updateRecordingTags}
          downloadTranscript={downloadTranscript}
          downloadSummary={downloadSummary}
          deleteSummary={deleteSummary}
        />
      </div>
    </>
  );
}
