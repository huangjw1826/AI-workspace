import React from "react";
import { BrowserRouter, Routes, Route, useNavigate, useParams, useLocation } from "react-router-dom";
import { ConfirmDialog, type ConfirmDialogState } from "./components/feedback/ConfirmDialog";
import { ToastStack } from "./components/feedback/ToastStack";
import { NavBar } from "./components/layout/NavBar";
import { listSummaryTemplates, getRecording } from "./lib/api";
import type { RecordingDetail, SummaryTemplate, Task } from "./lib/types";
import type { DetailTab, View } from "./lib/viewTypes";
import { useAppStore } from "./stores/appStore";
import { useSSE } from "./hooks/useSSE";
import { useLibraryFilters } from "./hooks/useLibraryFilters";
import { useSettingsActions } from "./hooks/useSettingsActions";
import { useRecordingActions } from "./hooks/useRecordingActions";
import { HealthPage } from "./pages/HealthPage";
import { LibraryPage } from "./pages/LibraryPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WatchPage } from "./pages/WatchPage";
import { PageTransition } from "./components/layout/PageTransition";
import { TopBar } from "./components/layout/TopBar";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
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
  const error = useAppStore((s) => s.error);
  const setError = useAppStore((s) => s.setError);
  const toasts = useAppStore((s) => s.toasts);
  const showToast = useAppStore((s) => s.showToast);

  // ---- local UI state ----
  const [detailTab, setDetailTab] = React.useState<DetailTab>("transcript");
  const [selected, setSelected] = React.useState<RecordingDetail | null>(null);
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [activeTask, setActiveTask] = React.useState<Task | null>(null);
  const [summaryMode, setSummaryMode] = React.useState("structured_summary");
  const [summaryTemplates, setSummaryTemplates] = React.useState<SummaryTemplate[]>([]);
  const [confirmDialog, setConfirmDialog] = React.useState<ConfirmDialogState | null>(null);

  // ---- hooks: filters, settings, recording actions ----
  const filters = useLibraryFilters();
  const settings = useSettingsActions();
  const actions = useRecordingActions({
    appliedFilters: filters.appliedFilters,
    selected,
    setSelected,
    setActiveTask,
    setRecordings: filters.setRecordings,
    setSearchMatchPreviews: filters.setSearchMatchPreviews,
    setSelectedIds,
    setDetailTab,
    setSummaryMode,
    setConfirmDialog,
  });

  // ---- SSE (task events + toasts) ----
  const refreshRef = React.useRef<() => Promise<void>>(() => Promise.resolve());
  useSSE(React.useCallback((_recordingId: string, _filename: string) => {
    refreshRef.current();
  }, []));

  // Combined refresh: recordings + settings
  const fullRefresh = React.useCallback(async (selectedId?: string | null) => {
    await Promise.all([
      filters.reloadList(filters.appliedFilters.query, filters.appliedFilters.tag),
      settings.loadSettings(),
    ]);
    const idToRefresh = selectedId === undefined ? selected?.recording.id : selectedId;
    if (idToRefresh) {
      try { setSelected(await getRecording(idToRefresh)); } catch { setSelected(null); }
    }
  }, [filters.appliedFilters.query, filters.appliedFilters.tag, selected?.recording.id]);

  refreshRef.current = fullRefresh;

  // ---- effects ----
  React.useEffect(() => {
    fullRefresh(null);
    listSummaryTemplates().then((r) => {
      setSummaryTemplates(r);
      setSummaryMode((c) => c || r[0]?.id || "structured_summary");
    }).catch(() => {});
  }, []);

  React.useEffect(() => {
    const visibleIds = new Set(filters.recordings.map((r) => r.id));
    setSelectedIds((ids) => ids.filter((id) => visibleIds.has(id)));
  }, [filters.recordings]);

  // Task polling
  React.useEffect(() => {
    if (!activeTask || ["completed", "error", "cancelled"].includes(activeTask.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const detail = await getRecording(activeTask.recording_id);
        setSelected(detail);
        setActiveTask(detail.tasks.find((t) => t.id === activeTask.id) ?? activeTask);
        await filters.reloadList(filters.appliedFilters.query, filters.appliedFilters.tag);
      } catch (err) { setError(err instanceof Error ? err.message : String(err)); }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [activeTask?.id, activeTask?.status, filters.appliedFilters.query, filters.appliedFilters.tag]);

  // ---- navigation ----
  const go = (v: View) => navigate(pathForView(v), { replace: true });

  // ---- render ----
  const completedCount = filters.recordings.filter((r) => r.status === "completed").length;
  const isLibrary = view === "library";
  const subtitle = isLibrary
    ? `${filters.recordings.length} 条录音 · ${completedCount} 条已摘要`
    : undefined;

  return (
    <main className="app-shell">
      <NavBar
        view={view} health={settings.health} busy={busy}
        onViewChange={go}
        onUpload={actions.handleUpload}
        onRefresh={() => fullRefresh().catch((err) => setError(err.message))}
        onResync={actions.handleResync}
      />

      <section className="workspace">
        <TopBar
          title={VIEW_TITLES[view]}
          subtitle={subtitle}
        />

        {error && <div className="error-banner">{error}</div>}

        <PageTransition pageKey={view}>
          {view === "library" && filters.recordings.length === 0 && (
            <div className="setup-banner">
              <h2>👋 欢迎使用 AI Recorder</h2>
              <p>当前数据目录：<code>{settings.storageSettings?.data_dir ?? "未设置"}</code></p>
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
              recordings={filters.recordings}
              filteredRecordings={filters.filteredRecordings}
              selected={selected}
              selectedIds={selectedIds} setSelectedIds={setSelectedIds}
              detailTab={detailTab} setDetailTab={setDetailTab}
              summaryMode={summaryMode} setSummaryMode={setSummaryMode}
              summaryTemplates={summaryTemplates}
              activeTask={activeTask} busy={busy}
              draftFilters={filters.draftFilters} appliedFilters={filters.appliedFilters}
              sortKey={filters.sortKey}
              setDraftFilters={filters.setDraftFilters} setSortKey={filters.setSortKey}
              applyFilters={filters.applyFilters} resetFilters={filters.resetFilters}
              clearAppliedQuery={filters.clearAppliedQuery} clearAppliedTag={filters.clearAppliedTag}
              clearAppliedStatus={filters.clearAppliedStatus} clearAppliedSource={filters.clearAppliedSource}
              searchMatchPreviews={filters.searchMatchPreviews}
              allTags={filters.allTags}
              selectRecording={actions.selectRecording} handleDelete={actions.handleDelete}
              runTranscription={actions.runTranscription} runSummary={actions.runSummary}
              runBatchTranscription={() => actions.runBatchTranscription(selectedIds)}
              runBatchSummary={() => actions.runBatchSummary(selectedIds, summaryMode)}
              deleteSelectedRecordings={() => actions.handleBatchDelete(selectedIds)}
              cancelActiveTask={actions.handleCancelTask}
              updateTranscriptSegment={actions.handleUpdateTranscriptSegment}
              updateRecordingTags={actions.handleUpdateRecordingTags}
              downloadTranscript={actions.downloadTranscript} downloadSummary={actions.downloadSummary}
              deleteSummary={actions.handleDeleteSummary}
            />
          )}

          {view === "watch" && (
            <WatchPage
              watchDraft={settings.watchDraft} setWatchDraft={settings.setWatchDraft}
              watchSettings={settings.watchSettings} watchEvents={settings.watchEvents}
              settingsBusy={settings.settingsBusy}
              saveWatchSettings={settings.saveWatchSettings} runWatchScan={settings.runWatchScan}
            />
          )}

          {view === "settings" && (
            <SettingsPage
              storageDraft={settings.storageDraft} setStorageDraft={settings.setStorageDraft}
              storageSettings={settings.storageSettings}
              llmDraft={settings.llmDraft} setLlmDraft={settings.setLlmDraft}
              llmSettings={settings.llmSettings} llmTest={settings.llmTest}
              asrDraft={settings.asrDraft} setAsrDraft={settings.setAsrDraft}
              asrSettings={settings.asrSettings}
              settingsBusy={settings.settingsBusy}
              saveStorageSettings={() => settings.saveStorageSettings(setConfirmDialog)}
              saveLlmSettings={settings.saveLlmSettings}
              saveAsrSettings={settings.saveAsrSettings}
              checkLlmConnectivity={settings.checkLlmConnectivity}
              applyProviderDefaults={settings.applyProviderDefaults}
            />
          )}

          {view === "health" && <HealthPage health={settings.health} />}
        </PageTransition>
      </section>

      <ToastStack toasts={toasts} />
      <ConfirmDialog state={confirmDialog} busy={busy} onCancel={() => setConfirmDialog(null)} />
    </main>
  );
}
