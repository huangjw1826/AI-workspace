/**
 * AI Recorder 前端 API 客户端
 *
 * 封装所有后端 API 调用，统一错误处理和响应类型。
 * API Base URL 自动检测：Vite 开发模式使用 localhost:8000，生产模式使用同源。
 */

import type {
  ApiToken,
  ApiTokenUpdate,
  HealthStatus,
  LlmConnectivityResult,
  LlmSettings,
  LlmSettingsUpdate,
  Recording,
  RecordingDetail,
  SearchResult,
  StorageMigrationPreview,
  StorageMigrationResult,
  StorageSettings,
  SummaryTemplate,
  Task,
  WatchEvent,
  WatchSettings
} from "./types";
import { getApiBaseUrl, sanitizeFilename } from "./utils";

const API_BASE_URL = getApiBaseUrl();

/** 拼接完整 API URL */
export function apiUrl(path: string) {
  if (!path.startsWith("/")) {
    throw new Error(`Invalid API path: ${path}`);
  }
  const sanitizedPath = path.replace(/\/{2,}/g, "/").replace(/\/\.\.(\/|$)/g, "/");
  return `${API_BASE_URL}${sanitizedPath}`;
}

/** 通用请求封装：自动 JSON 解析和错误处理 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  try {
    const response = await fetch(apiUrl(path), { ...init, headers });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json() as Promise<T>;
  } catch (error) {
    if (error instanceof TypeError && (error.message.includes("Failed to fetch") || error.message.includes("NetworkError"))) {
      throw new Error("网络错误：无法连接到服务器，请检查后端服务是否运行");
    }
    throw error;
  }
}

/** 从 Content-Disposition 响应头解析文件名（支持 UTF-8 和 ASCII 编码） */
function filenameFromDisposition(disposition: string | null, fallback: string) {
  if (!disposition) return fallback;

  let filename = fallback;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      filename = decodeURIComponent(utf8Match[1].replace(/"/g, ""));
    } catch {
      filename = utf8Match[1].replace(/"/g, "");
    }
  } else {
    const asciiMatch = disposition.match(/filename="?([^";]+)"?/i);
    if (asciiMatch?.[1]) {
      filename = asciiMatch[1];
    }
  }

  return sanitizeFilename(filename) || fallback;
}

/**
 * 触发浏览器下载文件（Blob 方式）
 * @param path API 路径
 * @param fallbackName 无法从响应头获取文件名时的回退名称
 */
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

// =====================================================================
// 健康检查
// =====================================================================

/** 获取系统健康状态 */
export function getHealth() {
  return request<HealthStatus>("/health");
}

// =====================================================================
// LLM 设置
// =====================================================================

/** 获取 LLM 当前设置 */
export function getLlmSettings() {
  return request<LlmSettings>("/api/settings/llm");
}

/** 更新 LLM 设置 */
export function updateLlmSettings(settings: LlmSettingsUpdate) {
  return request<LlmSettings>("/api/settings/llm", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

/** 测试 LLM 连通性（发送简短请求验证 API Key 和地址） */
export function testLlmConnectivity() {
  return request<LlmConnectivityResult>("/api/settings/llm/test", { method: "POST" });
}

// =====================================================================
// 目录监控设置
// =====================================================================

/** 获取目录监控设置 */
export function getWatchSettings() {
  return request<WatchSettings>("/api/settings/watch");
}

/** 更新目录监控设置 */
export function updateWatchSettings(settings: WatchSettings) {
  return request<WatchSettings>("/api/settings/watch", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

/** 手动触发目录扫描 */
export function scanWatchDirectory() {
  return request<{ count: number; events: WatchEvent[] }>("/api/watch/scan", { method: "POST" });
}

/** 获取监控事件列表 */
export function listWatchEvents() {
  return request<WatchEvent[]>("/api/watch/events");
}

// =====================================================================
// 摘要
// =====================================================================

/** 获取可用摘要模板列表 */
export function listSummaryTemplates() {
  return request<SummaryTemplate[]>("/api/summary/templates");
}

// =====================================================================
// 存储设置
// =====================================================================

/** 获取存储目录设置 */
export function getStorageSettings() {
  return request<StorageSettings>("/api/settings/storage");
}

/** 更新存储目录设置 */
export function updateStorageSettings(settings: Pick<StorageSettings, "data_dir" | "transcript_dir" | "summary_dir">) {
  return request<StorageSettings>("/api/settings/storage", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings)
  });
}

/** 预览数据目录迁移差异 */
export function previewStorageMigration(newDataDir: string) {
  return request<StorageMigrationPreview>(`/api/settings/storage/migration-preview?new_data_dir=${encodeURIComponent(newDataDir)}`);
}

/** 执行数据目录迁移 */
export function migrateStorage(dataDir: string) {
  return request<StorageMigrationResult>("/api/settings/storage/migrate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data_dir: dataDir }),
  });
}

// =====================================================================
// 录音管理
// =====================================================================

/**
 * 获取录音列表，支持搜索和标签筛选
 * @param query 搜索关键词（匹配文件名、标签、转写、摘要内容）
 * @param tag 按标签筛选
 */
export function listRecordings(query = "", tag = "") {
  const params = new URLSearchParams();
  if (query.trim()) params.set("query", query.trim());
  if (tag.trim()) params.set("tag", tag.trim());
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return request<SearchResult>(`/api/recordings${suffix}`);
}

/** 获取录音详情（含转写、摘要、任务） */
export function getRecording(id: string) {
  return request<RecordingDetail>(`/api/recordings/${id}`);
}

/** 上传音频文件（最大 500MB） */
export function uploadRecording(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return request<Recording>("/api/recordings", {
    method: "POST",
    body: formData
  });
}

/** 删除录音（级联删除关联的转写、摘要、任务） */
export function deleteRecording(id: string) {
  return request<{ message: string }>(`/api/recordings/${id}`, { method: "DELETE" });
}

/** 批量删除录音 */
export function deleteRecordingsBatch(recordingIds: string[]) {
  return request<{ deleted: string[]; missing: string[] }>("/api/recordings/batch-delete", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

/** 更新录音标签 */
export function updateRecordingTags(id: string, tags: string[]) {
  return request<Recording>(`/api/recordings/${id}/tags`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tags })
  });
}

