import type { ReactNode } from 'react';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/ToastProvider';

/** The real app providers, for tests. */
export function Providers({ children }: { children: ReactNode }): JSX.Element {
  return (
    <AuthProvider>
      <ToastProvider>{children}</ToastProvider>
    </AuthProvider>
  );
}
