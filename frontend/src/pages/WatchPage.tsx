import React from "react";
import { FolderSearch } from "lucide-react";
import { FolderPicker } from "../components/ui/FolderPicker";
import { SettingsSection } from "../components/ui/SettingsSection";
import type { WatchEvent, WatchSettings } from "../lib/types";

export function WatchPage({
  watchDraft,
  setWatchDraft,
  watchSettings,
  watchEvents,
  settingsBusy,
  saveWatchSettings,
  runWatchScan,
}: {
  watchDraft: WatchSettings;
  setWatchDraft: React.Dispatch<React.SetStateAction<WatchSettings>>;
  watchSettings: WatchSettings | null;
  watchEvents: WatchEvent[];
  settingsBusy: boolean;
  saveWatchSettings: () => void;
  runWatchScan: () => void;
}) {
  return (
    <section className="page-panel anim-fade-in">
      <SettingsSection title="录音目录监控" icon={<FolderSearch size={17} />}>
        <label className="check-line">
          <input
            type="checkbox"
            checked={watchDraft.enabled}
            onChange={(e) =>
              setWatchDraft((d) => ({ ...d, enabled: e.target.checked }))
            }
          />
          <span>启用目录监控</span>
        </label>

        <label>
          <span>录音目录路径</span>
          <FolderPicker
            value={watchDraft.watch_dir}
            onChange={(path) => setWatchDraft((d) => ({ ...d, watch_dir: path }))}
            placeholder="点击右侧选择按钮选择文件夹"
            disabled={settingsBusy}
          />
        </label>

        <div className="form-grid">
          <label className="check-line">
            <input
              type="checkbox"
              checked={watchDraft.recursive}
              onChange={(e) =>
                setWatchDraft((d) => ({ ...d, recursive: e.target.checked }))
              }
            />
            <span>包含子文件夹</span>
          </label>
          <label>
            <span>扫描间隔（秒）</span>
            <input
              className="form-input"
              type="number"
              min={2}
              value={watchDraft.interval_seconds}
              onChange={(e) =>
                setWatchDraft((d) => ({
                  ...d,
                  interval_seconds: Number(e.target.value),
                }))
              }
            />
          </label>
        </div>

        <label>
          <span>稳定检测次数</span>
          <input
            className="form-input"
            type="number"
            min={2}
            max={20}
            value={watchDraft.stable_count}
            onChange={(e) =>
              setWatchDraft((d) => ({
                ...d,
                stable_count: Number(e.target.value),
              }))
            }
          />
          <span className="muted">
            文件大小和修改时间连续 N 次扫描不变后才处理（同步盘建议 ≥3）
          </span>
        </label>

        <div className="form-grid">
          <label className="check-line">
            <input
              type="checkbox"
              checked={watchDraft.auto_transcribe}
              onChange={(e) =>
                setWatchDraft((d) => ({ ...d, auto_transcribe: e.target.checked }))
              }
            />
            <span>自动转写新录音</span>
          </label>
          <label className="check-line">
            <input
              type="checkbox"
              checked={watchDraft.auto_summary}
              onChange={(e) =>
                setWatchDraft((d) => ({ ...d, auto_summary: e.target.checked }))
              }
            />
            <span>自动生成摘要</span>
          </label>
        </div>

        <div className="button-row">
          <button
            className="btn btn-primary"
            disabled={settingsBusy}
            onClick={saveWatchSettings}
          >
            保存监控
          </button>
          <button
            className="btn btn-secondary"
            disabled={settingsBusy || !watchSettings?.watch_dir}
            onClick={runWatchScan}
          >
            立即扫描
          </button>
        </div>

        <p className="muted">
          当前：{watchSettings?.enabled ? "已启用" : "未启用"} ·{" "}
          {watchSettings?.exists ? "目录可访问" : "目录待检查"}
        </p>
      </SettingsSection>

      <div className="event-list">
        {watchEvents.length === 0 ? (
          <p className="muted">暂无扫描记录。</p>
        ) : (
          watchEvents.map((event) => (
            <div key={event.id} className="event-item">
              <strong>{event.status}</strong>
              <span>{event.filename}</span>
              <em>{event.reason}</em>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
