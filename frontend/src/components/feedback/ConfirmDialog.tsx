export interface ConfirmDialogState {
  title: string;
  message: string;
  confirmLabel?: string;
  tone?: "danger" | "primary";
  onConfirm: () => void | Promise<void>;
}

export function ConfirmDialog({
  state,
  busy,
  onCancel,
}: {
  state: ConfirmDialogState | null;
  busy: boolean;
  onCancel: () => void;
}) {
  if (!state) return null;

  return (
    <div className="confirm-backdrop" role="dialog" aria-modal="true" aria-label={state.title}>
      <div className="confirm-card">
        <h2>{state.title}</h2>
        <p>{state.message}</p>
        <div className="confirm-actions">
          <button className="secondary" disabled={busy} onClick={onCancel}>取消</button>
          <button
            className={state.tone === "danger" ? "danger-button" : "primary"}
            disabled={busy}
            onClick={() => void state.onConfirm()}
          >
            {state.confirmLabel ?? "确认"}
          </button>
        </div>
      </div>
    </div>
  );
}

