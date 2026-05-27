import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  listRecordings,
  getRecording,
  startTranscription,
  startSummary,
  deleteRecording,
  updateRecordingTags,
} from "../lib/api";
import type { Recording, RecordingDetail } from "../lib/types";
import type { LibraryFilters } from "../lib/viewTypes";

export function useRecordings(filters: LibraryFilters = { query: "", statuses: [], sources: [], tag: "" }) {
  return useQuery({
    queryKey: ["recordings", filters],
    queryFn: () => listRecordings(filters.query, filters.tag),
  });
}

export function useRecording(id: string | null) {
  return useQuery({
    queryKey: ["recording", id],
    queryFn: () => (id ? getRecording(id) : null),
    enabled: !!id,
  });
}

export function useStartTranscription() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ recordingId }: { recordingId: string }) =>
      startTranscription(recordingId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["recording", variables.recordingId] });
      queryClient.invalidateQueries({ queryKey: ["recordings"] });
    },
  });
}

export function useStartSummary() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ recordingId, mode }: { recordingId: string; mode: string }) =>
      startSummary(recordingId, mode),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["recording", variables.recordingId] });
      queryClient.invalidateQueries({ queryKey: ["recordings"] });
    },
  });
}

export function useDeleteRecording() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => deleteRecording(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recordings"] });
    },
  });
}

export function useUpdateRecordingTags() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ recordingId, tags }: { recordingId: string; tags: string[] }) =>
      updateRecordingTags(recordingId, tags),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["recording", variables.recordingId] });
      queryClient.invalidateQueries({ queryKey: ["recordings"] });
    },
  });
}
