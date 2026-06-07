import React from "react";
import {
  cancelTask,
  deleteRecording,
  deleteRecordingsBatch,
  deleteSummary,
  downloadFile,
  getRecording,
  listRecordings,
  startSummary,
  startSummaryBatch,
  startTranscription,
  startTranscriptionBatch,
  updateRecordingTags,
  updateTranscriptSegment,
  uploadRecording,
  resyncRecordings,
} from "../lib/api";
import type {
  ExportFormat,
  Recording,
  RecordingDetail,
  Task,
} from "../lib/types";
import type { LibraryFilters } from "../lib/viewTypes";
import { useAppStore } from "../stores/appStore";

interface UseRecordingActionsOptions {
  appliedFilters: LibraryFilters;
  selected: RecordingDetail | null;
  setSelected: (detail: RecordingDetail | null) => void;
  setActiveTask: (task: Task | null) => void;
  setRecordings: (recordings: Recording[]) => void;
  setSearchMatchPreviews: (previews: Record<string, string[]>) => void;
  setSelectedIds: React.Dispatch<React.SetStateAction<string[]>>;
  setDetailTab: (tab: "transcript" | "summary" | "info") => void;
  setSummaryMode: (mode: string) => void;
  setConfirmDialog: (state: any) => void;
}

export function useRecordingActions(opts: UseRecordingActionsOptions) {
  const setBusy = useAppStore((s) => s.setBusy);
  const setError = useAppStore((s) => s.setError);
  const showToast = useAppStore((s) => s.showToast);

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

  const refresh = React.useCallback(
    async (selectedId?: string | null) => {
      const idToRefresh = selectedId === undefined ? opts.selected?.recording.id : selectedId;
      const result = await listRecordings(opts.appliedFilters.query, opts.appliedFilters.tag);
      opts.setRecordings(result.recordings);
      opts.setSearchMatchPreviews(result.match_previews);
      if (idToRefresh) {
        try { opts.setSelected(await getRecording(idToRefresh)); } catch { opts.setSelected(null); }
      }
    },
    [opts.appliedFilters.query, opts.appliedFilters.tag, opts.selected?.recording.id],
  );

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      const recording = await uploadRecording(file);
      opts.setSelected(await getRecording(recording.id));
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
    opts.setSelected(detail);
    opts.setDetailTab("transcript");
    opts.setActiveTask(detail.tasks.find((t) => !["completed", "error", "cancelled"].includes(t.status)) ?? null);
  };

  const handleDelete = (recordingId: string, event: React.MouseEvent) => {
    event.stopPropagation();
    opts.setConfirmDialog({
      title: "删除录音",
      message: "确定要删除这条录音吗？转写结果、摘要和应用生成文件也会一并删除。",
      confirmLabel: "删除",
      tone: "danger",
      onConfirm: () => run(async () => {
        await deleteRecording(recordingId);
        if (opts.selected?.recording.id === recordingId) opts.setSelected(null);
        await refresh(opts.selected?.recording.id === recordingId ? null : undefined);
      }, "录音已删除"),
    });
  };

  const runTranscription = () => opts.selected && run(async () => {
    const task = await startTranscription(opts.selected!.recording.id);
    opts.setActiveTask(task);
    opts.setSelected(await getRecording(opts.selected!.recording.id));
  }, "转写任务已启动");

  const runSummary = (mode?: string) => opts.selected && run(async () => {
    const m = mode || "structured_summary";
    opts.setSummaryMode(m);
    const task = await startSummary(opts.selected!.recording.id, m);
    opts.setActiveTask(task);
    opts.setSelected(await getRecording(opts.selected!.recording.id));
    opts.setDetailTab("summary");
  }, "摘要任务已启动");

  const handleCancelTask = () => run(async () => {
    const task = await cancelTask(opts.selected!.tasks.find(
      (t) => !["completed", "error", "cancelled"].includes(t.status)
    )!.id);
    opts.setActiveTask(task);
    opts.setSelected(await getRecording(task.recording_id));
  }, "任务已取消");

  const handleUpdateTranscriptSegment = (segmentId: string, text: string) => opts.selected && run(async () => {
    await updateTranscriptSegment(opts.selected!.recording.id, segmentId, text);
    opts.setSelected(await getRecording(opts.selected!.recording.id));
  }, "转写片段已保存");

  const handleUpdateRecordingTags = (tags: string[]) => opts.selected && run(async () => {
    await updateRecordingTags(opts.selected!.recording.id, tags);
    await refresh(opts.selected!.recording.id);
  }, "标签已保存");

  const runBatchTranscription = (selectedIds: string[]) => selectedIds.length && run(async () => {
    const tasks = await startTranscriptionBatch(selectedIds);
    opts.setActiveTask(tasks[0] ?? null);
    await refresh(opts.selected?.recording.id);
  }, `已创建 ${selectedIds.length} 个转写任务`);

  const runBatchSummary = (selectedIds: string[], summaryMode: string) => selectedIds.length && run(async () => {
    const tasks = await startSummaryBatch(selectedIds, summaryMode);
    opts.setActiveTask(tasks[0] ?? null);
    await refresh(opts.selected?.recording.id);
  }, `已创建 ${selectedIds.length} 个摘要任务`);

  const handleBatchDelete = (selectedIds: string[]) => selectedIds.length && opts.setConfirmDialog({
    title: "批量删除",
    message: `确定删除选中的 ${selectedIds.length} 条录音吗？`,
    confirmLabel: "删除",
    tone: "danger",
    onConfirm: () => run(async () => {
      const result = await deleteRecordingsBatch(selectedIds);
      if (opts.selected && selectedIds.includes(opts.selected.recording.id)) opts.setSelected(null);
      opts.setSelectedIds([]);
      await refresh(opts.selected && selectedIds.includes(opts.selected.recording.id) ? null : undefined);
      showToast(`已删除 ${result.deleted.length} 条录音`, "success");
    }),
  });

  const downloadTranscript = (format: ExportFormat) => {
    if (!opts.selected) return;
    downloadFile(
      `/api/recordings/${opts.selected.recording.id}/exports/transcript?format=${format}`,
      `${opts.selected.recording.filename}-transcript.${format}`,
    ).catch((err) => { setError(err.message); showToast("导出失败", "error"); });
  };

  const downloadSummary = (summaryId: string, format: ExportFormat) => {
    downloadFile(`/api/summaries/${summaryId}/export?format=${format}`, `summary.${format}`)
      .catch((err) => { setError(err.message); showToast("导出失败", "error"); });
  };

  const handleDeleteSummary = (summaryId: string) => {
    if (!opts.selected) return;
    opts.setConfirmDialog({
      title: "删除摘要", message: "确定要删除这条摘要吗？",
      confirmLabel: "删除", tone: "danger",
      onConfirm: () => run(async () => {
        await deleteSummary(summaryId);
        opts.setSelected(await getRecording(opts.selected!.recording.id));
      }, "摘要已删除"),
    });
  };

  return {
    run,
    refresh,
    handleUpload,
    handleResync,
    selectRecording,
    handleDelete,
    runTranscription,
    runSummary,
    handleCancelTask,
    handleUpdateTranscriptSegment,
    handleUpdateRecordingTags,
    runBatchTranscription,
    runBatchSummary,
    handleBatchDelete,
    downloadTranscript,
    downloadSummary,
    handleDeleteSummary,
  };
}
