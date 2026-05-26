import React from "react";
import { CheckSquare, Clock3, Database, FileAudio, Filter, Search, Sparkles, Tag, Trash2, TrendingUp } from "lucide-react";
import { MetricCard } from "../components/recording/MetricCard";
import { RecordingDetailPanel } from "../components/recording/RecordingDetailPanel";
import { StatusBadge } from "../components/ui/StatusBadge";
import { clampPercent, formatDate, formatDuration, formatSize, statusLabel, toggleValue } from "../lib/format";
import type { DetailTab, LibraryFilters, SortKey } from "../lib/viewTypes";
import type { ExportFormat, Recording, RecordingDetail, SummaryTemplate, Task } from "../lib/types";

export const STATUS_OPTIONS = [
  { value: "uploaded", label: "待转写" },
  { value: "queued", label: "排队中" },
  { value: "normalizing", label: "处理中" },
  { value: "transcribing", label: "转写中" },
  { value: "transcribed", label: "已转写" },
  { value: "completed", label: "已摘要" },
  { value: "cancelled", label: "已取消" },
  { value: "error", label: "错误" },
];

export const SOURCE_OPTIONS = [
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
  const stats = {
    total: recordings.length,
    pending: recordings.filter((recording) => recording.status === "uploaded").length,
    transcribed: recordings.filter((recording) => ["transcribed", "completed"].includes(recording.status)).length,
    completed: recordings.filter((recording) => recording.status === "completed").length,
    errors: recordings.filter((recording) => recording.status === "error").length,
    totalSize: recordings.reduce((sum, recording) => sum + (recording.file_size_bytes ?? 0), 0),
    totalDuration: recordings.reduce((sum, recording) => sum + (recording.duration_seconds ?? 0), 0),
  };
  const completionRate = stats.total > 0 ? clampPercent((stats.completed / stats.total) * 100) : 0;
  const draftFilterCount =
    (draftFilters.query.trim() ? 1 : 0) + (draftFilters.tag.trim() ? 1 : 0) + draftFilters.statuses.length + draftFilters.sources.length;
  const visibleIds = filteredRecordings.map((recording) => recording.id);
  const allVisibleSelected = visibleIds.length > 0 && visibleIds.every((id) => selectedIds.includes(id));

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
      <section className="metrics-panel" aria-label="录音统计">
        <div className="metrics">
          <MetricCard
            label="全部录音"
            value={String(stats.total)}
            hint={`总大小 ${formatSize(stats.totalSize)}`}
            icon={<Database size={17} />}
          />
          <MetricCard
            label="待处理"
            value={String(stats.pending)}
            hint={`已转写 ${stats.transcribed} 条`}
            icon={<Clock3 size={17} />}
          />
          <MetricCard
            label="AI 完成率"
            value={`${completionRate}%`}
            hint={`已摘要 ${stats.completed} 条`}
            icon={<Sparkles size={17} />}
            progress={completionRate}
          />
          <MetricCard
            label="累计时长"
            value={formatDuration(stats.totalDuration)}
            hint={stats.errors > 0 ? `${stats.errors} 条错误` : "运行正常"}
            icon={<TrendingUp size={17} />}
          />
        </div>
      </section>

      <div className="library-layout">
        <section className="main-panel">
          <div className="toolbar">
            <div className="toolbar-row">
              <label className="searchbox">
                <Search size={16} />
                <input
                  value={draftFilters.query}
                  placeholder="搜索文件名"
                  onChange={(event) => setDraftFilters((draft) => ({ ...draft, query: event.target.value }))}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") applyFilters();
                  }}
                />
              </label>
              <label className="searchbox">
                <Tag size={16} />
                <input
                  value={draftFilters.tag}
                  placeholder="标签筛选"
                  onChange={(event) => setDraftFilters((draft) => ({ ...draft, tag: event.target.value }))}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") applyFilters();
                  }}
                />
              </label>
              <select value={sortKey} onChange={(event) => setSortKey(event.target.value as SortKey)}>
                <option value="created_desc">最新优先</option>
                <option value="created_asc">最旧优先</option>
                <option value="duration_desc">时长最长</option>
                <option value="size_desc">文件最大</option>
              </select>
              <button className="primary" onClick={applyFilters}>
                <Filter size={15} /> 查询{draftFilterCount > 0 ? ` ${draftFilterCount}` : ""}
              </button>
              <button className="secondary" onClick={resetFilters}>重置</button>
            </div>
            <div className="filter-groups">
              <fieldset>
                <legend>状态</legend>
                <div className="check-pills">
                  {STATUS_OPTIONS.map((option) => (
                    <label key={option.value} className="check-pill">
                      <input
                        type="checkbox"
                        checked={draftFilters.statuses.includes(option.value)}
                        onChange={() =>
                          setDraftFilters((draft) => ({ ...draft, statuses: toggleValue(draft.statuses, option.value) }))
                        }
                      />
                      <span>{option.label}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <fieldset>
                <legend>来源</legend>
                <div className="check-pills">
                  {SOURCE_OPTIONS.map((option) => (
                    <label key={option.value} className="check-pill">
                      <input
                        type="checkbox"
                        checked={draftFilters.sources.includes(option.value)}
                        onChange={() =>
                          setDraftFilters((draft) => ({ ...draft, sources: toggleValue(draft.sources, option.value) }))
                        }
                      />
                      <span>{option.label}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            </div>
          </div>

          <div className="result-strip">
            <span>显示 {filteredRecordings.length} / {recordings.length} 条录音</span>
            <div className="active-filters">
              {appliedFilters.query.trim() && <button onClick={clearAppliedQuery}>关键词：{appliedFilters.query} x</button>}
              {appliedFilters.tag.trim() && <button onClick={clearAppliedTag}>标签：{appliedFilters.tag} x</button>}
              {appliedFilters.statuses.map((status) => (
                <button key={status} onClick={() => clearAppliedStatus(status)}>状态：{statusLabel(status)} x</button>
              ))}
              {appliedFilters.sources.map((source) => (
                <button key={source} onClick={() => clearAppliedSource(source)}>
                  来源：{source === "watch" ? "目录监控" : "上传"} x
                </button>
              ))}
            </div>
          </div>

          {selectedIds.length > 0 && (
            <div className="batch-bar">
              <span><CheckSquare size={15} /> 已选择 {selectedIds.length} 条</span>
              <button className="secondary compact" disabled={busy} onClick={runBatchTranscription}>批量转写</button>
              <button className="secondary compact" disabled={busy} onClick={runBatchSummary}>批量摘要</button>
              <button className="icon-danger" disabled={busy} onClick={deleteSelectedRecordings}><Trash2 size={14} /></button>
            </div>
          )}

          <div className="recording-table">
            <div className="table-head">
              <span>
                <input
                  type="checkbox"
                  checked={allVisibleSelected}
                  onChange={toggleVisibleRecordings}
                  aria-label="选择当前列表"
                />
              </span>
              <span>文件</span>
              <span>状态</span>
              <span>大小</span>
              <span>时长</span>
              <span>来源</span>
              <span>创建时间</span>
              <span />
            </div>
            {filteredRecordings.map((recording) => (
              <div
                key={recording.id}
                className={selected?.recording.id === recording.id ? "table-row active" : "table-row"}
                onClick={() => selectRecording(recording.id)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.key === "Enter") selectRecording(recording.id);
                }}
              >
                <span onClick={(event) => event.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(recording.id)}
                    onChange={() => toggleRecording(recording.id)}
                    aria-label={`选择 ${recording.filename}`}
                  />
                </span>
                <span className="file-cell">
                  <FileAudio size={16} /> <span>{recording.filename}</span>
                  {searchMatchPreviews[recording.id]?.length > 0 && appliedFilters.query.trim() && (
                    <div className="match-snippets">
                      {searchMatchPreviews[recording.id].map((snippet, i) => (
                        <div key={i} className="match-snippet" title={snippet}>{snippet}</div>
                      ))}
                    </div>
                  )}
                </span>
                <span><StatusBadge status={recording.status} /></span>
                <span>{formatSize(recording.file_size_bytes)}</span>
                <span>{formatDuration(recording.duration_seconds)}</span>
                <span>{recording.source_type === "watch" ? "监控" : "上传"}</span>
                <span>{formatDate(recording.created_at)}</span>
                <span>
                  <button className="icon-danger" disabled={busy} onClick={(event) => handleDelete(recording.id, event)}>
                    <Trash2 size={14} />
                  </button>
                </span>
              </div>
            ))}
          </div>
        </section>

        <RecordingDetailPanel
          selected={selected}
          detailTab={detailTab}
          setDetailTab={setDetailTab}
          summaryMode={summaryMode}
          setSummaryMode={setSummaryMode}
          summaryTemplates={summaryTemplates}
          activeTask={activeTask}
          busy={busy}
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
