import type {
  HealthStatus,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  Recording,
  RecordingDetail,
  StorageSettings,
  SummaryTemplate,
  Task,
  WatchEvent,
  WatchSettings
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8000";

export function apiUrl(path: string) {
  return `${API_BASE}${path}`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function getHealth() {
  return request<HealthStatus>("/health");
}

export function getLlmSettings() {
  return request<LlmSettings>("/api/settings/llm");
}

export function updateLlmSettings(settings: LlmSettingsUpdate) {
  return request<LlmSettings>("/api/settings/llm", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

export function testLlmConnectivity() {
  return request<LlmConnectivityResult>("/api/settings/llm/test", { method: "POST" });
}

export function getWatchSettings() {
  return request<WatchSettings>("/api/settings/watch");
}

export function updateWatchSettings(settings: WatchSettings) {
  return request<WatchSettings>("/api/settings/watch", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

export function scanWatchDirectory() {
  return request<{ count: number; events: WatchEvent[] }>("/api/watch/scan", { method: "POST" });
}

export function listWatchEvents() {
  return request<WatchEvent[]>("/api/watch/events");
}

export function listSummaryTemplates() {
  return request<SummaryTemplate[]>("/api/summary/templates");
}

export function getStorageSettings() {
  return request<StorageSettings>("/api/settings/storage");
}

export function updateStorageSettings(settings: Pick<StorageSettings, "transcript_dir" | "summary_dir">) {
  return request<StorageSettings>("/api/settings/storage", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

export function listRecordings() {
  return request<Recording[]>("/api/recordings");
}

export function getRecording(id: string) {
  return request<RecordingDetail>(`/api/recordings/${id}`);
}

export function uploadRecording(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return request<Recording>("/api/recordings", {
    method: "POST",
    body: formData
  });
}

export function startTranscription(id: string) {
  return request<Task>(`/api/transcribe/${id}`, { method: "POST" });
}

export function startSummary(id: string, mode = "summary") {
  return request<Task>(`/api/summary/${id}?mode=${encodeURIComponent(mode)}`, { method: "POST" });
}

export function getTask(id: string) {
  return request<Task>(`/api/tasks/${id}`);
}

export function deleteRecording(id: string) {
  return request<{ message: string }>(`/api/recordings/${id}`, { method: "DELETE" });
}

export function deleteSummary(id: string) {
  return request<{ message: string }>(`/api/summaries/${id}`, { method: "DELETE" });
}
