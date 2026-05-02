import React from "react";
import { ConfirmDialog, type ConfirmDialogState } from "./components/feedback/ConfirmDialog";
import { ToastStack, type ToastMessage } from "./components/feedback/ToastStack";
import { NavBar } from "./components/layout/NavBar";
import {
  apiUrl,
  deleteRecording,
  deleteSummary,
  getHealth,
  getLlmSettings,
  getRecording,
  getStorageSettings,
  getWatchSettings,
  listRecordings,
  listSummaryTemplates,
  listWatchEvents,
  scanWatchDirectory,
  startSummary,
  startTranscription,
  testLlmConnectivity,
  updateLlmSettings,
  updateStorageSettings,
  updateWatchSettings,
  uploadRecording,
} from "./lib/api";
import type {
  ExportFormat,
  HealthStatus,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  Recording,
  RecordingDetail,
  StorageSettings,
  SummaryTemplate,
  Task,
  WatchEvent,
  WatchSettings,
} from "./lib/types";
import type { DetailTab, LibraryFilters, SortKey, View } from "./lib/viewTypes";
import { HealthPage } from "./pages/HealthPage";
import { LibraryPage } from "./pages/LibraryPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WatchPage } from "./pages/WatchPage";

const EMPTY_FILTERS: LibraryFilters = {
  query: "",
  statuses: [],
  sources: [],
};

const VIEW_TITLES: Record<View, string> = {
  library: "录音库",
  watch: "目录监控",
  settings: "设置",
  health: "系统状态",
};

