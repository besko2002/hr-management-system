import type { ReactNode } from 'react';

interface FieldProps {
  id: string;
  label: string;
  error?: string | null;
  hint?: string;
  required?: boolean;
  children: ReactNode;
}

/**
 * Label + control + error, wired with `aria-describedby`/`aria-invalid` by the caller
 * using the ids this component documents: `${id}-error` and `${id}-hint`.
 */
export function Field({ id, label, error, hint, required = false, children }: FieldProps): JSX.Element {
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {hint !== undefined && (
        <p className="field-hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error !== null && error !== undefined && (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

/** The describedby value for a control that may have a hint and an error. */
export function describedBy(id: string, hint: boolean, error: boolean): string | undefined {
  const ids = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(
    (value): value is string => value !== null,
  );
  return ids.length > 0 ? ids.join(' ') : undefined;
}
