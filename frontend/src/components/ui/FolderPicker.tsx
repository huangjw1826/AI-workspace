import React from "react";
import { FolderOpen } from "lucide-react";
import { pickFolder } from "../../lib/api";

export function FolderPicker({
  value,
  onChange,
  placeholder = "未选择",
  disabled = false,
  onError,
}: {
  value: string;
  onChange: (path: string) => void;
  placeholder?: string;
  disabled?: boolean;
  onError?: (message: string) => void;
}) {
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  async function handlePick() {
    setBusy(true);
    setError(null);
    try {
      const result = await pickFolder();
      if (result.error) {
        const msg = "无法弹出文件夹选择对话框，请检查后端日志";
        setError(msg);
        onError?.(msg);
        return;
      }
      if (result.cancelled) return;
      if (result.path) onChange(result.path);
    } catch (err) {
      const message = err instanceof Error ? err.message : "未知错误";
      setError(message);
      onError?.(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="folder-picker">
      <input
        className="form-input folder-picker-input"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
        disabled={disabled}
      />
      <button
        className="btn btn-secondary btn-sm folder-picker-btn"
        disabled={disabled || busy}
        onClick={handlePick}
        title="选择文件夹"
        type="button"
      >
        <FolderOpen size={14} />
        {busy ? "…" : "选择"}
      </button>
      {error && <span className="folder-picker-error">{error}</span>}
    </div>
  );
}