export default function App() {
  const [view, setView] = React.useState<View>("library");
  const [detailTab, setDetailTab] = React.useState<DetailTab>("transcript");
  const [health, setHealth] = React.useState<HealthStatus | null>(null);
  const [llmSettings, setLlmSettings] = React.useState<LlmSettings | null>(null);
  const [watchSettings, setWatchSettings] = React.useState<WatchSettings | null>(null);
  const [storageSettings, setStorageSettings] = React.useState<StorageSettings | null>(null);
  const [watchEvents, setWatchEvents] = React.useState<WatchEvent[]>([]);
  const [summaryTemplates, setSummaryTemplates] = React.useState<SummaryTemplate[]>([]);
  const [recordings, setRecordings] = React.useState<Recording[]>([]);
  const [selected, setSelected] = React.useState<RecordingDetail | null>(null);
  const [activeTask, setActiveTask] = React.useState<Task | null>(null);
  const [summaryMode, setSummaryMode] = React.useState("structured_summary");
  const [draftFilters, setDraftFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [sortKey, setSortKey] = React.useState<SortKey>("created_desc");
  const [busy, setBusy] = React.useState(false);
  const [settingsBusy, setSettingsBusy] = React.useState(false);
  const [error, setError] = React.useState("");
  const [llmTest, setLlmTest] = React.useState<LlmConnectivityResult | null>(null);
  const [confirmDialog, setConfirmDialog] = React.useState<ConfirmDialogState | null>(null);
  const [toasts, setToasts] = React.useState<ToastMessage[]>([]);

  const [llmDraft, setLlmDraft] = React.useState<LlmSettingsUpdate>({
    provider: "mimo",
    api_key: "",
    base_url: "",
    model: "",
    mimo_thinking: "disabled",
    max_completion_tokens: 2048,
    temperature: null,
    top_p: null,
  });
  const [watchDraft, setWatchDraft] = React.useState<WatchSettings>({
    enabled: false,
    watch_dir: "",
    recursive: true,
    interval_seconds: 10,
    exists: false,
  });
  const [storageDraft, setStorageDraft] = React.useState({ transcript_dir: "", summary_dir: "" });

  function showToast(message: string, tone: ToastMessage["tone"] = "info") {
    const id = Date.now() + Math.random();
    setToasts((items) => [...items, { id, message, tone }]);
    window.setTimeout(() => {
      setToasts((items) => items.filter((item) => item.id !== id));
    }, 2600);
  }

  const refresh = React.useCallback(
    async (selectedId?: string | null) => {
      const idToRefresh = selectedId === undefined ? selected?.recording.id : selectedId;
      const [healthResult, recordingsResult, llmResult, watchResult, eventsResult, templatesResult, storageResult] =
        await Promise.all([
          getHealth(),
          listRecordings(),
          getLlmSettings(),
          getWatchSettings(),
          listWatchEvents(),
          listSummaryTemplates(),
          getStorageSettings(),
        ]);

      setHealth(healthResult);
      setRecordings(recordingsResult);
      setLlmSettings(llmResult);
      setWatchSettings(watchResult);
      setWatchDraft(watchResult);
      setWatchEvents(eventsResult);
      setStorageSettings(storageResult);
      setStorageDraft({
        transcript_dir: storageResult.transcript_dir,
        summary_dir: storageResult.summary_dir,
      });
      setSummaryTemplates(templatesResult);
      setSummaryMode((current) => current || templatesResult[0]?.id || "structured_summary");
      setLlmDraft((draft) => ({
        ...draft,
        provider: llmResult.provider,
        base_url: llmResult.base_url,
        model: llmResult.model,
        mimo_thinking: llmResult.mimo_thinking,
        max_completion_tokens: llmResult.max_completion_tokens,
        temperature: llmResult.temperature,
        top_p: llmResult.top_p,
      }));

      if (idToRefresh) {
        try {
          setSelected(await getRecording(idToRefresh));
        } catch {
          setSelected(null);
        }
      }
    },
    [selected?.recording.id]
  );

  React.useEffect(() => {
    refresh(null).catch((err) => setError(err.message));
  }, []);

  React.useEffect(() => {
    if (!activeTask || ["completed", "error"].includes(activeTask.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const detail = await getRecording(activeTask.recording_id);
        setSelected(detail);
        setActiveTask(detail.tasks.find((task) => task.id === activeTask.id) ?? activeTask);
        setRecordings(await listRecordings());
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    }, 1500);
    return () => window.clearInterval(timer);
  }, [activeTask?.id, activeTask?.status]);

  const filteredRecordings = React.useMemo(() => {
    const normalizedQuery = appliedFilters.query.trim().toLowerCase();
    const result = recordings.filter((recording) => {
      const matchesQuery = !normalizedQuery || recording.filename.toLowerCase().includes(normalizedQuery);
      const matchesStatus = appliedFilters.statuses.length === 0 || appliedFilters.statuses.includes(recording.status);
      const matchesSource = appliedFilters.sources.length === 0 || appliedFilters.sources.includes(recording.source_type);
      return matchesQuery && matchesStatus && matchesSource;
    });
    return result.sort((a, b) => {
      if (sortKey === "created_asc") return new Date(a.created_at).getTime() - new Date(b.created_at).getTime();
      if (sortKey === "duration_desc") return (b.duration_seconds ?? 0) - (a.duration_seconds ?? 0);
      if (sortKey === "size_desc") return (b.file_size_bytes ?? 0) - (a.file_size_bytes ?? 0);
      return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
    });
  }, [recordings, appliedFilters, sortKey]);

  async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
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
      showToast("上传失败，请查看错误信息", "error");
    } finally {
      setBusy(false);
      event.target.value = "";
    }
  }

  async function selectRecording(id: string) {
    setSelected(await getRecording(id));
    setDetailTab("transcript");
  }

  function handleDelete(recordingId: string, event: React.MouseEvent) {
    event.stopPropagation();
    setConfirmDialog({
      title: "删除录音",
      message: "确定要删除这条录音吗？转写结果、摘要和应用生成文件也会一并删除，原始监控目录文件会保留。",
      confirmLabel: "删除",
      tone: "danger",
      onConfirm: async () => {
        setConfirmDialog(null);
        setBusy(true);
        setError("");
        try {
          await deleteRecording(recordingId);
          if (selected?.recording.id === recordingId) setSelected(null);
          await refresh(selected?.recording.id === recordingId ? null : undefined);
          showToast("录音记录已删除", "success");
        } catch (err) {
          setError(err instanceof Error ? err.message : String(err));
          showToast("删除失败，请查看错误信息", "error");
        } finally {
          setBusy(false);
        }
      },
    });
  }

  async function runTranscription() {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      const task = await startTranscription(selected.recording.id);
      setActiveTask(task);
      setSelected(await getRecording(selected.recording.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function runSummary(mode = summaryMode) {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      setSummaryMode(mode);
      const task = await startSummary(selected.recording.id, mode);
      setActiveTask(task);
      setSelected(await getRecording(selected.recording.id));
      setDetailTab("summary");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function saveLlmSettings() {
    setSettingsBusy(true);
    setError("");
    setLlmTest(null);
    try {
      const updated = await updateLlmSettings({ ...llmDraft, api_key: llmDraft.api_key ?? "" });
      setLlmSettings(updated);
      setLlmDraft((draft) => ({ ...draft, api_key: "" }));
      setHealth(await getHealth());
      showToast("LLM 设置已保存", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSettingsBusy(false);
    }
  }

  async function saveWatchSettings() {
    setSettingsBusy(true);
    setError("");
    try {
      const updated = await updateWatchSettings(watchDraft);
      setWatchSettings(updated);
      setWatchDraft(updated);
      setWatchEvents(await listWatchEvents());
      showToast("目录监控设置已保存", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSettingsBusy(false);
    }
  }

  async function saveStorageSettings() {
    setSettingsBusy(true);
    setError("");
    try {
      const updated = await updateStorageSettings(storageDraft);
      setStorageSettings(updated);
      setStorageDraft({ transcript_dir: updated.transcript_dir, summary_dir: updated.summary_dir });
      showToast("保存位置已更新", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSettingsBusy(false);
    }
  }

  async function runWatchScan() {
    setSettingsBusy(true);
    setError("");
    try {
      await scanWatchDirectory();
      setWatchEvents(await listWatchEvents());
      setRecordings(await listRecordings());
      showToast("目录扫描已完成", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSettingsBusy(false);
    }
  }

  async function checkLlmConnectivity() {
    setSettingsBusy(true);
    setError("");
    try {
      setLlmTest(await testLlmConnectivity());
      setHealth(await getHealth());
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setLlmTest({
        ok: false,
        provider: llmDraft.provider ?? "unknown",
        base_url: llmDraft.base_url ?? "",
        model: llmDraft.model ?? "",
        message,
      });
    } finally {
      setSettingsBusy(false);
    }
  }

  function applyProviderDefaults(provider: LlmSettingsUpdate["provider"]) {
    const defaults = llmSettings?.providers[provider];
    setLlmDraft((draft) => ({
      ...draft,
      provider,
      base_url: defaults?.base_url ?? "",
      model: defaults?.model ?? "",
      temperature: defaults?.temperature ?? null,
      top_p: defaults?.top_p ?? null,
    }));
  }

  function downloadTranscript(format: ExportFormat) {
    if (!selected) return;
    window.location.href = apiUrl(`/api/recordings/${selected.recording.id}/exports/transcript?format=${format}`);
  }

  function downloadSummary(summaryId: string, format: ExportFormat) {
    window.location.href = apiUrl(`/api/summaries/${summaryId}/export?format=${format}`);
  }

  async function handleDeleteSummary(summaryId: string) {
    if (!selected) return;
    const recordingId = selected.recording.id;
    setConfirmDialog({
      title: "删除摘要",
      message: "确定要删除这条摘要吗？已保存的摘要文件也会尽量一并清理。",
      confirmLabel: "删除",
      tone: "danger",
      onConfirm: async () => {
        setConfirmDialog(null);
        setBusy(true);
        setError("");
        try {
          await deleteSummary(summaryId);
          setSelected(await getRecording(recordingId));
          setRecordings(await listRecordings());
          showToast("摘要已删除", "success");
        } catch (err) {
          setError(err instanceof Error ? err.message : String(err));
          showToast("摘要删除失败，请查看错误信息", "error");
        } finally {
          setBusy(false);
        }
      },
    });
  }

  function applyFilters() {
    setAppliedFilters({
      query: draftFilters.query,
      statuses: [...draftFilters.statuses],
      sources: [...draftFilters.sources],
    });
  }

  function resetFilters() {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setSortKey("created_desc");
  }

  function clearAppliedQuery() {
    setDraftFilters((draft) => ({ ...draft, query: "" }));
    setAppliedFilters((filters) => ({ ...filters, query: "" }));
  }

  function clearAppliedStatus(status: string) {
    setDraftFilters((draft) => ({ ...draft, statuses: draft.statuses.filter((item) => item !== status) }));
    setAppliedFilters((filters) => ({ ...filters, statuses: filters.statuses.filter((item) => item !== status) }));
  }

  function clearAppliedSource(source: string) {
    setDraftFilters((draft) => ({ ...draft, sources: draft.sources.filter((item) => item !== source) }));
    setAppliedFilters((filters) => ({ ...filters, sources: filters.sources.filter((item) => item !== source) }));
  }

  const completedCount = recordings.filter((recording) => recording.status === "completed").length;

  return (
    <main className="app-shell">
      <NavBar
        view={view}
        health={health}
        busy={busy}
        onViewChange={setView}
        onUpload={handleUpload}
        onRefresh={() => refresh().catch((err) => setError(err.message))}
      />

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>{VIEW_TITLES[view]}</h1>
            <p>{recordings.length} 条录音 · {completedCount} 条已摘要</p>
          </div>
        </header>

        {error && <div className="error">{error}</div>}

        {view === "library" && (
          <LibraryPage
            recordings={recordings}
            filteredRecordings={filteredRecordings}
            selected={selected}
            detailTab={detailTab}
            setDetailTab={setDetailTab}
            summaryMode={summaryMode}
            setSummaryMode={setSummaryMode}
            summaryTemplates={summaryTemplates}
            activeTask={activeTask}
            busy={busy}
            draftFilters={draftFilters}
            appliedFilters={appliedFilters}
            sortKey={sortKey}
            setDraftFilters={setDraftFilters}
            setSortKey={setSortKey}
            applyFilters={applyFilters}
            resetFilters={resetFilters}
            clearAppliedQuery={clearAppliedQuery}
            clearAppliedStatus={clearAppliedStatus}
            clearAppliedSource={clearAppliedSource}
            selectRecording={selectRecording}
            handleDelete={handleDelete}
            runTranscription={runTranscription}
            runSummary={runSummary}
            downloadTranscript={downloadTranscript}
            downloadSummary={downloadSummary}
            deleteSummary={handleDeleteSummary}
          />
        )}

        {view === "watch" && (
          <WatchPage
            watchDraft={watchDraft}
            setWatchDraft={setWatchDraft}
            watchSettings={watchSettings}
            watchEvents={watchEvents}
            settingsBusy={settingsBusy}
            saveWatchSettings={saveWatchSettings}
            runWatchScan={runWatchScan}
          />
        )}

        {view === "settings" && (
          <SettingsPage
            storageDraft={storageDraft}
            setStorageDraft={setStorageDraft}
            storageSettings={storageSettings}
            llmDraft={llmDraft}
            setLlmDraft={setLlmDraft}
            llmSettings={llmSettings}
            llmTest={llmTest}
            settingsBusy={settingsBusy}
            saveStorageSettings={saveStorageSettings}
            saveLlmSettings={saveLlmSettings}
            checkLlmConnectivity={checkLlmConnectivity}
            applyProviderDefaults={applyProviderDefaults}
          />
        )}

        {view === "health" && <HealthPage health={health} />}
      </section>

      <ToastStack toasts={toasts} />
      <ConfirmDialog state={confirmDialog} busy={busy} onCancel={() => setConfirmDialog(null)} />
    </main>
  );
}
