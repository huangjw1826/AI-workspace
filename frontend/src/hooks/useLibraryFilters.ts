import React from "react";
import { listRecordings } from "../lib/api";
import type { Recording } from "../lib/types";
import type { LibraryFilters, SortKey } from "../lib/viewTypes";

const EMPTY_FILTERS: LibraryFilters = { query: "", statuses: [], sources: [], tag: "" };

export function useLibraryFilters() {
  const [recordings, setRecordings] = React.useState<Recording[]>([]);
  const [searchMatchPreviews, setSearchMatchPreviews] = React.useState<Record<string, string[]>>({});
  const [draftFilters, setDraftFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = React.useState<LibraryFilters>(EMPTY_FILTERS);
  const [sortKey, setSortKey] = React.useState<SortKey>("created_desc");

  const reloadList = React.useCallback((query: string, tag: string) =>
    listRecordings(query, tag).then((r) => {
      setRecordings(r.recordings);
      setSearchMatchPreviews(r.match_previews);
    }), []);

  const applyFilters = React.useCallback(() => {
    const next = { query: draftFilters.query, statuses: [...draftFilters.statuses], sources: [...draftFilters.sources], tag: draftFilters.tag };
    setAppliedFilters(next);
    reloadList(next.query, next.tag);
  }, [draftFilters, reloadList]);

  const resetFilters = React.useCallback(() => {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setSortKey("created_desc");
    reloadList("", "");
  }, [reloadList]);

  const clearAppliedQuery = React.useCallback(() => {
    setDraftFilters((d) => ({ ...d, query: "" }));
    setAppliedFilters((f) => ({ ...f, query: "" }));
    reloadList("", appliedFilters.tag);
  }, [appliedFilters.tag, reloadList]);

  const clearAppliedTag = React.useCallback(() => {
    setDraftFilters((d) => ({ ...d, tag: "" }));
    setAppliedFilters((f) => ({ ...f, tag: "" }));
  }, []);

  const clearAppliedStatus = React.useCallback((status: string) => {
    setDraftFilters((d) => ({ ...d, statuses: d.statuses.filter((s) => s !== status) }));
    setAppliedFilters((f) => ({ ...f, statuses: f.statuses.filter((s) => s !== status) }));
  }, []);

  const clearAppliedSource = React.useCallback((source: string) => {
    setDraftFilters((d) => ({ ...d, sources: d.sources.filter((s) => s !== source) }));
    setAppliedFilters((f) => ({ ...f, sources: f.sources.filter((s) => s !== source) }));
  }, []);

  const filteredRecordings = React.useMemo(() => {
    const q = appliedFilters.query.trim().toLowerCase();
    const tag = appliedFilters.tag.trim().toLowerCase();
    return recordings
      .filter((r) => {
        if (q && !r.filename.toLowerCase().includes(q)) return false;
        if (tag && !r.tags.split(",").some((t) => t.trim().toLowerCase() === tag)) return false;
        if (appliedFilters.statuses.length && !appliedFilters.statuses.includes(r.status)) return false;
        if (appliedFilters.sources.length && !appliedFilters.sources.includes(r.source_type)) return false;
        return true;
      })
      .sort((a, b) => {
        const aTime = (a.source_mtime ?? 0) * 1000 || new Date(a.created_at).getTime();
        const bTime = (b.source_mtime ?? 0) * 1000 || new Date(b.created_at).getTime();
        switch (sortKey) {
          case "created_asc": return aTime - bTime;
          case "duration_desc": return (b.duration_seconds ?? 0) - (a.duration_seconds ?? 0);
          case "size_desc": return (b.file_size_bytes ?? 0) - (a.file_size_bytes ?? 0);
          default: return bTime - aTime;
        }
      });
  }, [recordings, appliedFilters, sortKey]);

  // Compute all unique tags for autocomplete
  const allTags = React.useMemo(() => {
    const tagSet = new Set<string>();
    for (const r of recordings) {
      if (r.tags) {
        r.tags.split(",").forEach((t) => {
          const trimmed = t.trim();
          if (trimmed) tagSet.add(trimmed);
        });
      }
    }
    return Array.from(tagSet).sort();
  }, [recordings]);

  return {
    recordings, setRecordings,
    searchMatchPreviews, setSearchMatchPreviews,
    filteredRecordings,
    allTags,
    draftFilters, setDraftFilters,
    appliedFilters, setAppliedFilters,
    sortKey, setSortKey,
    reloadList,
    applyFilters,
    resetFilters,
    clearAppliedQuery,
    clearAppliedTag,
    clearAppliedStatus,
    clearAppliedSource,
  };
}
