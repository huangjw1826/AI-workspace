/**
 * SSE (Server-Sent Events) — lightweight functional client.
 *
 * EventSource has built-in auto-reconnect, so we don't replicate it.
 * A simple listener-pattern is all we need for the single /api/events endpoint.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type TaskEventType =
  | "task.started"
  | "task.processing"
  | "task.progress"
  | "task.completed"
  | "task.failed"
  | "task.cancelled"
  | "recording.created"
  | "heartbeat";

export interface TaskEvent {
  event_id: string;
  event_type: TaskEventType;
  task_id: string;
  recording_id: string;
  progress: number;
  message: string;
  timestamp: string;
  data?: { result_path?: string; error?: string };
}

export type TaskEventListener = (event: TaskEvent) => void;

// ---------------------------------------------------------------------------
// Singleton connection
// ---------------------------------------------------------------------------

let es: EventSource | null = null;
const listeners = new Set<TaskEventListener>();

function parseEvent(e: MessageEvent): TaskEvent | null {
  try {
    return JSON.parse(e.data) as TaskEvent;
  } catch {
    return null;
  }
}

function onMessage(e: MessageEvent) {
  const event = parseEvent(e);
  if (event) listeners.forEach((fn) => fn(event));
}

export function connectSSE(url = "/api/events") {
  if (es) return;
  es = new EventSource(url);
  // EventSource auto-reconnects by default — no manual logic needed
  es.addEventListener("task.started", onMessage);
  es.addEventListener("task.progress", onMessage);
  es.addEventListener("task.completed", onMessage);
  es.addEventListener("task.failed", onMessage);
  es.addEventListener("recording.created", onMessage);
}

export function disconnectSSE() {
  if (es) {
    es.close();
    es = null;
  }
}

export function addSSEListener(fn: TaskEventListener): () => void {
  listeners.add(fn);
  return () => { listeners.delete(fn); };
}
