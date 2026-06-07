import React from "react";
import {
  getAsrSettings,
  getHealth,
  getLlmSettings,
  getStorageSettings,
  getWatchSettings,
  listWatchEvents,
  migrateStorage,
  previewStorageMigration,
  scanWatchDirectory,
  testLlmConnectivity,
  updateAsrSettings,
  updateLlmSettings,
  updateStorageSettings,
  updateWatchSettings,
} from "../lib/api";
import type {
  AsrSettings,
  AsrSettingsUpdate,
  HealthStatus,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  StorageMigrationPreview,
  StorageSettings,
  WatchEvent,
  WatchSettings,
} from "../lib/types";
import { useAppStore } from "../stores/appStore";

export function useSettingsActions() {
  const setBusy = useAppStore((s) => s.setBusy);
  const setError = useAppStore((s) => s.setError);
  const showToast = useAppStore((s) => s.showToast);

  const [health, setHealth] = React.useState<HealthStatus | null>(null);
  const [llmSettings, setLlmSettings] = React.useState<LlmSettings | null>(null);
  const [watchSettings, setWatchSettings] = React.useState<WatchSettings | null>(null);
  const [storageSettings, setStorageSettings] = React.useState<StorageSettings | null>(null);
  const [watchEvents, setWatchEvents] = React.useState<WatchEvent[]>([]);
  const [asrSettings, setAsrSettings] = React.useState<AsrSettings | null>(null);
  const [llmTest, setLlmTest] = React.useState<LlmConnectivityResult | null>(null);
  const [settingsBusy, setSettingsBusy] = React.useState(false);

  const [llmDraft, setLlmDraft] = React.useState<LlmSettingsUpdate>({
    provider: "mimo", api_key: "", base_url: "", model: "",
    mimo_thinking: "disabled", max_completion_tokens: 2048, temperature: null, top_p: null,
  });
  const [watchDraft, setWatchDraft] = React.useState<WatchSettings>({
    enabled: false, watch_dir: "", recursive: true, interval_seconds: 10, stable_count: 2, exists: false,
    auto_transcribe: false, auto_summary: false, auto_summary_mode: "structured_summary",
  });
  const [storageDraft, setStorageDraft] = React.useState({ data_dir: "", transcript_dir: "", summary_dir: "" });
  const [asrDraft, setAsrDraft] = React.useState<AsrSettingsUpdate>({
    enable_diarization: false, max_concurrency: 1,
  });

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

  const loadSettings = React.useCallback(async () => {
    await Promise.all([
      getHealth().then(setHealth).catch(() => {}),
      getLlmSettings()
        .then((r) => {
          setLlmSettings(r);
          setLlmDraft((d) => ({
            ...d, provider: r.provider, base_url: r.base_url, model: r.model,
            mimo_thinking: r.mimo_thinking, max_completion_tokens: r.max_completion_tokens,
            temperature: r.temperature, top_p: r.top_p,
          }));
        })
        .catch(() => {}),
      getWatchSettings().then((r) => { setWatchSettings(r); setWatchDraft(r); }).catch(() => {}),
      listWatchEvents().then(setWatchEvents).catch(() => {}),
      getStorageSettings()
        .then((r) => {
          setStorageSettings(r);
          setStorageDraft({ data_dir: r.data_dir, transcript_dir: r.transcript_dir, summary_dir: r.summary_dir });
        })
        .catch(() => {}),
      getAsrSettings()
        .then((r) => {
          setAsrSettings(r);
          setAsrDraft({ enable_diarization: r.enable_diarization, max_concurrency: r.max_concurrency });
        })
        .catch(() => {}),
    ]);
  }, []);

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

  const saveStorageSettings = async (confirmCallback: (state: any) => void) => {
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
    confirmCallback({
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

  return {
    health, setHealth,
    llmSettings, setLlmSettings,
    watchSettings, setWatchSettings,
    storageSettings, setStorageSettings,
    watchEvents, setWatchEvents,
    asrSettings, setAsrSettings,
    llmTest, setLlmTest,
    settingsBusy, setSettingsBusy,
    llmDraft, setLlmDraft,
    watchDraft, setWatchDraft,
    storageDraft, setStorageDraft,
    asrDraft, setAsrDraft,
    loadSettings,
    saveLlmSettings,
    saveWatchSettings,
    saveAsrSettings,
    saveStorageSettings,
    runWatchScan,
    checkLlmConnectivity,
    applyProviderDefaults,
  };
}
