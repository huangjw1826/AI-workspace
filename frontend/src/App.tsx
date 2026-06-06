import React from "react";
import { BrowserRouter, Routes, Route, useNavigate, useParams, useLocation } from "react-router-dom";
import { ConfirmDialog, type ConfirmDialogState } from "./components/feedback/ConfirmDialog";
import { ToastStack } from "./components/feedback/ToastStack";
import { NavBar } from "./components/layout/NavBar";
import {
  cancelTask, deleteRecording, deleteRecordingsBatch, deleteSummary,
  downloadFile, getAsrSettings, getHealth, getLlmSettings, getRecording,
  getStorageSettings, getWatchSettings, listRecordings, listSummaryTemplates,
  listWatchEvents, migrateStorage, previewStorageMigration, resyncRecordings,
  scanWatchDirectory,
  startSummary, startSummaryBatch, startTranscription, startTranscriptionBatch,
  testLlmConnectivity, updateAsrSettings, updateLlmSettings, updateRecordingTags,
  updateTranscriptSegment, updateStorageSettings, updateWatchSettings, uploadRecording,
} from "./lib/api";
import type {
  AsrSettings, AsrSettingsUpdate, ExportFormat, HealthStatus, LlmConnectivityResult, LlmSettings,
  LlmSettingsUpdate, Recording, RecordingDetail, StorageMigrationPreview,
  StorageSettings, SummaryTemplate, Task, WatchEvent, WatchSettings,
} from "./lib/types";
import type { DetailTab, LibraryFilters, SortKey, View } from "./lib/viewTypes";
import { useAppStore } from "./stores/appStore";
import { useSSE } from "./hooks/useSSE";
import { HealthPage } from "./pages/HealthPage";
import { LibraryPage } from "./pages/LibraryPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WatchPage } from "./pages/WatchPage";
import { PageTransition } from "./components/layout/PageTransition";
import { TopBar } from "./components/layout/TopBar";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const EMPTY_FILTERS: LibraryFilters = { query: "", statuses: [], sources: [], tag: "" };

