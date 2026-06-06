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
    <div
      className="confirm-backdrop anim-fade-in"
      role="dialog"
      aria-modal="true"
      aria-label={state.title}
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="confirm-card anim-scale-in">
        <h2>{state.title}</h2>
        <p>{state.message}</p>
        <div className="confirm-actions">
          <button className="btn btn-ghost" disabled={busy} onClick={onCancel}>
            取消
          </button>
          <button
            className={state.tone === "danger" ? "btn btn-danger" : "btn btn-primary"}
            disabled={busy}
            onClick={async () => {
              try {
                await state.onConfirm();
              } finally {
                onCancel();
              }
            }}
          >
            {state.confirmLabel ?? "确认"}
          </button>
        </div>
      </div>
    </div>
  );
}
