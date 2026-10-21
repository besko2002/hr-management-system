import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { ToastContext, type Toast, type ToastContextValue, type ToastTone } from './toastContext';

const LIFETIME_MS = 5000;

/** Success confirmations, announced politely and dismissable by keyboard. */
export function ToastProvider({ children }: { children: ReactNode }): JSX.Element {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number): void => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, message: string): void => {
      const id = nextId.current;
      nextId.current += 1;
      setToasts((current) => [...current, { id, tone, message }]);
      window.setTimeout(() => {
        dismiss(id);
      }, LIFETIME_MS);
    },
    [dismiss],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      toasts,
      success: (message: string) => {
        push('success', message);
      },
      failure: (message: string) => {
        push('error', message);
      },
      dismiss,
    }),
    [toasts, push, dismiss],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" role="status" aria-live="polite" aria-label="Notifications">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.tone}`}>
            <span>{toast.message}</span>
            <button
              type="button"
              className="icon-button"
              aria-label="Dismiss notification"
              onClick={() => {
                dismiss(toast.id);
              }}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
