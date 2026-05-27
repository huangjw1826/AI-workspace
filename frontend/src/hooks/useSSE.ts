import { useEffect } from "react";
import { useTaskStore } from "../stores/taskStore";
import { addSSEListener, type TaskEvent } from "../lib/sse";

export function useSSE() {
  const addTask = useTaskStore((state) => state.addTask);
  const updateTask = useTaskStore((state) => state.updateTask);

  useEffect(() => {
    const unsubscribe = addSSEListener((event: TaskEvent) => {
      addTask(event);

      if (event.event_type === "task.completed" || event.event_type === "task.failed") {
        setTimeout(() => {
          updateTask(event.task_id, {
            status: event.event_type === "task.completed" ? "completed" : "failed",
          });
        }, 3000);
      }
    });

    return () => {
      unsubscribe();
    };
  }, [addTask, updateTask]);
}
