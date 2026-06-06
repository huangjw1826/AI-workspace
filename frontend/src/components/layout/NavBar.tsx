import React from "react";
import {
  Activity,
  Database,
  FileAudio,
  FolderSearch,
  FolderSync,
  RefreshCw,
  Settings,
  Upload,
} from "lucide-react";
import type { HealthStatus } from "../../lib/types";
import type { View } from "../../lib/viewTypes";

const NAV_ITEMS: { view: View; icon: React.ReactNode; label: string }[] = [
  { view: "library", icon: <Database size={16} />, label: "录音库" },
  { view: "watch", icon: <FolderSearch size={16} />, label: "目录监控" },
  { view: "settings", icon: <Settings size={16} />, label: "设置" },
  { view: "health", icon: <Activity size={16} />, label: "系统状态" },
];

export function NavBar({
  view,
  health,
  busy,
  onViewChange,
  onUpload,
  onRefresh,
  onResync,
}: {
  view: View;
  health: HealthStatus | null;
  busy: boolean;
  onViewChange: (view: View) => void;
  onUpload: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onRefresh: () => void;
  onResync: () => void;
}) {
  return (
    <header className="nav">
      <button className="brand" onClick={() => onViewChange("library")}>
        <div className="brand-icon">
          <FileAudio size={16} strokeWidth={2.5} />
        </div>
        <span className="brand-name">AI Recorder</span>
      </button>

      <nav className="nav-links" aria-label="主导航">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.view}
            className={`nav-item${view === item.view ? " active" : ""}`}
            onClick={() => onViewChange(item.view)}
          >
            {item.icon}
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="nav-actions">
        <div className="nav-status">
          <span className={`status-dot${health?.status === "ok" ? " ok" : " bad"}`} />
          <span>{health?.status === "ok" ? "服务正常" : "待检查"}</span>
        </div>

        <label className="btn btn-secondary upload-btn" title="上传录音文件">
          <Upload size={15} />
          <span>{busy ? "处理中..." : "上传录音"}</span>
          <input
            type="file"
            accept=".wav,.mp3,.m4a,.flac,.aac,.ogg"
            onChange={onUpload}
            disabled={busy}
          />
        </label>

        <button className="btn btn-icon" onClick={onRefresh} title="刷新数据">
          <RefreshCw size={16} />
        </button>

        <button className="btn btn-icon" onClick={onResync} disabled={busy} title="重新同步所有文件信息（时长、大小等）">
          <FolderSync size={16} />
        </button>
      </div>
    </header>
  );
}
