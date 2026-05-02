import { Activity, Database, FileAudio, FolderSearch, RefreshCw, Settings, Upload } from "lucide-react";
import type React from "react";
import type { HealthStatus } from "../../lib/types";
import type { View } from "../../lib/viewTypes";

export function NavBar({
  view,
  health,
  busy,
  onViewChange,
  onUpload,
  onRefresh,
}: {
  view: View;
  health: HealthStatus | null;
  busy: boolean;
  onViewChange: (view: View) => void;
  onUpload: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onRefresh: () => void;
}) {
  return (
    <header className="nav">
      <div className="brand">
        <FileAudio size={22} />
        <span>AI Recorder</span>
      </div>
      <nav className="nav-links" aria-label="主导航">
        <button className={view === "library" ? "nav-item active" : "nav-item"} onClick={() => onViewChange("library")}>
          <Database size={17} /> 录音库
        </button>
        <button className={view === "watch" ? "nav-item active" : "nav-item"} onClick={() => onViewChange("watch")}>
          <FolderSearch size={17} /> 目录监控
        </button>
        <button className={view === "settings" ? "nav-item active" : "nav-item"} onClick={() => onViewChange("settings")}>
          <Settings size={17} /> 设置
        </button>
        <button className={view === "health" ? "nav-item active" : "nav-item"} onClick={() => onViewChange("health")}>
          <Activity size={17} /> 系统状态
        </button>
      </nav>
      <div className="nav-actions">
        <div className="nav-status">
          <span className={health?.status === "ok" ? "dot ok-bg" : "dot bad-bg"} />
          <span>{health?.status === "ok" ? "服务正常" : "待检查"}</span>
        </div>
        <label className="primary upload-button">
          <Upload size={16} />
          <span>{busy ? "处理中" : "上传录音"}</span>
          <input type="file" accept=".wav,.mp3,.m4a,.flac,.aac,.ogg" onChange={onUpload} />
        </label>
        <button className="secondary" onClick={onRefresh}>
          <RefreshCw size={16} /> 刷新
        </button>
      </div>
    </header>
  );
}

