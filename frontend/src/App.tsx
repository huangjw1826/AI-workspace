import React from "react";
import { BrowserRouter, Routes, Route, useNavigate, useParams, useLocation } from "react-router-dom";
import { ConfirmDialog, type ConfirmDialogState } from "./components/feedback/ConfirmDialog";
import { ToastStack, type ToastMessage } from "./components/feedback/ToastStack";
import { NavBar } from "./components/layout/NavBar";
import {
  cancelTask,
  deleteRecording,
  deleteRecordingsBatch,
  deleteSummary,
  downloadFile,
  getHealth,
  getLlmSettings,
  getRecording,
  getStorageSettings,
  getWatchSettings,
  listRecordings,
  listSummaryTemplates,
  listWatchEvents,
  migrateStorage,
  previewStorageMigration,
  scanWatchDirectory,
  startSummary,
  startSummaryBatch,
  startTranscription,
  startTranscriptionBatch,
  testLlmConnectivity,
  updateLlmSettings,
  updateRecordingTags,
  updateStorageSettings,
  updateTranscriptSegment,
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
  StorageMigrationPreview,
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
  tag: "",
};

const VIEW_TITLES: Record<View, string> = {
  library: "录音库",
  watch: "目录监控",
  settings: "设置",
  health: "系统状态",
};

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const [view, setView] = React.useState<View>(() => {
    const path = location.pathname;
    if (path === "/watch") return "watch";
    if (path === "/settings") return "settings";
    if (path === "/health") return "health";
    return "library";
  });
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
  const [busy, setBusy] = React.useState(false);
  const [settingsBusy, setSettingsBusy] = React.useState(false);
  const [error, setError] = React.useState("");
  const [llmTest, setLlmTest] = React.useState<LlmConnectivityResult | null>(null);
  const [confirmDialog, setConfirmDialog] = React.useState<ConfirmDialogState | null>(null);
  const [toasts, setToasts] = React.useState<ToastMessage[]>([]);

  const setViewWithNavigate = React.useCallback(
    (newView: View) => {
      setView(newView);
      switch (newView) {
        case "watch":
          navigate("/watch", { replace: true });
          break;
        case "settings":
          navigate("/settings", { replace: true });
          break;
        case "health":
          navigate("/health", { replace: true });
          break;
        default:
          navigate("/", { replace: true });
      }
    },
    [navigate]
  );

  const reloadRecordings = React.useCallback(
    async (query: string, tag: string) => {
      const result = await listRecordings(query, tag);
      setRecordings(result.recordings);
      setSearchMatchPreviews(result.match_previews);
    },
    [],
  );

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
    stable_count: 2,
    exists: false,
  });
  const [storageDraft, setStorageDraft] = React.useState({ data_dir: "", transcript_dir: "", summary_dir: "" });

  function showToast(message: string, tone: ToastMessage["tone"] = "info") {
    const id = Date.now() + Math.random();
    setToasts((items) => [...items, { id, message, tone }]);
    window.setTimeout(() => {
      setToasts((items) => items.filter((item) => item.id !== id));
    }, 2600);
  }

  React.useEffect(() => {
    let sseClient: { connect: () => void; disconnect: () => void } | null = null;

    async function initSSE() {
      const { connectSSE, disconnectSSE, addSSEListener } = await import("./lib/sse");
      sseClient = { connect: connectSSE, disconnect: disconnectSSE };

      connectSSE();

      const unsubscribe = addSSEListener((event) => {
        switch (event.event_type) {
          case "task.started":
            showToast(`任务开始: ${event.message}`, "info");
            break;
          case "task.progress":
            if (event.progress > 0 && event.progress < 100) {
              // Skip progress toasts, just update UI
            }
            break;
          case "task.completed":
            showToast(`任务完成: ${event.message}`, "success");
            break;
          case "task.failed":
            showToast(`任务失败: ${event.message}`, "error");
            break;
        }
      });

      return () => {
        unsubscribe();
        disconnectSSE();
      };
    }

    const cleanupPromise = initSSE();

    return () => {
      cleanupPromise.then((cleanup) => cleanup?.());
      if (sseClient) {
        sseClient.disconnect();
      }
    };
  }, []);

  const refresh = React.useCallback(
    async (selectedId?: string | null) => {
      const idToRefresh = selectedId === undefined ? selected?.recording.id : selectedId;

      // Health: always independent — must not be blocked by other API failures
      const healthPromise = getHealth()
        .then((data) => {
          setHealth(data);
        })
        .catch((healthErr) => {
          console.warn("Health check failed:", healthErr);
        });

      // Recordings
      const recordingsPromise = listRecordings(appliedFilters.query, appliedFilters.tag)
        .then((searchResult) => {
          setRecordings(searchResult.recordings);
          setSearchMatchPreviews(searchResult.match_previews);
        })
        .catch((recErr) => {
          console.warn("List recordings failed:", recErr);
        });

      // LLM settings
      const llmPromise = getLlmSettings()
        .then((llmResult) => {
          setLlmSettings(llmResult);
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
        })
        .catch((llmErr) => {
          console.warn("Get LLM settings failed:", llmErr);
        });

      // Watch settings
      const watchPromise = getWatchSettings()
        .then((watchResult) => {
          setWatchSettings(watchResult);
          setWatchDraft(watchResult);
        })
        .catch((watchErr) => {
          console.warn("Get watch settings failed:", watchErr);
        });

      // Watch events
      const eventsPromise = listWatchEvents()
        .then((events) => {
          setWatchEvents(events);
        })
        .catch((eventsErr) => {
          console.warn("List watch events failed:", eventsErr);
        });

      // Summary templates
      const templatesPromise = listSummaryTemplates()
        .then((templatesResult) => {
          setSummaryTemplates(templatesResult);
          setSummaryMode((current) => current || templatesResult[0]?.id || "structured_summary");
        })
        .catch((tmplErr) => {
          console.warn("List summary templates failed:", tmplErr);
        });

      // Storage settings
      const storagePromise = getStorageSettings()
        .then((storageResult) => {
          setStorageSettings(storageResult);
          setStorageDraft({
            data_dir: storageResult.data_dir,
            transcript_dir: storageResult.transcript_dir,
            summary_dir: storageResult.summary_dir,
          });
        })
        .catch((storageErr) => {
          console.warn("Get storage settings failed:", storageErr);
        });

      // 并行等待所有独立请求完成
      await Promise.all([
        healthPromise,
        recordingsPromise,
        llmPromise,
        watchPromise,
        eventsPromise,
        templatesPromise,
        storagePromise,
      ]);

      if (idToRefresh) {
        try {
          setSelected(await getRecording(idToRefresh));
        } catch {
          setSelected(null);
        }
      }
    },
    [appliedFilters.query, appliedFilters.tag, selected?.recording.id]
  );

  React.useEffect(() => {
    refresh(null);
  }, []);

  React.useEffect(() => {
    const visibleIds = new Set(recordings.map((recording) => recording.id));
    setSelectedIds((ids) => ids.filter((id) => visibleIds.has(id)));
  }, [recordings]);

  React.useEffect(() => {
    if (!activeTask || ["completed", "error", "cancelled"].includes(activeTask.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const detail = await getRecording(activeTask.recording_id);
        setSelected(detail);
        setActiveTask(detail.tasks.find((task) => task.id === activeTask.id) ?? activeTask);
        const searchResult = await listRecordings(appliedFilters.query, appliedFilters.tag);
        setRecordings(searchResult.recordings);
        setSearchMatchPreviews(searchResult.match_previews);
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    }, 1500);
    return () => window.clearInterval(timer);
  }, [activeTask?.id, activeTask?.status, appliedFilters.query, appliedFilters.tag]);

  const filteredRecordings = React.useMemo(() => {
    const normalizedQuery = appliedFilters.query.trim().toLowerCase();
    const normalizedTag = appliedFilters.tag.trim().toLowerCase();
    const result = recordings.filter((recording) => {
      const matchesQuery = !normalizedQuery || recording.filename.toLowerCase().includes(normalizedQuery);
      const matchesTag = !normalizedTag || recording.tags.split(",").some((t) => t.trim().toLowerCase() === normalizedTag);
      const matchesStatus = appliedFilters.statuses.length === 0 || appliedFilters.statuses.includes(recording.status);
      const matchesSource = appliedFilters.sources.length === 0 || appliedFilters.sources.includes(recording.source_type);
      return matchesQuery && matchesTag && matchesStatus && matchesSource;
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
    const detail = await getRecording(id);
    setSelected(detail);
    setDetailTab("transcript");
    const newActiveTask = detail.tasks.find(
      (task) => !["completed", "error", "cancelled"].includes(task.status)
    ) ?? null;
    setActiveTask(newActiveTask);
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

  async function handleCancelTask() {
    if (!activeTask) return;
    setBusy(true);
    setError("");
    try {
      const task = await cancelTask(activeTask.id);
      setActiveTask(task);
      setSelected(await getRecording(task.recording_id));
      await reloadRecordings(appliedFilters.query, appliedFilters.tag);
      showToast("任务已取消", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("取消失败，请查看错误信息", "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateTranscriptSegment(segmentId: string, text: string) {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      await updateTranscriptSegment(selected.recording.id, segmentId, text);
      setSelected(await getRecording(selected.recording.id));
      await reloadRecordings(appliedFilters.query, appliedFilters.tag);
      showToast("转写片段已保存", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("转写保存失败，请查看错误信息", "error");
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateRecordingTags(tags: string[]) {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      await updateRecordingTags(selected.recording.id, tags);
      await refresh(selected.recording.id);
      showToast("标签已保存", "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("标签保存失败，请查看错误信息", "error");
    } finally {
      setBusy(false);
    }
  }

  async function runBatchTranscription() {
    if (selectedIds.length === 0) return;
    setBusy(true);
    setError("");
    try {
      const tasks = await startTranscriptionBatch(selectedIds);
      setActiveTask(tasks[0] ?? null);
      await refresh(selected?.recording.id);
      showToast(`已创建 ${tasks.length} 个转写任务`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("批量转写启动失败", "error");
    } finally {
      setBusy(false);
    }
  }

  async function runBatchSummary() {
    if (selectedIds.length === 0) return;
    setBusy(true);
    setError("");
    try {
      const tasks = await startSummaryBatch(selectedIds, summaryMode);
      setActiveTask(tasks[0] ?? null);
      await refresh(selected?.recording.id);
      showToast(`已创建 ${tasks.length} 个摘要任务`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      showToast("批量摘要启动失败", "error");
    } finally {
      setBusy(false);
    }
  }

  function handleBatchDelete() {
    if (selectedIds.length === 0) return;
    setConfirmDialog({
      title: "批量删除录音",
      message: `确定删除选中的 ${selectedIds.length} 条录音吗？转写、摘要和应用生成文件会一起清理。`,
      confirmLabel: "删除",
      tone: "danger",
      onConfirm: async () => {
        setConfirmDialog(null);
        setBusy(true);
        setError("");
        try {
          const result = await deleteRecordingsBatch(selectedIds);
          if (selected && selectedIds.includes(selected.recording.id)) setSelected(null);
          setSelectedIds([]);
          await refresh(selected && selectedIds.includes(selected.recording.id) ? null : undefined);
          showToast(`已删除 ${result.deleted.length} 条录音`, "success");
        } catch (err) {
          setError(err instanceof Error ? err.message : String(err));
          showToast("批量删除失败，请查看错误信息", "error");
        } finally {
          setBusy(false);
        }
      },
    });
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
    const oldDataDir = storageSettings?.data_dir ?? "";
    const newDataDir = storageDraft.data_dir?.trim() ?? "";
    if (newDataDir && newDataDir !== oldDataDir) {
      setSettingsBusy(true);
      showToast("正在分析新旧目录的数据差异…", "info");
      let preview: StorageMigrationPreview | null = null;
      try {
        preview = await previewStorageMigration(newDataDir);
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
        showToast("无法读取目录数据，请检查路径是否正确", "error");
        setSettingsBusy(false);
        return;
      }
      setSettingsBusy(false);

      const o = preview.old;
      const n = preview.new;
      const hasDb = o.recordings != null;

      function _formatDir(label: string, dir: string, s: typeof o): string {
        const lines: string[] = [];
        lines.push(`${label} ${dir}`);
        if (!s.exists) {
          lines.push("  · 目录不存在");
          return lines.join("\n");
        }
        if (hasDb) {
          lines.push(`  · 录音记录：${s.recordings ?? 0} 条`);
          lines.push(`  · 转写任务：${s.tasks ?? 0} 个`);
          lines.push(`  · 摘要：${s.summaries ?? 0} 条`);
          lines.push(`  · 转写片段：${s.transcript_segments ?? 0} 条`);
        }
        lines.push(`  · 数据库：${(s.app_db_size_mb ?? 0).toFixed(1)} MB`);
        lines.push(`  · 原始音频：${s.recording_count ?? 0} 个（${(s.recording_size_mb ?? 0).toFixed(1)} MB）`);
        lines.push(`  · 归一化音频：${s.normalized_count ?? 0} 个（${(s.normalized_size_mb ?? 0).toFixed(1)} MB）`);
        return lines.join("\n");
      }

      const oldText = _formatDir("📂 当前目录", oldDataDir, o);
      const newText = _formatDir("📂 新目录", newDataDir, n);
      const totalSize = (o.app_db_size_mb ?? 0) + (o.recording_size_mb ?? 0) + (o.normalized_size_mb ?? 0);
      const newHasData = (hasDb && n.recordings !== 0) || n.recording_count > 0 || (n.app_db_size_mb ?? 0) > 0;
      const isMerge = newHasData;

      let warning = "";
      if (isMerge) {
        const oldRec = o.recordings ?? 0;
        const newRec = n.recordings ?? 0;
        warning =
          "\n\n⚠ 新旧目录都有数据！将执行增量合并：\n" +
          `  · 数据库：旧库 ${oldRec} 条 → 新库 ${newRec} 条 → 合并后 ${oldRec + newRec} 条（UUID 去重）\n` +
          "  · 音频文件：同名文件保留新目录版本，仅补充缺失文件\n" +
          "  · 合并后两边的录音记录都会保留，不会丢失任何数据";
      }
      if (totalSize > 1000) {
        warning += `\n\n⚠ 数据量较大（约 ${(totalSize / 1024).toFixed(1)} GB），请耐心等待。`;
      }

      const verb = isMerge ? "合并" : "迁移";
      const title = isMerge ? "确认合并数据目录" : "确认迁移数据目录";
      const header = isMerge
        ? "合并操作将把当前目录的数据合并到新目录，新目录已有的数据会保留。"
        : "迁移操作将把当前目录下的所有数据复制到新目录。";
      const contentSection = isMerge
        ? "── 合并内容 ──\n① 数据库逐表合并（INSERT OR IGNORE，UUID 去重）\n② 缺失的原始音频文件（recordings/）\n③ 缺失的归一化音频文件（normalized/）"
        : "── 迁移内容 ──\n① 数据库文件（app.db）— 录音记录、任务、转写、摘要\n② 原始音频文件（recordings/）\n③ 归一化音频文件（normalized/）";
      const btnLabel = isMerge ? "开始合并数据" : "开始迁移数据";

      setConfirmDialog({
        title,
        message:
          header + "\n\n" +
          oldText + "\n\n" +
          newText + "\n\n" +
          contentSection + "\n\n" +
          "── 注意事项 ──\n" +
          "· 旧目录文件保留不动，不会删除或修改\n" +
          "· 修改 .env 文件后需重启应用才能生效\n" +
          "· 建议合并前关闭其他终端上的应用" +
          warning,
        confirmLabel: btnLabel,
        tone: "danger",
        onConfirm: async () => {
          setConfirmDialog(null);
          setSettingsBusy(true);
          showToast(`正在${verb}数据库和文件…`, "info");
          try {
            const migResult = await migrateStorage(newDataDir);
            for (const line of migResult.results) {
              showToast(line, "success");
            }
            showToast("正在更新配置文件…", "info");
            await doSaveStorageSettings();
            showToast(`数据${verb}完成，请重启应用使新目录生效`, "success");
          } catch (err) {
            showToast(`${verb}失败：` + (err instanceof Error ? err.message : String(err)), "error");
            setError(err instanceof Error ? err.message : String(err));
          } finally {
            setSettingsBusy(false);
          }
        },
      });
      return;
    }
    await doSaveStorageSettings();
  }

  async function doSaveStorageSettings() {
    setSettingsBusy(true);
    setError("");
    try {
      const updated = await updateStorageSettings(storageDraft);
      setStorageSettings(updated);
      setStorageDraft({ data_dir: updated.data_dir, transcript_dir: updated.transcript_dir, summary_dir: updated.summary_dir });
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
      await reloadRecordings(appliedFilters.query, appliedFilters.tag);
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
    downloadFile(
      `/api/recordings/${selected.recording.id}/exports/transcript?format=${format}`,
      `${selected.recording.filename}-transcript.${format}`
    ).catch((err) => {
      setError(err instanceof Error ? err.message : String(err));
      showToast("转写导出失败", "error");
    });
  }

  function downloadSummary(summaryId: string, format: ExportFormat) {
    downloadFile(`/api/summaries/${summaryId}/export?format=${format}`, `summary.${format}`).catch((err) => {
      setError(err instanceof Error ? err.message : String(err));
      showToast("摘要导出失败", "error");
    });
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
          await reloadRecordings(appliedFilters.query, appliedFilters.tag);
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

  async function applyFilters() {
    const nextFilters = {
      query: draftFilters.query,
      statuses: [...draftFilters.statuses],
      sources: [...draftFilters.sources],
      tag: draftFilters.tag,
    };
    setAppliedFilters(nextFilters);
    await reloadRecordings(nextFilters.query, nextFilters.tag);
  }

  async function resetFilters() {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setSortKey("created_desc");
    await reloadRecordings("", "");
  }

  async function clearAppliedQuery() {
    setDraftFilters((draft) => ({ ...draft, query: "" }));
    setAppliedFilters((filters) => ({ ...filters, query: "" }));
    await reloadRecordings("", appliedFilters.tag);
  }

  function clearAppliedTag() {
    setDraftFilters((draft) => ({ ...draft, tag: "" }));
    setAppliedFilters((filters) => ({ ...filters, tag: "" }));
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
        onViewChange={setViewWithNavigate}
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

        {view === "library" && recordings.length === 0 && (
          <div className="setup-banner">
            <h2>👋 欢迎使用 AI Recorder</h2>
            <p>
              当前数据目录：<code>{storageSettings?.data_dir ?? "未设置"}</code>
            </p>
            <div className="setup-steps">
              <div className="setup-step">
                <strong>新设备，想连接到已有数据？</strong>
                <p>去设置页把"数据总目录"改成同步盘文件夹（如 OneDrive、Syncthing），然后按提示迁移即可。</p>
              </div>
              <div className="setup-step">
                <strong>全新开始？</strong>
                <p>上传录音或者设置目录监控即可自动发现音频文件。</p>
              </div>
            </div>
            <button className="primary" onClick={() => setViewWithNavigate("settings")}>前往设置页面</button>
          </div>
        )}

        {view === "library" && (
          <LibraryPage
            recordings={recordings}
            filteredRecordings={filteredRecordings}
            selected={selected}
            selectedIds={selectedIds}
            setSelectedIds={setSelectedIds}
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
            clearAppliedTag={clearAppliedTag}
            clearAppliedStatus={clearAppliedStatus}
            clearAppliedSource={clearAppliedSource}
            searchMatchPreviews={searchMatchPreviews}
            selectRecording={selectRecording}
            handleDelete={handleDelete}
            runTranscription={runTranscription}
            runSummary={runSummary}
            runBatchTranscription={runBatchTranscription}
            runBatchSummary={runBatchSummary}
            deleteSelectedRecordings={handleBatchDelete}
            cancelActiveTask={handleCancelTask}
            updateTranscriptSegment={handleUpdateTranscriptSegment}
            updateRecordingTags={handleUpdateRecordingTags}
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
