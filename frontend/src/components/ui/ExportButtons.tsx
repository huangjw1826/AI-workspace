import { Download } from "lucide-react";
import type { ExportFormat } from "../../lib/types";

export function ExportButtons({ onExport }: { onExport: (format: ExportFormat) => void }) {
  return (
    <div className="export-buttons">
      <button onClick={() => onExport("md")}><Download size={14} /> MD</button>
      <button onClick={() => onExport("txt")}><Download size={14} /> TXT</button>
    </div>
  );
}

