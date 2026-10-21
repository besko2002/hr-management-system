import { useContext } from 'react';
import { ToastContext, type ToastContextValue } from './toastContext';

/** Outside a provider the toasts are simply dropped, so no screen can crash over one. */
const NOOP: ToastContextValue = {
  toasts: [],
  success: () => undefined,
  failure: () => undefined,
  dismiss: () => undefined,
};

export function useToast(): ToastContextValue {
  return useContext(ToastContext) ?? NOOP;
}
