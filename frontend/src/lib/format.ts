import type { Task } from "./types";

export function formatDuration(value?: number) {
  if (value === undefined || value === null) return "--";
  const total = Math.round(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, "0");
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, "0")}:${seconds}`;
  return `${minutes}:${seconds}`;
}

export function formatSize(value?: number) {
  if (!value) return "--";
  if (value >= 1024 * 1024 * 1024) return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${value} B`;
}

export function formatDate(value?: string | number) {
  if (!value) return "--";
  const date = typeof value === "number" ? new Date(value * 1000) : new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return date.toLocaleString();
}

export function formatShortDate(value?: string | number) {
  if (!value) return "--";
  const date = typeof value === "number" ? new Date(value * 1000) : new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return date.toLocaleString(undefined, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function statusLabel(status: string) {
  const labels: Record<string, string> = {
    uploaded: "待转写",
    queued: "排队中",
    running: "运行中",
    cancelled: "已取消",
    normalizing: "处理中",
    transcribing: "转写中",
    transcribed: "已转写",
    completed: "已摘要",
    error: "错误",
  };
  return labels[status] ?? status;
}

export function toggleValue(values: string[], value: string) {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value];
}

export function clampPercent(value: number) {
  return Math.max(0, Math.min(100, Math.round(value)));
}

export function summaryPreview(content: string) {
  return content
    .replace(/[#>*_`|]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 96);
}

export function taskLabel(task?: Task | null) {
  if (!task) return "";
  if (task.status === "cancelled") {
    if (task.task_type === "transcribe" || task.task_type === "transcription") return "转写已取消";
    if (task.task_type.startsWith("summary:")) return "摘要已取消";
    return `${task.task_type} 已取消`;
  }
  if (task.status === "completed") {
    if (task.task_type === "transcribe" || task.task_type === "transcription") return "转写完成";
    if (task.task_type.startsWith("summary:")) return "摘要生成完成";
    return `${task.task_type} 完成`;
  }
  if (task.status === "error") {
    if (task.task_type === "transcribe" || task.task_type === "transcription") return "转写失败";
    if (task.task_type.startsWith("summary:")) return "摘要生成失败";
    return `${task.task_type} 失败`;
  }
  if (task.task_type === "transcribe" || task.task_type === "transcription") return "正在转写录音";
  if (task.task_type.startsWith("summary:")) return "正在生成摘要";
  return task.task_type;
}
