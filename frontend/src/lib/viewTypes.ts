export type View = "library" | "watch" | "settings" | "health";
export type DetailTab = "transcript" | "summary" | "info";
export type SortKey = "created_desc" | "created_asc" | "duration_desc" | "size_desc";

export type LibraryFilters = {
  query: string;
  statuses: string[];
  sources: string[];
};
