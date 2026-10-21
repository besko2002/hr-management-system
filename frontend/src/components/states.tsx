import type { ReactNode } from 'react';

/** Busy indicator for a panel that is fetching. Announced politely. */
export function Loading({ label = 'Loading…' }: { label?: string }): JSX.Element {
  return (
    <div className="loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

/** Shown when a list came back empty, never as a disguise for an error. */
export function EmptyState({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children?: ReactNode;
}): JSX.Element {
  return (
    <div className="empty-state">
      <p className="empty-title">{title}</p>
      {hint !== undefined && <p className="empty-hint">{hint}</p>}
      {children}
    </div>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}): JSX.Element {
  return (
    <header className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle !== undefined && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions !== undefined && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function Card({
  title,
  children,
  actions,
}: {
  title?: string;
  children: ReactNode;
  actions?: ReactNode;
}): JSX.Element {
  return (
    <section className="card">
      {(title !== undefined || actions !== undefined) && (
        <div className="card-head">
          {title !== undefined && <h2 className="card-title">{title}</h2>}
          {actions !== undefined && <div className="card-actions">{actions}</div>}
        </div>
      )}
      <div className="card-body">{children}</div>
    </section>
  );
}
