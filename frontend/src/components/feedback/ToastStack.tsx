export interface ToastMessage {
  id: number;
  message: string;
  tone?: "success" | "error" | "info";
}

export function ToastStack({ toasts }: { toasts: ToastMessage[] }) {
  if (toasts.length === 0) return null;
  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast ${toast.tone ?? "info"}`}>
          {toast.message}
        </div>
      ))}
    </div>
  );
}

