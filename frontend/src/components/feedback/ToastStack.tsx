export interface ToastMessage {
  id: number;
  message: string;
  tone?: "success" | "error" | "info";
}

export function ToastStack({ toasts }: { toasts: ToastMessage[] }) {
  if (toasts.length === 0) return null;

  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((toast, i) => (
        <div
          key={toast.id}
          className={`toast ${toast.tone ?? "info"}`}
          style={{ animationDelay: `${i * 40}ms` }}
        >
          {toast.message}
        </div>
      ))}
    </div>
  );
}
