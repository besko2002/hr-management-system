import { useState } from 'react';
import { Modal } from './Modal';

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'danger' | 'primary';
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** The gate in front of every destructive action: terminate, delete, finalize. */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'danger',
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps): JSX.Element {
  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={
        <>
          <button type="button" className="button" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`button button-${tone}`}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <p>{message}</p>
    </Modal>
  );
}

interface NoteDialogProps {
  title: string;
  label: string;
  /** A rejection must carry a note: the API answers 400 without one. */
  required?: boolean;
  confirmLabel?: string;
  busy?: boolean;
  onConfirm: (note: string) => void;
  onCancel: () => void;
}

/** Decision dialog with a note field, used by leave approve / reject. */
export function NoteDialog({
  title,
  label,
  required = false,
  confirmLabel = 'Submit',
  busy = false,
  onConfirm,
  onCancel,
}: NoteDialogProps): JSX.Element {
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  function submit(): void {
    const value = note.trim();
    if (required && value.length === 0) {
      setError('A note is required to reject a request.');
      return;
    }
    if (note.length > 500) {
      setError('The note must be at most 500 characters.');
      return;
    }
    setError(null);
    onConfirm(value);
  }

  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={
        <>
          <button type="button" className="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="button button-primary" onClick={submit} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <div className="field">
        <label htmlFor="decision-note">
          {label}
          {required && <span aria-hidden="true"> *</span>}
        </label>
        <textarea
          id="decision-note"
          value={note}
          rows={4}
          maxLength={500}
          required={required}
          aria-required={required}
          aria-invalid={error !== null}
          aria-describedby={error === null ? undefined : 'decision-note-error'}
          onChange={(event) => {
            setNote(event.target.value);
          }}
        />
        {error !== null && (
          <p className="field-error" id="decision-note-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </Modal>
  );
}
