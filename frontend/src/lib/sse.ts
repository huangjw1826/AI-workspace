export type TaskEventType =
  | "task.started"
  | "task.processing"
  | "task.progress"
  | "task.completed"
  | "task.failed"
  | "task.cancelled"
  | "heartbeat";

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

export interface SSEClientOptions {
  onOpen?: () => void;
  onError?: (error: Event) => void;
  onTaskStarted?: (event: TaskEvent) => void;
  onTaskProgress?: (event: TaskEvent) => void;
  onTaskCompleted?: (event: TaskEvent) => void;
  onTaskFailed?: (event: TaskEvent) => void;
  reconnectInterval?: number;
  maxReconnectInterval?: number;
}

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

  connect(url: string): void {
    if (this.eventSource || this.isConnecting) {
      return;
    }

    this.currentUrl = url;
    this.isConnecting = true;

    try {
      this.eventSource = new EventSource(url);

      this.eventSource.onopen = () => {
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        this.options.onOpen();
      };

      this.eventSource.onerror = (error) => {
        this.isConnecting = false;
        this.options.onError(error);

        if (this.eventSource?.readyState === EventSource.CLOSED) {
          this.scheduleReconnect();
        }
      };

      this.eventSource.addEventListener("task.started", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskStarted(event);
        } catch {
          console.error("Failed to parse task.started event");
        }
      });

      this.eventSource.addEventListener("task.progress", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskProgress(event);
        } catch {
          console.error("Failed to parse task.progress event");
        }
      });

      this.eventSource.addEventListener("task.completed", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskCompleted(event);
        } catch {
          console.error("Failed to parse task.completed event");
        }
      });

      this.eventSource.addEventListener("task.failed", (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data) as TaskEvent;
          this.options.onTaskFailed(event);
        } catch {
          console.error("Failed to parse task.failed event");
        }
      });

    } catch (error) {
      this.isConnecting = false;
      console.error("Failed to create EventSource:", error);
      this.scheduleReconnect();
    }
  }

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

  isConnected(): boolean {
    return this.eventSource !== null && this.eventSource.readyState === EventSource.OPEN;
  }
}

let globalClient: SSEClient | null = null;
const listeners = new Set<(event: TaskEvent) => void>();

import { getApiBaseUrl } from "./utils";

export function getSSEUrl() {
  return `${getApiBaseUrl()}/api/events`;
}

export function initSSEClient(): SSEClient {
  if (globalClient) {
    return globalClient;
  }

  globalClient = new SSEClient({
    onOpen: () => {
      console.log("SSE connected");
    },
    onError: (error) => {
      console.error("SSE error:", error);
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

export function connectSSE(): SSEClient {
  const client = initSSEClient();
  client.connect(getSSEUrl());
  return client;
}

export function disconnectSSE(): void {
  if (globalClient) {
    globalClient.disconnect();
    globalClient = null;
  }
}

export function addSSEListener(callback: (event: TaskEvent) => void): () => void {
  listeners.add(callback);
  return () => {
    listeners.delete(callback);
  };
}

export type { SSEClient };