/** 编辑转写片段的文本内容 */
export function updateTranscriptSegment(recordingId: string, segmentId: string, text: string) {
  return request<RecordingDetail["segments"][number]>(`/api/recordings/${recordingId}/segments/${segmentId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text })
  });
}

// =====================================================================
// 任务管理
// =====================================================================

/** 发起单个录音转写 */
export function startTranscription(id: string) {
  return request<Task>(`/api/transcribe/${id}`, { method: "POST" });
}

/** 批量发起转写 */
export function startTranscriptionBatch(recordingIds: string[]) {
  return request<Task[]>("/api/transcribe/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

/**
 * 发起摘要生成
 * @param id 录音 ID
 * @param mode 摘要模板（默认 "summary"）
 */
export function startSummary(id: string, mode = "summary") {
  return request<Task>(`/api/summary/${id}?mode=${encodeURIComponent(mode)}`, { method: "POST" });
}

/** 批量发起摘要 */
export function startSummaryBatch(recordingIds: string[], mode = "summary") {
  return request<Task[]>(`/api/summary/batch?mode=${encodeURIComponent(mode)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recording_ids: recordingIds })
  });
}

/** 获取任务状态 */
export function getTask(id: string) {
  return request<Task>(`/api/tasks/${id}`);
}

/** 取消任务 */
export function cancelTask(id: string) {
  return request<Task>(`/api/tasks/${id}/cancel`, { method: "POST" });
}

// =====================================================================
// 摘要管理
// =====================================================================

/** 删除单个摘要 */
export function deleteSummary(id: string) {
  return request<{ message: string }>(`/api/summaries/${id}`, { method: "DELETE" });
}

// =====================================================================
// 文件夹选择（PC 端原生对话框）
// =====================================================================

/** 文件夹选择（PC 端原生对话框） */

export interface PickFolderResult {
  path: string;
  cancelled?: boolean;
  error?: boolean;
}

/** 弹出 Windows 原生文件夹选择对话框 */
export function pickFolder(): Promise<PickFolderResult> {
  return request<PickFolderResult>("/api/pick-folder", { method: "POST" });
}

// =====================================================================
// API Token 管理
// =====================================================================

/** 创建新 Token（返回完整 Token 仅一次） */
export function createApiToken(data: { name: string; device_info?: string }) {
  return request<ApiToken>("/api/tokens", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

/** 列出所有 Token（已掩码） */
export function listApiTokens() {
  return request<ApiToken[]>("/api/tokens");
}

/** 更新 Token（名称/启用状态） */
export function updateApiToken(id: string, data: ApiTokenUpdate) {
  return request<ApiToken>(`/api/tokens/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

/** 删除/撤销 Token */
export function deleteApiToken(id: string) {
  return request<{ message: string }>(`/api/tokens/${id}`, {
    method: "DELETE",
  });
}
