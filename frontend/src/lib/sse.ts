/**
 * SSE (Server-Sent Events) 客户端模块
 *
 * 管理前端到后端的 SSE 连接，实现任务进度的实时推送接收。
 * 支持：
 * - 自动重连（指数退避策略，1 秒起步，最大 30 秒间隔）
 * - 多监听器注册（多个组件可同时接收事件）
 * - 连接状态管理
 * - 事件类型分发（task.started / task.progress / task.completed / task.failed）
 *
 * 使用方式：
 * ```ts
 * import { connectSSE, disconnectSSE, addSSEListener } from "../lib/sse";
 *
 * connectSSE();
 * const removeListener = addSSEListener((event) => {
 *   console.log("收到事件:", event);
 * });
 * // 清理
 * removeListener();
 * disconnectSSE();
 * ```
 */

/** SSE 事件类型 - 与后端 TaskEventType 一一对应 */
export type TaskEventType =
  | "task.started"
  | "task.processing"
  | "task.progress"
  | "task.completed"
  | "task.failed"
  | "task.cancelled"
  | "heartbeat";

/** SSE 事件数据结构 - 对应后端 TaskEvent */
export interface TaskEvent {
  event_id: string;
  event_type: TaskEventType;
  task_id: string;
  recording_id: string;
  progress: number;
  message: string;
  timestamp: string;
  data?: {
    result_path?: string;
    error?: string;
  };
}

/** SSE 客户端配置选项 */
export interface SSEClientOptions {
  onOpen?: () => void;
  onError?: (error: Event) => void;
  onTaskStarted?: (event: TaskEvent) => void;
  onTaskProgress?: (event: TaskEvent) => void;
  onTaskCompleted?: (event: TaskEvent) => void;
  onTaskFailed?: (event: TaskEvent) => void;
  /** 初始重连间隔（毫秒），默认 1000 */
  reconnectInterval?: number;
  /** 最大重连间隔（毫秒），默认 30000 */
  maxReconnectInterval?: number;
}

/** 默认配置常量 */
const DEFAULT_OPTIONS: Required<SSEClientOptions> = {
  onOpen: () => {},
  onError: () => {},
  onTaskStarted: () => {},
  onTaskProgress: () => {},
  onTaskCompleted: () => {},
  onTaskFailed: () => {},
  reconnectInterval: 1000,
  maxReconnectInterval: 30000,
};

/**
 * SSE 客户端类
 *
 * 封装 EventSource API，提供自动重连和事件分发能力。
 * 使用指数退避策略进行重连：1s, 2s, 4s, 8s, ..., 最大 30s
 */
class SSEClient {
  private eventSource: EventSource | null = null;
  private options: Required<SSEClientOptions>;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isConnecting = false;
  private currentUrl = "";

  constructor(options: SSEClientOptions = {}) {
    this.options = { ...DEFAULT_OPTIONS, ...options };
  }

  /**
   * 连接到 SSE 端点
   * 幂等操作：如果已有连接则跳过。
   * 注册事件监听器并按事件类型分发。
   *
   * @param url SSE 端点的完整 URL
   */
  connect(url: string): void {
    if (this.eventSource || this.isConnecting) {
      return;
    }

    this.currentUrl = url;
    this.isConnecting = true;

    try {
      this.eventSource = new EventSource(url);

      // 连接建立事件
      this.eventSource.onopen = () => {
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        this.options.onOpen();
      };

      // 连接错误/断开事件
      this.eventSource.onerror = (error) => {
        this.isConnecting = false;
        this.options.onError(error);

        if (this.eventSource?.readyState === EventSource.CLOSED) {
          this.scheduleReconnect();
        }
      };

      // 任务开始事件
      this.eventSource.addEventListener("task.started", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskStarted(event);
        } catch {
          console.error("解析 task.started 事件失败");
        }
      });

      // 任务进度事件
      this.eventSource.addEventListener("task.progress", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskProgress(event);
        } catch {
          console.error("解析 task.progress 事件失败");
        }
      });

      // 任务完成事件
      this.eventSource.addEventListener("task.completed", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskCompleted(event);
        } catch {
          console.error("解析 task.completed 事件失败");
        }
      });

      // 任务失败事件
      this.eventSource.addEventListener("task.failed", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskFailed(event);
        } catch {
          console.error("解析 task.failed 事件失败");
        }
      });

    } catch (error) {
      this.isConnecting = false;
      console.error("创建 EventSource 失败:", error);
      this.scheduleReconnect();
    }
  }

  /**
   * 安排自动重连（指数退避）
   * 每次重连后重连间隔翻倍，直到达到最大间隔。
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer || this.isConnecting) {
      return;
    }

    const delay = Math.min(
      this.options.reconnectInterval * Math.pow(2, this.reconnectAttempts),
      this.options.maxReconnectInterval
    );

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.reconnectAttempts++;
      this.disconnect();
      this.connect(this.currentUrl);
    }, delay);
  }

  /** 断开 SSE 连接并清理所有定时器 */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    this.isConnecting = false;
    this.reconnectAttempts = 0;
  }

  /** 检查连接是否已建立 */
  isConnected(): boolean {
    return this.eventSource !== null && this.eventSource.readyState === EventSource.OPEN;
  }
}

// =====================================================================
// 全局 SSE 客户端单例
// =====================================================================

/** 全局 SSE 客户端实例 */
let globalClient: SSEClient | null = null;
/** 全局事件监听器集合 */
const listeners = new Set<(event: TaskEvent) => void>();

import { getApiBaseUrl } from "./utils";

/** 获取 SSE 端点的完整 URL */
export function getSSEUrl() {
  return `${getApiBaseUrl()}/api/events`;
}

/**
 * 初始化全局 SSE 客户端（懒加载单例）
 * 仅在首次调用时创建客户端实例。
 */
export function initSSEClient(): SSEClient {
  if (globalClient) {
    return globalClient;
  }

  globalClient = new SSEClient({
    onOpen: () => {
      console.log("SSE 已连接");
    },
    onError: (error) => {
      console.error("SSE 错误:", error);
    },
    onTaskStarted: (event) => {
      listeners.forEach((listener) => listener(event));
    },
    onTaskProgress: (event) => {
      listeners.forEach((listener) => listener(event));
    },
    onTaskCompleted: (event) => {
      listeners.forEach((listener) => listener(event));
    },
    onTaskFailed: (event) => {
      listeners.forEach((listener) => listener(event));
    },
  });

  return globalClient;
}

/** 连接 SSE 并返回客户端实例 */
export function connectSSE(): SSEClient {
  const client = initSSEClient();
  client.connect(getSSEUrl());
  return client;
}

/** 断开 SSE 连接并释放全局客户端 */
export function disconnectSSE(): void {
  if (globalClient) {
    globalClient.disconnect();
    globalClient = null;
  }
}

/**
 * 添加 SSE 事件监听器
 * @param callback 事件回调函数
 * @returns 移除监听器的清理函数
 */
export function addSSEListener(callback: (event: TaskEvent) => void): () => void {
  listeners.add(callback);
  return () => {
    listeners.delete(callback);
  };
}

export type { SSEClient };
