import { ChevronDown, ChevronUp, Trash2 } from "lucide-react";
import { formatShortDate, summaryPreview } from "../../lib/format";
import type { ExportFormat, Summary } from "../../lib/types";
import { MarkdownView } from "../markdown/MarkdownView";
import { ExportButtons } from "../ui/ExportButtons";

export function SummaryCard({
  summary,
  templateName,
  isLatest,
  expanded,
  busy,
  onToggle,
  onExport,
  onDelete,
}: {
  summary: Summary;
  templateName: string;
  isLatest: boolean;
  expanded: boolean;
  busy: boolean;
  onToggle: () => void;
  onExport: (format: ExportFormat) => void;
  onDelete: () => void;
}) {
  return (
    <article className={expanded ? "summary-card expanded" : "summary-card"}>
      <div className="summary-card-top">
        <button className="summary-toggle" onClick={onToggle} aria-expanded={expanded}>
          {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          <span>
            <strong>{templateName}</strong>
            <em>{formatShortDate(summary.created_at)} · 约 {summary.content.length} 字</em>
          </span>
          {isLatest && <mark className="latest-badge">最新</mark>}
        </button>
        <div className="summary-actions">
          <ExportButtons onExport={onExport} formats={["md", "txt", "docx"]} />
          <button className="icon-danger" disabled={busy} onClick={onDelete} title="删除摘要" aria-label="删除摘要">
            <Trash2 size={14} />
          </button>
        </div>
      </div>
      {!expanded && (
        <p className="summary-preview">
          <span>预览</span>
          {summaryPreview(summary.content) || "点击展开查看摘要正文"}
        </p>
      )}
      {expanded && <MarkdownView content={summary.content} />}
    </article>
  );
}
