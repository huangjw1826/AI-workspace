/**
 * AI Recorder 前端类型定义
 *
 * 本文件定义前端所有数据模型接口，与后端 FastAPI 响应结构一一对应。
 * 类型按功能域分组，标注 API 响应或前端内部使用。
 */

// =====================================================================
// 核心数据模型（对应后端 SQLModel 实体）
// =====================================================================

/** 录音记录 - 对应后端 Recording 模型 */
export interface Recording {
  id: string;
  filename: string;
  original_path: string;
  normalized_path?: string;
  duration_seconds?: number;
  file_size_bytes?: number;
  source_mtime?: number;
  format: string;
  content_hash?: string;
  /** 来源：upload（手动上传）| watch（目录监控） */
  source_type: string;
  source_path?: string;
  /** 标签，逗号分隔 */
  tags: string;
  /** 状态：uploaded | queued | normalizing | transcribing | transcribed | completed | error */
  status: string;
  error_message?: string;
  created_at: string;
  updated_at: string;
}

/** 异步任务 - 对应后端 Task 模型 */
export interface Task {
  id: string;
  recording_id: string;
  /** 任务类型：transcription 或 summary:<mode> */
  task_type: string;
  /** 状态：queued | running | completed | error | cancelled */
  status: string;
  /** 进度百分比 0-100 */
  progress: number;
  error_message?: string;
  result_path?: string;
  completed_at?: string;
}

/** 转写片段 - 对应后端 TranscriptSegment 模型 */
export interface TranscriptSegment {
  id: string;
  recording_id: string;
  /** 开始时间（秒） */
  start_time: number;
  /** 结束时间（秒） */
  end_time: number;
  speaker: string;
  text: string;
  /** 排序序号 */
  sequence: number;
}

/** 摘要记录 - 对应后端 Summary 模型 */
export interface Summary {
  id: string;
  recording_id: string;
  /** 摘要模板标识 */
  mode: string;
  /** Markdown 格式正文 */
  content: string;
  created_at: string;
}

/** 录音详情 - GET /api/recordings/{id} 的完整响应 */
export interface RecordingDetail {
  recording: Recording;
  segments: TranscriptSegment[];
  summaries: Summary[];
  tasks: Task[];
}

// =====================================================================
// 健康检查
// =====================================================================

/** 系统健康状态 - GET /health */
export interface HealthStatus {
  status: string;
  python: string;
  ffmpeg: boolean;
  funasr: boolean;
  data_dir: string;
  model_dir: string;
  asr_model: string;
  llm_provider: string;
  llm_base_url: string;
  llm_model: string;
  llm_configured: boolean;
  log_dir?: string;
  recent_errors?: string[];
}

// =====================================================================
// LLM 设置相关
// =====================================================================

/** LLM 提供商预设 */
export interface LlmProviderDefault {
  base_url: string;
  model: string;
  temperature?: number;
  top_p?: number | null;
}

/** LLM 当前设置 - GET/PUT /api/settings/llm */
export interface LlmSettings {
  provider: "deepseek" | "tongyi" | "qwen" | "mimo";
  base_url: string;
  model: string;
  configured: boolean;
  api_key_masked: string;
  mimo_thinking: "enabled" | "disabled";
  max_completion_tokens: number;
  temperature?: number | null;
  top_p?: number | null;
  providers: Record<string, LlmProviderDefault>;
}

/** LLM 设置更新请求 */
export interface LlmSettingsUpdate {
  provider: "deepseek" | "tongyi" | "qwen" | "mimo";
  api_key?: string;
  base_url?: string;
  model?: string;
  mimo_thinking?: "enabled" | "disabled";
  max_completion_tokens?: number;
  temperature?: number | null;
  top_p?: number | null;
}

/** LLM 连通性测试结果 - POST /api/settings/llm/test */
export interface LlmConnectivityResult {
  ok: boolean;
  provider: string;
  base_url: string;
  model: string;
  latency_ms?: number;
  message: string;
}

// =====================================================================
// 设置相关
// =====================================================================

/** 目录监控设置 - GET/PUT /api/settings/watch */
export interface WatchSettings {
  enabled: boolean;
  watch_dir: string;
  recursive: boolean;
  interval_seconds: number;
  exists: boolean;
}

/** 存储目录设置 - GET/PUT /api/settings/storage */
export interface StorageSettings {
  transcript_dir: string;
  summary_dir: string;
  transcript_exists: boolean;
  summary_exists: boolean;
}

// =====================================================================
// 目录监控
// =====================================================================

/** 监控事件 - GET /api/watch/events */
export interface WatchEvent {
  id: string;
  file_path: string;
  filename: string;
  /** 状态：imported | duplicate_skipped | skipped | error */
  status: string;
  reason?: string;
  recording_id?: string;
  duplicate_of_id?: string;
  content_hash?: string;
  file_size?: number;
  file_mtime?: number;
  created_at: string;
}

// =====================================================================
// 摘要
// =====================================================================

/** 摘要模板 - GET /api/summary/templates */
export interface SummaryTemplate {
  id: string;
  name: string;
  description: string;
}

// =====================================================================
// 导出类型
// =====================================================================

/** 支持的导出格式 */
export type ExportFormat = "md" | "txt" | "json" | "srt" | "docx";

// =====================================================================
// 搜索结果
// =====================================================================

/** 录音搜索响应 - GET /api/recordings?query=&tag= */
export interface SearchResult {
  recordings: Recording[];
  /** 搜索命中预览，key 为录音 ID，value 为匹配片段数组 */
  match_previews: Record<string, string[]>;
}
