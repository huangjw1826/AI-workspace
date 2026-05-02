import type React from "react";

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
      <div className="metric-top">
        <span className="metric-icon">{icon}</span>
        <span>{label}</span>
      </div>
      <strong>{value}</strong>
      {progress === undefined ? (
        <span>{hint}</span>
      ) : (
        <div className="metric-progress">
          <i style={{ width: `${progress}%` }} />
          <span>{hint}</span>
        </div>
      )}
    </div>
  );
}

