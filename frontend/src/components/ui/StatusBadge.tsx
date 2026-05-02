import { CheckCircle2, Clock3, Loader2, XCircle } from "lucide-react";
import { statusLabel } from "../../lib/format";

function statusClass(status: string) {
  if (["queued", "normalizing", "transcribing"].includes(status)) return "status-badge running";
  if (status === "transcribed") return "status-badge transcribed";
  if (status === "completed") return "status-badge completed";
  if (status === "error") return "status-badge error";
  return "status-badge pending";
}

function StatusIcon({ status }: { status: string }) {
  if (["queued", "normalizing", "transcribing"].includes(status)) return <Loader2 className="spin" size={12} />;
  if (status === "completed" || status === "transcribed") return <CheckCircle2 size={12} />;
  if (status === "error") return <XCircle size={12} />;
  return <Clock3 size={12} />;
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <mark className={statusClass(status)}>
      <StatusIcon status={status} />
      {statusLabel(status)}
    </mark>
  );
}

