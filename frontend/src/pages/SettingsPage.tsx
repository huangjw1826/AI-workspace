import React from "react";
import { HardDrive, Settings } from "lucide-react";
import { SettingsSection } from "../components/ui/SettingsSection";
import type { LlmConnectivityResult, LlmSettings, LlmSettingsUpdate, StorageSettings } from "../lib/types";

type StorageDraft = Pick<StorageSettings, "transcript_dir" | "summary_dir">;

export function SettingsPage({
  storageDraft,
  setStorageDraft,
  storageSettings,
  llmDraft,
  setLlmDraft,
  llmSettings,
  llmTest,
  settingsBusy,
  saveStorageSettings,
  saveLlmSettings,
  checkLlmConnectivity,
  applyProviderDefaults,
}: {
  storageDraft: StorageDraft;
  setStorageDraft: React.Dispatch<React.SetStateAction<StorageDraft>>;
  storageSettings: StorageSettings | null;
  llmDraft: LlmSettingsUpdate;
  setLlmDraft: React.Dispatch<React.SetStateAction<LlmSettingsUpdate>>;
  llmSettings: LlmSettings | null;
  llmTest: LlmConnectivityResult | null;
  settingsBusy: boolean;
  saveStorageSettings: () => void;
  saveLlmSettings: () => void;
  checkLlmConnectivity: () => void;
  applyProviderDefaults: (provider: LlmSettingsUpdate["provider"]) => void;
}) {
  return (
    <section className="main-panel page-panel settings-page">
      <SettingsSection title="保存位置" icon={<HardDrive size={17} />}>
        <label>
          <span>转写保存目录</span>
          <input
            value={storageDraft.transcript_dir}
            onChange={(event) => setStorageDraft((draft) => ({ ...draft, transcript_dir: event.target.value }))}
          />
        </label>
        <label>
          <span>摘要保存目录</span>
          <input
            value={storageDraft.summary_dir}
            onChange={(event) => setStorageDraft((draft) => ({ ...draft, summary_dir: event.target.value }))}
          />
        </label>
        <div className="button-row">
          <button className="primary" disabled={settingsBusy} onClick={saveStorageSettings}>保存位置</button>
        </div>
        <p className="muted">
          转写目录：{storageSettings?.transcript_exists ? "可用" : "待检查"} · 摘要目录：
          {storageSettings?.summary_exists ? "可用" : "待检查"}
        </p>
      </SettingsSection>

      <SettingsSection title="LLM 设置" icon={<Settings size={17} />}>
        <div className="form-grid">
          <label>
            <span>服务商</span>
            <select
              value={llmDraft.provider}
              onChange={(event) => applyProviderDefaults(event.target.value as LlmSettingsUpdate["provider"])}
            >
              <option value="mimo">小米 MiMo</option>
              <option value="deepseek">DeepSeek</option>
              <option value="tongyi">通义千问</option>
              <option value="qwen">Qwen</option>
            </select>
          </label>
          <label>
            <span>模型</span>
            <input
              value={llmDraft.model ?? ""}
              onChange={(event) => setLlmDraft((draft) => ({ ...draft, model: event.target.value }))}
            />
          </label>
        </div>
        <label>
          <span>Base URL</span>
          <input
            value={llmDraft.base_url ?? ""}
            onChange={(event) => setLlmDraft((draft) => ({ ...draft, base_url: event.target.value }))}
          />
        </label>
        <label>
          <span>API Key</span>
          <input
            type="password"
            value={llmDraft.api_key ?? ""}
            placeholder={llmSettings?.api_key_masked || "未配置"}
            onChange={(event) => setLlmDraft((draft) => ({ ...draft, api_key: event.target.value }))}
          />
        </label>
        <div className="form-grid">
          <label>
            <span>Max Tokens</span>
            <input
              type="number"
              min={1}
              value={llmDraft.max_completion_tokens ?? 2048}
              onChange={(event) => setLlmDraft((draft) => ({ ...draft, max_completion_tokens: Number(event.target.value) }))}
            />
          </label>
          <label>
            <span>Temperature</span>
            <input
              type="number"
              min={0}
              max={1.5}
              step={0.1}
              value={llmDraft.temperature ?? ""}
              onChange={(event) =>
                setLlmDraft((draft) => ({
                  ...draft,
                  temperature: event.target.value === "" ? null : Number(event.target.value),
                }))
              }
            />
          </label>
        </div>
        <div className="button-row">
          <button className="primary" disabled={settingsBusy} onClick={saveLlmSettings}>保存 LLM</button>
          <button className="secondary" disabled={settingsBusy} onClick={checkLlmConnectivity}>测试连接</button>
        </div>
        {llmTest && <p className={llmTest.ok ? "ok" : "bad"}>{llmTest.message}</p>}
      </SettingsSection>
    </section>
  );
}
