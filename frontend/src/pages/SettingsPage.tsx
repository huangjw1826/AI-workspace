import React from "react";
import {
  Check,
  CheckCircle,
  Copy,
  HardDrive,
  Key,
  Mic,
  Settings,
  Trash2,
  XCircle,
} from "lucide-react";
import { FolderPicker } from "../components/ui/FolderPicker";
import { SettingsSection } from "../components/ui/SettingsSection";
import {
  createApiToken,
  deleteApiToken,
  listApiTokens,
  updateApiToken,
} from "../lib/api";
import type {
  ApiToken,
  AsrSettings,
  AsrSettingsUpdate,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  StorageSettings,
} from "../lib/types";

type StorageDraft = Pick<
  StorageSettings,
  "data_dir" | "transcript_dir" | "summary_dir"
>;

export function SettingsPage({
  storageDraft,
  setStorageDraft,
  storageSettings,
  llmDraft,
  setLlmDraft,
  llmSettings,
  llmTest,
  asrDraft,
  setAsrDraft,
  asrSettings,
  settingsBusy,
  saveStorageSettings,
  saveLlmSettings,
  saveAsrSettings,
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
  asrDraft: AsrSettingsUpdate;
  setAsrDraft: React.Dispatch<React.SetStateAction<AsrSettingsUpdate>>;
  asrSettings: AsrSettings | null;
  settingsBusy: boolean;
  saveStorageSettings: () => void;
  saveLlmSettings: () => void;
  saveAsrSettings: () => void;
  checkLlmConnectivity: () => void;
  applyProviderDefaults: (provider: LlmSettingsUpdate["provider"]) => void;
}) {
  return (
    <section className="page-panel anim-fade-in">
      <SettingsSection title="保存位置" icon={<HardDrive size={17} />}>
        <label>
          <span>数据总目录</span>
          <FolderPicker
            value={storageDraft.data_dir}
            onChange={(path) =>
              setStorageDraft((d) => ({ ...d, data_dir: path }))
            }
            placeholder={
              storageSettings?.data_dir || "存放录音、数据库等所有数据的文件夹"
            }
            disabled={settingsBusy}
          />
          <span className="muted">
            修改后需重启应用。多电脑共享数据？改到同步盘文件夹即可
          </span>
        </label>
        <label>
          <span>转写保存目录</span>
          <FolderPicker
            value={storageDraft.transcript_dir}
            onChange={(path) =>
              setStorageDraft((d) => ({ ...d, transcript_dir: path }))
            }
            placeholder="点击右侧选择按钮选择文件夹"
            disabled={settingsBusy}
          />
        </label>
        <label>
          <span>摘要保存目录</span>
          <FolderPicker
            value={storageDraft.summary_dir}
            onChange={(path) =>
              setStorageDraft((d) => ({ ...d, summary_dir: path }))
            }
            placeholder="点击右侧选择按钮选择文件夹"
            disabled={settingsBusy}
          />
        </label>
        <div className="button-row">
          <button
            className="btn btn-primary"
            disabled={settingsBusy}
            onClick={saveStorageSettings}
          >
            保存位置
          </button>
        </div>
        <p className="muted">
          转写目录：{storageSettings?.transcript_exists ? "可用" : "待检查"} ·
          摘要目录：{storageSettings?.summary_exists ? "可用" : "待检查"}
        </p>
      </SettingsSection>

      <SettingsSection title="语音转写 (ASR)" icon={<Mic size={17} />}>
        <label>
          <span>说话人分离（说话人识别）</span>
          <label className="toggle-row">
            <input
              type="checkbox"
              className="toggle-input"
              checked={asrDraft.enable_diarization ?? false}
              onChange={(e) =>
                setAsrDraft((d) => ({
                  ...d,
                  enable_diarization: e.target.checked,
                }))
              }
              disabled={settingsBusy}
            />
            <span className="toggle-switch" />
            <span className="toggle-label-text">
              {asrDraft.enable_diarization ? "已启用" : "已禁用"}
            </span>
          </label>
          <span className="muted">
            启用后将使用 cam++ 模型自动识别不同说话人。启用后转写速度会降低约 30%，且首次启用需下载模型（约 80MB）。
          </span>
        </label>
        <div className="button-row">
          <button
            className="btn btn-primary"
            disabled={settingsBusy}
            onClick={saveAsrSettings}
          >
            保存 ASR
          </button>
        </div>
      </SettingsSection>

      <SettingsSection title="LLM 设置" icon={<Settings size={17} />}>
        <div className="form-grid">
          <label>
            <span>服务商</span>
            <select
              className="form-select"
              value={llmDraft.provider}
              onChange={(e) =>
                applyProviderDefaults(
                  e.target.value as LlmSettingsUpdate["provider"]
                )
              }
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
              className="form-input"
              value={llmDraft.model ?? ""}
              onChange={(e) =>
                setLlmDraft((d) => ({ ...d, model: e.target.value }))
              }
            />
          </label>
        </div>
        <label>
          <span>Base URL</span>
          <input
            className="form-input"
            value={llmDraft.base_url ?? ""}
            onChange={(e) =>
              setLlmDraft((d) => ({ ...d, base_url: e.target.value }))
            }
          />
        </label>
        <label>
          <span>API Key</span>
          <input
            className="form-input"
            type="password"
            value={llmDraft.api_key ?? ""}
            placeholder={llmSettings?.api_key_masked || "未配置"}
            onChange={(e) =>
              setLlmDraft((d) => ({ ...d, api_key: e.target.value }))
            }
          />
        </label>
        <div className="form-grid">
          <label>
            <span>Max Tokens</span>
            <input
              className="form-input"
              type="number"
              min={1}
              value={llmDraft.max_completion_tokens ?? 2048}
              onChange={(e) =>
                setLlmDraft((d) => ({
                  ...d,
                  max_completion_tokens: Number(e.target.value),
                }))
              }
            />
          </label>
          <label>
            <span>Temperature</span>
            <input
              className="form-input"
              type="number"
              min={0}
              max={1.5}
              step={0.1}
              value={llmDraft.temperature ?? ""}
              onChange={(e) =>
                setLlmDraft((d) => ({
                  ...d,
                  temperature:
                    e.target.value === "" ? null : Number(e.target.value),
                }))
              }
            />
          </label>
        </div>
        <div className="button-row">
          <button
            className="btn btn-primary"
            disabled={settingsBusy}
            onClick={saveLlmSettings}
          >
            保存 LLM
          </button>
          <button
            className="btn btn-secondary"
            disabled={settingsBusy}
            onClick={checkLlmConnectivity}
          >
            测试连接
          </button>
        </div>
        {llmTest && (
          <p className={llmTest.ok ? "ok" : "bad"}>{llmTest.message}</p>
        )}
      </SettingsSection>

      <ApiTokenSection />
    </section>
  );
}

