import type {
  HealthStatus,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  Recording,
  RecordingDetail,
  SearchResult,
  StorageSettings,
  SummaryTemplate,
  Task,
  WatchEvent,
  WatchSettings
} from "./types";
function defaultApiBaseUrl() {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured.replace(/\/+$/, "");
  if (typeof window !== "undefined" && ["5173", "5174"].includes(window.location.port)) {
    return "http://127.0.0.1:8000";
  }
  return typeof window !== "undefined" ? window.location.origin : "http://127.0.0.1:8000";
}

const API_BASE_URL = defaultApiBaseUrl();

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  const response = await fetch(apiUrl(path), { ...init, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

function filenameFromDisposition(disposition: string | null, fallback: string) {
  if (!disposition) return fallback;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1].replace(/"/g, ""));
  const asciiMatch = disposition.match(/filename="?([^";]+)"?/i);
  return asciiMatch?.[1] ?? fallback;
}

export async function downloadFile(path: string, fallbackName: string) {
  const headers = new Headers();

  const response = await fetch(apiUrl(path), { headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Download failed: ${response.status}`);
  }

  const blob = await response.blob();
  const href = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = filenameFromDisposition(response.headers.get("content-disposition"), fallbackName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(href);
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

export function listRecordings(query = "", tag = "") {
  const params = new URLSearchParams();
  if (query.trim()) params.set("query", query.trim());
  if (tag.trim()) params.set("tag", tag.trim());
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return request<SearchResult>(`/api/recordings${suffix}`);
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

export function startTranscriptionBatch(recordingIds: string[]) {
  return request<Task[]>("/api/transcribe/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

export function startSummary(id: string, mode = "summary") {
  return request<Task>(`/api/summary/${id}?mode=${encodeURIComponent(mode)}`, { method: "POST" });
}

export function startSummaryBatch(recordingIds: string[], mode = "summary") {
  return request<Task[]>(`/api/summary/batch?mode=${encodeURIComponent(mode)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

export function getTask(id: string) {
  return request<Task>(`/api/tasks/${id}`);
}

export function cancelTask(id: string) {
  return request<Task>(`/api/tasks/${id}/cancel`, { method: "POST" });
}

export function deleteRecording(id: string) {
  return request<{ message: string }>(`/api/recordings/${id}`, { method: "DELETE" });
}

export function deleteRecordingsBatch(recordingIds: string[]) {
  return request<{ deleted: string[]; missing: string[] }>("/api/recordings/batch-delete", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

export function updateRecordingTags(id: string, tags: string[]) {
  return request<Recording>(`/api/recordings/${id}/tags`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tags })
  });
}

export function updateTranscriptSegment(recordingId: string, segmentId: string, text: string) {
  return request<RecordingDetail["segments"][number]>(`/api/recordings/${recordingId}/segments/${segmentId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text })
  });
}

export function deleteSummary(id: string) {
  return request<{ message: string }>(`/api/summaries/${id}`, { method: "DELETE" });
}

export interface PickFolderResult {
  path: string;
}

export function pickFolder(): Promise<PickFolderResult> {
  return request<PickFolderResult>("/api/pick-folder", { method: "POST" });
}