const VIEW_TITLES: Record<View, string> = {
  library: "录音库", watch: "目录监控", settings: "设置", health: "系统状态",
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function viewFromPath(pathname: string): View {
  if (pathname === "/watch") return "watch";
  if (pathname === "/settings") return "settings";
  if (pathname === "/health") return "health";
  return "library";
}

function pathForView(view: View): string {
  return view === "library" ? "/" : `/${view}`;
}

// ---------------------------------------------------------------------------
// App
// ---------------------------------------------------------------------------
export default function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const view = viewFromPath(location.pathname);

  // ---- global UI state (Zustand) ----
  const busy = useAppStore((s) => s.busy);
  const setBusy = useAppStore((s) => s.setBusy);
  const error = useAppStore((s) => s.error);
  const setError = useAppStore((s) => s.setError);
  const toasts = useAppStore((s) => s.toasts);
  const showToast = useAppStore((s) => s.showToast);

  // ---- SSE (task events + toasts) ----
  const refreshRef = React.useRef<() => Promise<void>>(() => Promise.resolve());
  useSSE(React.useCallback((_recordingId: string, _filename: string) => {
    refreshRef.current();
  }, []));

  // ---- local data state ----
  const [detailTab, setDetailTab] = React.useState<DetailTab>("transcript");
  const [health, setHealth] = React.useState<HealthStatus | null>(null);
  const [llmSettings, setLlmSettings] = React.useState<LlmSettings | null>(null);
  const [watchSettings, setWatchSettings] = React.useState<WatchSettings | null>(null);
  const [storageSettings, setStorageSettings] = React.useState<StorageSettings | null>(null);
  const [watchEvents, setWatchEvents] = React.useState<WatchEvent[]>([]);
  const [summaryTemplates, setSummaryTemplates] = React.useState<SummaryTemplate[]>([]);
  const [recordings, setRecordings] = React.useState<Recording[]>([]);
  const [searchMatchPreviews, setSearchMatchPreviews] = React.useState<Record<string, string[]>>({});
  const [selected, setSelected] = React.useState<RecordingDetail | null>(null);
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [activeTask, setActiveTask] = React.useState<Task | null>(null);
  const [summaryMode, setSummaryMode] = React.useState("structured_summary");
  const [draftFilters, setDraftFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [sortKey, setSortKey] = React.useState<SortKey>("created_desc");
  const [settingsBusy, setSettingsBusy] = React.useState(false);
  const [llmTest, setLlmTest] = React.useState<LlmConnectivityResult | null>(null);
  const [confirmDialog, setConfirmDialog] = React.useState<ConfirmDialogState | null>(null);
  const [asrSettings, setAsrSettings] = React.useState<AsrSettings | null>(null);
  const [asrDraft, setAsrDraft] = React.useState<AsrSettingsUpdate>({
    enable_diarization: false, max_concurrency: 1,
  });

  // ---- settings drafts ----
  const [llmDraft, setLlmDraft] = React.useState<LlmSettingsUpdate>({
    provider: "mimo", api_key: "", base_url: "", model: "",
    mimo_thinking: "disabled", max_completion_tokens: 2048, temperature: null, top_p: null,
  });
  const [watchDraft, setWatchDraft] = React.useState<WatchSettings>({
    enabled: false, watch_dir: "", recursive: true, interval_seconds: 10, stable_count: 2, exists: false,
  });
  const [storageDraft, setStorageDraft] = React.useState({ data_dir: "", transcript_dir: "", summary_dir: "" });

  // ---- action wrapper: eliminates try/catch/setBusy/setError boilerplate ----
  const run = React.useCallback(
    async <T,>(fn: () => Promise<T>, okMsg?: string): Promise<T | undefined> => {
      setBusy(true);
      setError("");
      try {
        const result = await fn();
        if (okMsg) showToast(okMsg, "success");
        return result;
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        setError(msg);
        return undefined;
      } finally {
        setBusy(false);
      }
    },
    [setBusy, setError, showToast],
  );

  // ---- refresh: parallel load of all server state ----
  const refresh = React.useCallback(
    async (selectedId?: string | null) => {
      const idToRefresh = selectedId === undefined ? selected?.recording.id : selectedId;

      await Promise.all([
        getHealth().then(setHealth).catch((e) => console.warn("Health check failed:", e)),
        listRecordings(appliedFilters.query, appliedFilters.tag)
          .then((r) => { setRecordings(r.recordings); setSearchMatchPreviews(r.match_previews); })
          .catch((e) => console.warn("List recordings failed:", e)),
        getLlmSettings()
          .then((r) => { setLlmSettings(r); setLlmDraft((d) => ({ ...d, provider: r.provider, base_url: r.base_url, model: r.model, mimo_thinking: r.mimo_thinking, max_completion_tokens: r.max_completion_tokens, temperature: r.temperature, top_p: r.top_p })); })
          .catch((e) => console.warn("Get LLM settings failed:", e)),
        getWatchSettings().then((r) => { setWatchSettings(r); setWatchDraft(r); }).catch((e) => console.warn("Get watch settings failed:", e)),
        listWatchEvents().then(setWatchEvents).catch((e) => console.warn("List watch events failed:", e)),
        listSummaryTemplates().then((r) => { setSummaryTemplates(r); setSummaryMode((c) => c || r[0]?.id || "structured_summary"); }).catch((e) => console.warn("List summary templates failed:", e)),
        getStorageSettings().then((r) => { setStorageSettings(r); setStorageDraft({ data_dir: r.data_dir, transcript_dir: r.transcript_dir, summary_dir: r.summary_dir }); }).catch((e) => console.warn("Get storage settings failed:", e)),
        getAsrSettings().then((r) => { setAsrSettings(r); setAsrDraft({ enable_diarization: r.enable_diarization, max_concurrency: r.max_concurrency }); }).catch((e) => console.warn("Get ASR settings failed:", e)),
      ]);

      if (idToRefresh) {
        try { setSelected(await getRecording(idToRefresh)); } catch { setSelected(null); }
      }
    },
    [appliedFilters.query, appliedFilters.tag, selected?.recording.id],
  );

  // Keep the ref wired to the latest refresh so SSE callback always calls the current version
  refreshRef.current = refresh;

  // ---- effects ----
  React.useEffect(() => { refresh(null); }, []);

  React.useEffect(() => {
    const visibleIds = new Set(recordings.map((r) => r.id));
    setSelectedIds((ids) => ids.filter((id) => visibleIds.has(id)));
  }, [recordings]);

  // Task polling — TODO: replace with SSE-driven refresh when task completes
  React.useEffect(() => {
    if (!activeTask || ["completed", "error", "cancelled"].includes(activeTask.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const detail = await getRecording(activeTask.recording_id);
        setSelected(detail);
        setActiveTask(detail.tasks.find((t) => t.id === activeTask.id) ?? activeTask);
        const result = await listRecordings(appliedFilters.query, appliedFilters.tag);
        setRecordings(result.recordings);
        setSearchMatchPreviews(result.match_previews);
      } catch (err) { setError(err instanceof Error ? err.message : String(err)); }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [activeTask?.id, activeTask?.status, appliedFilters.query, appliedFilters.tag]);

  // ---- derived ----
  const filteredRecordings = React.useMemo(() => {
    const q = appliedFilters.query.trim().toLowerCase();
    const tag = appliedFilters.tag.trim().toLowerCase();
    return recordings
      .filter((r) => {
        if (q && !r.filename.toLowerCase().includes(q)) return false;
        if (tag && !r.tags.split(",").some((t) => t.trim().toLowerCase() === tag)) return false;
        if (appliedFilters.statuses.length && !appliedFilters.statuses.includes(r.status)) return false;
        if (appliedFilters.sources.length && !appliedFilters.sources.includes(r.source_type)) return false;
        return true;
      })
      .sort((a, b) => {
        const aTime = (a.source_mtime ?? 0) * 1000 || new Date(a.created_at).getTime();
        const bTime = (b.source_mtime ?? 0) * 1000 || new Date(b.created_at).getTime();
        switch (sortKey) {
          case "created_asc": return aTime - bTime;
          case "duration_desc": return (b.duration_seconds ?? 0) - (a.duration_seconds ?? 0);
          case "size_desc": return (b.file_size_bytes ?? 0) - (a.file_size_bytes ?? 0);
          default: return bTime - aTime;
        }
      });
  }, [recordings, appliedFilters, sortKey]);

  // ---- navigation ----
  const go = (v: View) => navigate(pathForView(v), { replace: true });

  // ---- recording handlers ----
  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      const recording = await uploadRecording(file);
      setSelected(await getRecording(recording.id));
      await refresh(recording.id);
      showToast("录音已加入录音库", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("上传失败", "error");
    } finally {
      setBusy(false);
      event.target.value = "";
    }
  };

  const handleResync = () => run(async () => {
    const result = await resyncRecordings();
    await refresh();
    const detailMsg = [
      result.total > 0 ? `共 ${result.total} 条` : "",
      result.updated > 0 ? `已更新 ${result.updated} 条` : "",
      ((result as any).relocated ?? 0) > 0 ? `重定位 ${(result as any).relocated} 条` : "",
      result.missing > 0 ? `缺失 ${result.missing} 条` : "",
      result.errors > 0 ? `错误 ${result.errors} 条` : "",
    ].filter(Boolean).join("，");
    showToast(`文件信息同步完成：${detailMsg}`, result.errors > 0 ? "error" : "success");
  });

  const selectRecording = async (id: string) => {
    const detail = await getRecording(id);
    setSelected(detail);
    setDetailTab("transcript");
    setActiveTask(detail.tasks.find((t) => !["completed", "error", "cancelled"].includes(t.status)) ?? null);
  };

  const handleDelete = (recordingId: string, event: React.MouseEvent) => {
    event.stopPropagation();
    setConfirmDialog({
      title: "删除录音",
      message: "确定要删除这条录音吗？转写结果、摘要和应用生成文件也会一并删除。",
      confirmLabel: "删除",
      tone: "danger",
      onConfirm: () => run(async () => {
        await deleteRecording(recordingId);
        if (selected?.recording.id === recordingId) setSelected(null);
        await refresh(selected?.recording.id === recordingId ? null : undefined);
      }, "录音已删除"),
    });
  };

  const runTranscription = () => selected && run(async () => {
    const task = await startTranscription(selected.recording.id);
    setActiveTask(task);
    setSelected(await getRecording(selected.recording.id));
  }, "转写任务已启动");

  const runSummary = (mode = summaryMode) => selected && run(async () => {
    setSummaryMode(mode);
    const task = await startSummary(selected.recording.id, mode);
    setActiveTask(task);
    setSelected(await getRecording(selected.recording.id));
    setDetailTab("summary");
  }, "摘要任务已启动");

  const handleCancelTask = () => activeTask && run(async () => {
    const task = await cancelTask(activeTask.id);
    setActiveTask(task);
    setSelected(await getRecording(task.recording_id));
  }, "任务已取消");

  const handleUpdateTranscriptSegment = (segmentId: string, text: string) => selected && run(async () => {
    await updateTranscriptSegment(selected.recording.id, segmentId, text);
    setSelected(await getRecording(selected.recording.id));
  }, "转写片段已保存");

  const handleUpdateRecordingTags = (tags: string[]) => selected && run(async () => {
    await updateRecordingTags(selected.recording.id, tags);
    await refresh(selected.recording.id);
  }, "标签已保存");

  const runBatchTranscription = () => selectedIds.length && run(async () => {
    const tasks = await startTranscriptionBatch(selectedIds);
    setActiveTask(tasks[0] ?? null);
    await refresh(selected?.recording.id);
  }, `已创建 ${selectedIds.length} 个转写任务`);

  const runBatchSummary = () => selectedIds.length && run(async () => {
    const tasks = await startSummaryBatch(selectedIds, summaryMode);
    setActiveTask(tasks[0] ?? null);
    await refresh(selected?.recording.id);
  }, `已创建 ${selectedIds.length} 个摘要任务`);

  const handleBatchDelete = () => selectedIds.length && setConfirmDialog({
    title: "批量删除",
    message: `确定删除选中的 ${selectedIds.length} 条录音吗？`,
    confirmLabel: "删除",
    tone: "danger",
    onConfirm: () => run(async () => {
      const result = await deleteRecordingsBatch(selectedIds);
      if (selected && selectedIds.includes(selected.recording.id)) setSelected(null);
      setSelectedIds([]);
      await refresh(selected && selectedIds.includes(selected.recording.id) ? null : undefined);
      showToast(`已删除 ${result.deleted.length} 条录音`, "success");
    }),
  });

  // ---- export handlers ----
  const downloadTranscript = (format: ExportFormat) => {
    if (!selected) return;
    downloadFile(
      `/api/recordings/${selected.recording.id}/exports/transcript?format=${format}`,
      `${selected.recording.filename}-transcript.${format}`,
    ).catch((err) => { setError(err.message); showToast("导出失败", "error"); });
  };

  const downloadSummary = (summaryId: string, format: ExportFormat) => {
    downloadFile(`/api/summaries/${summaryId}/export?format=${format}`, `summary.${format}`)
      .catch((err) => { setError(err.message); showToast("导出失败", "error"); });
  };

  const handleDeleteSummary = (summaryId: string) => {
    if (!selected) return;
    setConfirmDialog({
      title: "删除摘要", message: "确定要删除这条摘要吗？",
      confirmLabel: "删除", tone: "danger",
      onConfirm: () => run(async () => {
        await deleteSummary(summaryId);
        setSelected(await getRecording(selected.recording.id));
      }, "摘要已删除"),
    });
  };

  // ---- settings handlers ----
  const saveLlmSettings = () => run(async () => {
    setSettingsBusy(true);
    try {
      const updated = await updateLlmSettings({ ...llmDraft, api_key: llmDraft.api_key ?? "" });
      setLlmSettings(updated);
      setLlmDraft((d) => ({ ...d, api_key: "" }));
      setHealth(await getHealth());
    } finally { setSettingsBusy(false); }
  }, "LLM 设置已保存");

  const saveWatchSettings = () => run(async () => {
    setSettingsBusy(true);
    try {
      const updated = await updateWatchSettings(watchDraft);
      setWatchSettings(updated); setWatchDraft(updated);
      setWatchEvents(await listWatchEvents());
    } finally { setSettingsBusy(false); }
  }, "监控设置已保存");

  const saveAsrSettings = () => run(async () => {
    setSettingsBusy(true);
    try {
      const updated = await updateAsrSettings(asrDraft);
      setAsrSettings(updated);
      setAsrDraft({ enable_diarization: updated.enable_diarization, max_concurrency: updated.max_concurrency });
    } finally { setSettingsBusy(false); }
  }, "ASR 设置已保存");

  const doSaveStorageSettings = async () => {
    const updated = await updateStorageSettings(storageDraft);
    setStorageSettings(updated);
    setStorageDraft({ data_dir: updated.data_dir, transcript_dir: updated.transcript_dir, summary_dir: updated.summary_dir });
  };

  const saveStorageSettings = async () => {
    const oldDir = storageSettings?.data_dir ?? "";
    const newDir = storageDraft.data_dir?.trim() ?? "";
    if (!newDir || newDir === oldDir) return run(doSaveStorageSettings, "保存位置已更新");

    setSettingsBusy(true);
    let preview: StorageMigrationPreview | null = null;
    try {
      preview = await previewStorageMigration(newDir);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("无法读取目录数据", "error");
      setSettingsBusy(false);
      return;
    }
    setSettingsBusy(false);

    const isMerge = preview.old.exists && preview.new.exists && (preview.new.recordings ?? 0) > 0;
    const verb = isMerge ? "合并" : "迁移";
    setConfirmDialog({
      title: isMerge ? "确认合并数据目录" : "确认迁移数据目录",
      message: `${verb}操作将把当前目录数据${isMerge ? "合并" : "复制"}到新目录。`,
      confirmLabel: isMerge ? "开始合并" : "开始迁移",
      tone: "danger",
      onConfirm: () => run(async () => {
        setSettingsBusy(true);
        try {
          const result = await migrateStorage(newDir);
          for (const line of result.results) showToast(line, "success");
          await doSaveStorageSettings();
          showToast(`数据${verb}完成，请重启应用`, "success");
        } catch (err) {
          showToast(`${verb}失败: ${err instanceof Error ? err.message : String(err)}`, "error");
        } finally { setSettingsBusy(false); }
      }),
    });
  };

  const runWatchScan = () => run(async () => {
    setSettingsBusy(true);
    try {
      await scanWatchDirectory();
      setWatchEvents(await listWatchEvents());
    } finally { setSettingsBusy(false); }
  }, "目录扫描已完成");

  const checkLlmConnectivity = () => run(async () => {
    setSettingsBusy(true);
    try {
      setLlmTest(await testLlmConnectivity());
      setHealth(await getHealth());
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setLlmTest({ ok: false, provider: llmDraft.provider ?? "unknown", base_url: llmDraft.base_url ?? "", model: llmDraft.model ?? "", message: msg });
    } finally { setSettingsBusy(false); }
  });

  const applyProviderDefaults = (provider: LlmSettingsUpdate["provider"]) => {
    const defaults = llmSettings?.providers[provider];
    setLlmDraft((d) => ({ ...d, provider, base_url: defaults?.base_url ?? "", model: defaults?.model ?? "", temperature: defaults?.temperature ?? null, top_p: defaults?.top_p ?? null }));
  };

  // ---- filter handlers (server-side search for query/tag) ----
  const reloadList = (query: string, tag: string) =>
    listRecordings(query, tag).then((r) => {
      setRecordings(r.recordings);
      setSearchMatchPreviews(r.match_previews);
    });

  const applyFilters = () => {
    const next = { query: draftFilters.query, statuses: [...draftFilters.statuses], sources: [...draftFilters.sources], tag: draftFilters.tag };
    setAppliedFilters(next);
    reloadList(next.query, next.tag);
  };

  const resetFilters = () => {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setSortKey("created_desc");
    reloadList("", "");
  };

  const clearAppliedQuery = () => {
    setDraftFilters((d) => ({ ...d, query: "" }));
    setAppliedFilters((f) => ({ ...f, query: "" }));
    reloadList("", appliedFilters.tag);
  };

  const clearAppliedTag = () => {
    setDraftFilters((d) => ({ ...d, tag: "" }));
    setAppliedFilters((f) => ({ ...f, tag: "" }));
  };

  const clearAppliedStatus = (status: string) => {
    setDraftFilters((d) => ({ ...d, statuses: d.statuses.filter((s) => s !== status) }));
    setAppliedFilters((f) => ({ ...f, statuses: f.statuses.filter((s) => s !== status) }));
  };

  const clearAppliedSource = (source: string) => {
    setDraftFilters((d) => ({ ...d, sources: d.sources.filter((s) => s !== source) }));
    setAppliedFilters((f) => ({ ...f, sources: f.sources.filter((s) => s !== source) }));
  };

  // ---- render ----
  const completedCount = recordings.filter((r) => r.status === "completed").length;
  const isLibrary = view === "library";
  const subtitle = isLibrary
    ? `${recordings.length} 条录音 · ${completedCount} 条已摘要`
    : undefined;

  return (
    <main className="app-shell">
      <NavBar
        view={view} health={health} busy={busy}
        onViewChange={go}
        onUpload={handleUpload}
        onRefresh={() => refresh().catch((err) => setError(err.message))}
        onResync={handleResync}
      />

      <section className="workspace">
        <TopBar
          title={VIEW_TITLES[view]}
          subtitle={subtitle}
        />

        {error && <div className="error-banner">{error}</div>}

        <PageTransition pageKey={view}>
          {view === "library" && recordings.length === 0 && (
            <div className="setup-banner">
              <h2>👋 欢迎使用 AI Recorder</h2>
              <p>当前数据目录：<code>{storageSettings?.data_dir ?? "未设置"}</code></p>
              <div className="setup-steps">
                <div className="setup-step">
                  <strong>新设备，想连接到已有数据？</strong>
                  <p>去设置页把"数据总目录"改成同步盘文件夹，然后按提示迁移即可。</p>
                </div>
                <div className="setup-step">
                  <strong>全新开始？</strong>
                  <p>上传录音或者设置目录监控即可自动发现音频文件。</p>
                </div>
              </div>
              <button className="btn btn-primary" onClick={() => go("settings")}>前往设置页面</button>
            </div>
          )}

          {view === "library" && (
            <LibraryPage
              recordings={recordings}
              filteredRecordings={filteredRecordings}
              selected={selected}
              selectedIds={selectedIds} setSelectedIds={setSelectedIds}
              detailTab={detailTab} setDetailTab={setDetailTab}
              summaryMode={summaryMode} setSummaryMode={setSummaryMode}
              summaryTemplates={summaryTemplates}
              activeTask={activeTask} busy={busy}
              draftFilters={draftFilters} appliedFilters={appliedFilters}
              sortKey={sortKey}
              setDraftFilters={setDraftFilters} setSortKey={setSortKey}
              applyFilters={applyFilters} resetFilters={resetFilters}
              clearAppliedQuery={clearAppliedQuery} clearAppliedTag={clearAppliedTag}
              clearAppliedStatus={clearAppliedStatus} clearAppliedSource={clearAppliedSource}
              searchMatchPreviews={searchMatchPreviews}
              selectRecording={selectRecording} handleDelete={handleDelete}
              runTranscription={runTranscription} runSummary={runSummary}
              runBatchTranscription={runBatchTranscription} runBatchSummary={runBatchSummary}
              deleteSelectedRecordings={handleBatchDelete}
              cancelActiveTask={handleCancelTask}
              updateTranscriptSegment={handleUpdateTranscriptSegment}
              updateRecordingTags={handleUpdateRecordingTags}
              downloadTranscript={downloadTranscript} downloadSummary={downloadSummary}
              deleteSummary={handleDeleteSummary}
            />
          )}

          {view === "watch" && (
            <WatchPage
              watchDraft={watchDraft} setWatchDraft={setWatchDraft}
              watchSettings={watchSettings} watchEvents={watchEvents}
              settingsBusy={settingsBusy}
              saveWatchSettings={saveWatchSettings} runWatchScan={runWatchScan}
            />
          )}

          {view === "settings" && (
            <SettingsPage
              storageDraft={storageDraft} setStorageDraft={setStorageDraft}
              storageSettings={storageSettings}
              llmDraft={llmDraft} setLlmDraft={setLlmDraft}
              llmSettings={llmSettings} llmTest={llmTest}
              asrDraft={asrDraft} setAsrDraft={setAsrDraft}
              asrSettings={asrSettings}
              settingsBusy={settingsBusy}
              saveStorageSettings={saveStorageSettings} saveLlmSettings={saveLlmSettings}
              saveAsrSettings={saveAsrSettings}
              checkLlmConnectivity={checkLlmConnectivity} applyProviderDefaults={applyProviderDefaults}
            />
          )}

          {view === "health" && <HealthPage health={health} />}
        </PageTransition>
      </section>

      <ToastStack toasts={toasts} />
      <ConfirmDialog state={confirmDialog} busy={busy} onCancel={() => setConfirmDialog(null)} />
    </main>
  );
}