function ApiTokenSection() {
  const [tokens, setTokens] = React.useState<ApiToken[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [newTokenName, setNewTokenName] = React.useState("");
  const [creating, setCreating] = React.useState(false);
  const [createdTokenData, setCreatedTokenData] = React.useState<{
    token: string;
  } | null>(null);
  const [copied, setCopied] = React.useState(false);

  React.useEffect(() => {
    loadTokens().catch(() => {});
  }, []);

  async function loadTokens() {
    setLoading(true);
    setError(null);
    try {
      setTokens(await listApiTokens());
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载 Token 列表失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate() {
    const name = newTokenName.trim();
    if (!name || creating) return;
    setCreating(true);
    setError(null);
    try {
      const result = await createApiToken({ name, device_info: "{}" });
      setCreatedTokenData({ token: result.token ?? "" });
      setNewTokenName("");
      await loadTokens();
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建 Token 失败");
    } finally {
      setCreating(false);
    }
  }

  async function handleToggleActive(token: ApiToken) {
    const original = tokens.find((t) => t.id === token.id);
    setTokens((prev) =>
      prev.map((t) =>
        t.id === token.id ? { ...t, is_active: !t.is_active } : t
      )
    );
    try {
      await updateApiToken(token.id, { is_active: !token.is_active });
    } catch {
      if (original)
        setTokens((prev) => prev.map((t) => (t.id === token.id ? original : t)));
    }
  }

  async function handleDelete(tokenId: string) {
    if (!window.confirm("确定要删除此 Token 吗？")) return;
    const original = tokens.find((t) => t.id === tokenId);
    setTokens((prev) => prev.filter((t) => t.id !== tokenId));
    try {
      await deleteApiToken(tokenId);
    } catch {
      if (original) setTokens((prev) => [...prev, original]);
    }
  }

  function handleCopyToken(token: string) {
    navigator.clipboard.writeText(token).then(
      () => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      },
      () => setError("复制 Token 失败，请手动复制")
    );
  }

  return (
    <SettingsSection title="远程访问 Token" icon={<Key size={17} />}>
      {createdTokenData ? (
        <div className="token-created">
          <p className="ok">Token 创建成功！请立即复制保存，此 Token 不会再次显示。</p>
          <div className="token-created-value">
            <code>{createdTokenData.token}</code>
            <button
              className="btn btn-icon"
              onClick={() => handleCopyToken(createdTokenData.token)}
              title="复制 Token"
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}
            </button>
          </div>
          <div className="button-row">
            <button
              className="btn btn-primary"
              onClick={() => setCreatedTokenData(null)}
            >
              我已保存
            </button>
          </div>
        </div>
      ) : (
        <>
          <label>
            <span>设备名称</span>
            <div className="token-create-row">
              <input
                className="form-input"
                value={newTokenName}
                onChange={(e) => setNewTokenName(e.target.value)}
                placeholder="例如：我的 Android 手机"
                disabled={creating}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleCreate();
                }}
              />
              <button
                className="btn btn-primary"
                disabled={creating || !newTokenName.trim()}
                onClick={handleCreate}
              >
                {creating ? "创建中..." : "创建 Token"}
              </button>
            </div>
          </label>
        </>
      )}

      {loading && <p className="muted">加载中...</p>}
      {error && <p className="bad">{error}</p>}

      {!loading && !error && tokens.length === 0 && (
        <p className="muted">还没有创建 Token，请在上方创建。</p>
      )}

      {tokens.length > 0 && (
        <div className="token-list">
          {tokens.map((token) => (
            <div
              key={token.id}
              className={`token-item${token.is_active ? "" : " token-item-disabled"}`}
            >
              <div className="token-item-info">
                <span className="token-item-name">{token.name}</span>
                <span
                  className={`token-item-status${
                    token.is_active ? " status-active" : " status-disabled"
                  }`}
                >
                  {token.is_active ? "活跃" : "已禁用"}
                </span>
              </div>
              <div className="token-item-meta">
                <code className="token-item-token">{token.token ?? ""}</code>
                <span className="muted">
                  {token.last_used_at
                    ? `最近使用: ${new Date(token.last_used_at).toLocaleDateString("zh-CN")}`
                    : "从未使用"}
                </span>
              </div>
              <div className="token-item-actions">
                <button
                  className="btn btn-icon"
                  onClick={() => handleToggleActive(token)}
                  title={token.is_active ? "禁用" : "启用"}
                >
                  {token.is_active ? (
                    <CheckCircle size={16} className="text-success" />
                  ) : (
                    <XCircle size={16} className="text-muted" />
                  )}
                </button>
                <button
                  className="btn btn-icon"
                  onClick={() => handleDelete(token.id)}
                  title="删除"
                >
                  <Trash2 size={16} className="text-danger" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <p className="muted" style={{ fontSize: 12 }}>
        Token 用于 Android 客户端远程访问后端 API。每个设备应使用独立的 Token。
      </p>
    </SettingsSection>
  );
}
