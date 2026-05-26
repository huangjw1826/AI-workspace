import React from "react";
import { FolderOpen } from "lucide-react";
import { pickFolder } from "../../lib/api";

export function FolderPicker({
  value,
  onChange,
  placeholder = "未选择",
  disabled = false,
}: {
  value: string;
  onChange: (path: string) => void;
  placeholder?: string;
  disabled?: boolean;
}) {
  const [busy, setBusy] = React.useState(false);

  async function handlePick() {
    setBusy(true);
    try {
      const result = await pickFolder();
      if (result.path) {
        onChange(result.path);
      }
    } catch (err) {
      console.warn("Folder picker failed:", err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="folder-picker">
      <input
        className="folder-picker-input"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
      />
      <button
        className="folder-picker-btn secondary compact"
        disabled={disabled || busy}
        onClick={handlePick}
        title="选择文件夹"
        type="button"
      >
        <FolderOpen size={16} />
        {busy ? "…" : "选择"}
      </button>
    </div>
  );
}
