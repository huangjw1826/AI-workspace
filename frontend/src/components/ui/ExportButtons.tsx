import { Download } from "lucide-react";
import type { ExportFormat } from "../../lib/types";

const LABELS: Record<ExportFormat, string> = {
  md: "MD",
  txt: "TXT",
  json: "JSON",
  srt: "SRT",
  docx: "DOCX"
};

export function ExportButtons({
  onExport,
  formats = ["md", "txt"]
}: {
  onExport: (format: ExportFormat) => void;
  formats?: ExportFormat[];
}) {
  return (
    <div className="export-buttons">
      {formats.map((format) => (
        <button key={format} onClick={() => onExport(format)}><Download size={14} /> {LABELS[format]}</button>
      ))}
    </div>
  );
}
