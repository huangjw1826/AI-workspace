import React from "react";

export function MetricCard({
  label,
  value,
  hint,
  icon,
  progress,
}: {
  label: string;
  value: string;
  hint: string;
  icon: React.ReactNode;
  progress?: number;
}) {
  return (
    <div className="metric-card">
      {icon && <div className="metric-icon-watermark">{icon}</div>}
      <span className="metric-label">{label}</span>
      <span className="metric-value">{value}</span>
      {progress === undefined ? (
        <span className="metric-hint">{hint}</span>
      ) : (
        <>
          <div className="metric-progress-bar">
            <div
              className="metric-progress-fill"
              style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
            />
          </div>
          <span className="metric-hint">{hint}</span>
        </>
      )}
    </div>
  );
}
