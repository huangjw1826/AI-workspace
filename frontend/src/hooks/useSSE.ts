import { useEffect } from "react";
import { connectSSE, disconnectSSE, addSSEListener, type TaskEvent } from "../lib/sse";
import { useTaskStore } from "../stores/taskStore";
import { useAppStore } from "../stores/appStore";

/**
 * Single SSE hook — connects, updates task store, and shows toasts.
 * App.tsx calls this once; no separate SSE setup needed.
 */
export function useSSE(onRecordingCreated?: (recordingId: string, filename: string) => void) {
  const addTask = useTaskStore((s) => s.addTask);
  const updateTask = useTaskStore((s) => s.updateTask);
  const showToast = useAppStore((s) => s.showToast);

  useEffect(() => {
    connectSSE();

    const unsub = addSSEListener((event: TaskEvent) => {
      if (event.event_type === "recording.created") {
        showToast(event.message, "success");
        onRecordingCreated?.(event.recording_id, event.message);
        return;
      }

      addTask(event);

      switch (event.event_type) {
        case "task.started":
          showToast(`任务开始: ${event.message}`, "info");
          break;
        case "task.completed":
          showToast(`任务完成: ${event.message}`, "success");
          updateTask(event.task_id, { status: "completed" });
          break;
        case "task.failed":
          showToast(`任务失败: ${event.message}`, "error");
          updateTask(event.task_id, { status: "failed" });
          break;
        case "task.progress":
          if (event.progress > 0 && event.progress < 100) {
            // silent progress — just update store, no toast spam
          }
          break;
      }
    });

    return () => {
      unsub();
      disconnectSSE();
    };
  }, [addTask, updateTask, showToast, onRecordingCreated]);
}
