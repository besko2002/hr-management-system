import type { ReactNode } from 'react';

export type BannerTone = 'error' | 'info' | 'success' | 'warning';

interface BannerProps {
  tone?: BannerTone;
  children: ReactNode;
  onDismiss?: () => void;
}

/**
 * The one way an error or notice reaches the screen. `role="alert"` for errors so a
 * screen reader announces a failed save immediately.
 */
export function Banner({ tone = 'error', children, onDismiss }: BannerProps): JSX.Element {
  return (
    <div className={`banner banner-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span className="banner-text">{children}</span>
      {onDismiss !== undefined && (
        <button type="button" className="banner-close" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}
