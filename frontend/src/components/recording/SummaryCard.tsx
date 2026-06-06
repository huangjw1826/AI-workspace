import { ChevronDown, Trash2 } from "lucide-react";
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
    <article className={`summary-card${expanded ? " expanded" : ""}`}>
      <div className="summary-card-header" onClick={onToggle}>
        <div className="summary-card-header-left">
          <strong>{templateName}</strong>
          <span className="summary-card-meta">
            {formatShortDate(summary.created_at)} · 约 {summary.content.length} 字
            {isLatest && <mark className="latest-badge">最新</mark>}
          </span>
        </div>
        <div className="summary-card-header-right">
          <ExportButtons onExport={onExport} formats={["md", "txt", "docx"]} />
          <button
            className="btn btn-icon danger"
            disabled={busy}
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
            title="删除摘要"
          >
            <Trash2 size={14} />
          </button>
          <ChevronDown
            size={16}
            className="summary-chevron"
            style={{ transform: expanded ? "rotate(180deg)" : undefined }}
          />
        </div>
      </div>
      {!expanded && (
        <p className="summary-preview">
          <span>预览</span>
          {summaryPreview(summary.content) || "点击展开查看摘要正文"}
        </p>
      )}
      {expanded && (
        <div className="summary-card-body">
          <MarkdownView content={summary.content} />
        </div>
      )}
    </article>
  );
}
