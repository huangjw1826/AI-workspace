import { CheckCircle2, Clock3, Loader2, XCircle } from "lucide-react";
import { statusLabel } from "../../lib/format";

function statusClass(status: string): string {
  if (["queued", "normalizing", "transcribing"].includes(status)) return "running";
  if (status === "transcribed") return "transcribed";
  if (status === "completed") return "completed";
  if (status === "error") return "error";
  if (status === "cancelled") return "cancelled";
  return "pending";
}

function StatusIcon({ status }: { status: string }) {
  if (["queued", "normalizing", "transcribing"].includes(status))
    return <Loader2 className="spin" size={11} />;
  if (status === "completed" || status === "transcribed")
    return <CheckCircle2 size={11} />;
  if (status === "error") return <XCircle size={11} />;
  return <Clock3 size={11} />;
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <mark className={`status-badge ${statusClass(status)}`}>
      <StatusIcon status={status} />
      {statusLabel(status)}
    </mark>
  );
}
