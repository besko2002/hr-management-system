import { createContext } from 'react';

export type ToastTone = 'success' | 'error';

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

export interface ToastContextValue {
  toasts: Toast[];
  success: (message: string) => void;
  failure: (message: string) => void;
  dismiss: (id: number) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);
