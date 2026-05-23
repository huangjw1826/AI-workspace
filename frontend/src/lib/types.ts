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
  source_type: string;
  source_path?: string;
  tags: string;
  status: string;
  error_message?: string;
  created_at: string;
  updated_at: string;
}

export interface Task {
  id: string;
  recording_id: string;
  task_type: string;
  status: string;
  progress: number;
  error_message?: string;
  result_path?: string;
  completed_at?: string;
}

export interface TranscriptSegment {
  id: string;
  recording_id: string;
  start_time: number;
  end_time: number;
  speaker: string;
  text: string;
  sequence: number;
}

export interface Summary {
  id: string;
  recording_id: string;
  mode: string;
  content: string;
  created_at: string;
}

export interface RecordingDetail {
  recording: Recording;
  segments: TranscriptSegment[];
  summaries: Summary[];
  tasks: Task[];
}

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

export interface LlmProviderDefault {
  base_url: string;
  model: string;
  temperature?: number;
  top_p?: number | null;
}

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

export interface LlmConnectivityResult {
  ok: boolean;
  provider: string;
  base_url: string;
  model: string;
  latency_ms?: number;
  message: string;
}

export interface WatchSettings {
  enabled: boolean;
  watch_dir: string;
  recursive: boolean;
  interval_seconds: number;
  exists: boolean;
}

export interface StorageSettings {
  transcript_dir: string;
  summary_dir: string;
  transcript_exists: boolean;
  summary_exists: boolean;
}

export interface WatchEvent {
  id: string;
  file_path: string;
  filename: string;
  status: string;
  reason?: string;
  recording_id?: string;
  duplicate_of_id?: string;
  content_hash?: string;
  file_size?: number;
  file_mtime?: number;
  created_at: string;
}

export interface SummaryTemplate {
  id: string;
  name: string;
  description: string;
}

export type ExportFormat = "md" | "txt" | "json" | "srt" | "docx";
