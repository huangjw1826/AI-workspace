import { create } from "zustand";
import { persist } from "zustand/middleware";

// ---------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------
export type ToastTone = "info" | "success" | "error";

export interface ToastMessage {
  id: number;
  message: string;
  tone: ToastTone;
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------
export interface AppState {
  // persisted
  sidebarCollapsed: boolean;
  playbackPosition: Record<string, number>;

  // transient (not persisted)
  busy: boolean;
  error: string;
  toasts: ToastMessage[];

  // actions
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setPlaybackPosition: (recordingId: string, position: number) => void;
  getPlaybackPosition: (recordingId: string) => number;

  setBusy: (busy: boolean) => void;
  setError: (error: string) => void;
  showToast: (message: string, tone?: ToastTone) => void;
  dismissToast: (id: number) => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      // persisted
      sidebarCollapsed: false,
      playbackPosition: {},

      // transient
      busy: false,
      error: "",
      toasts: [],

      // persisted actions
      toggleSidebar: () =>
        set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),

      setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),

      setPlaybackPosition: (recordingId, position) =>
        set((s) => ({
          playbackPosition: { ...s.playbackPosition, [recordingId]: position },
        })),

      getPlaybackPosition: (recordingId) =>
        get().playbackPosition[recordingId] ?? 0,

      // transient actions
      setBusy: (busy) => set({ busy }),
      setError: (error) => set({ error }),

      showToast: (message, tone = "info") => {
        const id = Date.now() + Math.random();
        set((s) => ({ toasts: [...s.toasts, { id, message, tone }] }));
        setTimeout(() => {
          set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
        }, 2600);
      },

      dismissToast: (id) =>
        set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
    }),
    {
      name: "ai-recorder-app-storage",
      partialize: (state) => ({
        sidebarCollapsed: state.sidebarCollapsed,
        playbackPosition: state.playbackPosition,
      }),
    }
  )
);
