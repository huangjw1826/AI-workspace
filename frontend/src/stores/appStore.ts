import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { RecordingDetail } from "../lib/types";

export interface AppState {
  sidebarCollapsed: boolean;
  currentRecordingId: string | null;
  playbackPosition: Record<string, number>;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setCurrentRecordingId: (id: string | null) => void;
  setPlaybackPosition: (recordingId: string, position: number) => void;
  getPlaybackPosition: (recordingId: string) => number;
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      currentRecordingId: null,
      playbackPosition: {},

      toggleSidebar: () =>
        set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),

      setSidebarCollapsed: (collapsed) =>
        set({ sidebarCollapsed: collapsed }),

      setCurrentRecordingId: (id) =>
        set({ currentRecordingId: id }),

      setPlaybackPosition: (recordingId, position) =>
        set((state) => ({
          playbackPosition: {
            ...state.playbackPosition,
            [recordingId]: position,
          },
        })),

      getPlaybackPosition: (recordingId) => {
        return get().playbackPosition[recordingId] ?? 0;
      },
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
