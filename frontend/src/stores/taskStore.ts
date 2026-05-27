import { create } from "zustand";
import type { TaskEvent } from "../lib/sse";

export interface TaskState {
  taskId: string;
  recordingId: string;
  status: "started" | "processing" | "completed" | "failed";
  progress: number;
  message: string;
  timestamp: string;
}

interface TaskStore {
  tasks: Record<string, TaskState>;
  activeTaskId: string | null;
  addTask: (event: TaskEvent) => void;
  updateTask: (taskId: string, updates: Partial<TaskState>) => void;
  removeTask: (taskId: string) => void;
  setActiveTaskId: (taskId: string | null) => void;
  clearCompletedTasks: () => void;
}

export const useTaskStore = create<TaskStore>((set) => ({
  tasks: {},
  activeTaskId: null,

  addTask: (event: TaskEvent) =>
    set((state) => ({
      tasks: {
        ...state.tasks,
        [event.task_id]: {
          taskId: event.task_id,
          recordingId: event.recording_id,
          status: event.event_type === "task.started" ? "started" :
                 event.event_type === "task.completed" ? "completed" :
                 event.event_type === "task.failed" ? "failed" : "processing",
          progress: event.progress,
          message: event.message,
          timestamp: event.timestamp,
        },
      },
      activeTaskId: event.task_id,
    })),

  updateTask: (taskId, updates) =>
    set((state) => ({
      tasks: {
        ...state.tasks,
        [taskId]: {
          ...state.tasks[taskId],
          ...updates,
        },
      },
    })),

  removeTask: (taskId) =>
    set((state) => {
      const { [taskId]: _, ...rest } = state.tasks;
      return {
        tasks: rest,
        activeTaskId: state.activeTaskId === taskId ? null : state.activeTaskId,
      };
    }),

  setActiveTaskId: (taskId) => set({ activeTaskId: taskId }),

  clearCompletedTasks: () =>
    set((state) => ({
      tasks: Object.fromEntries(
        Object.entries(state.tasks).filter(
          ([_, task]) => task.status !== "completed" && task.status !== "failed"
        )
      ),
    })),
}));
