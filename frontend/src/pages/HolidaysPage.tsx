import { useState } from 'react';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type { HolidayResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { describedBy, Field } from '../components/Field';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { formatDate } from '../lib/format';
import { useResource } from '../lib/useResource';
import { validateIsoDate, validateName, type FieldErrors } from '../lib/validation';

export function HolidaysPage(): JSX.Element {
  const toast = useToast();
  const holidays = useResource<HolidayResponse[]>(
    (signal) => api.listHolidays({}, signal),
    'holidays-crud',
  );

  const [date, setDate] = useState('');
  const [name, setName] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<HolidayResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<HolidayResponse | null>(null);

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    const dateError = validateIsoDate(date, 'Date');
    if (dateError !== null) {
      next.date = dateError;
    }
    const nameError = validateName(name, 'Holiday name');
    if (nameError !== null) {
      next.name = nameError;
    }
    return next;
  }

  async function submit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (editing === null) {
        await api.createHoliday({ date, name: name.trim() });
        toast.success('Holiday added.');
      } else {
        await api.updateHoliday(editing.id, { date, name: name.trim() });
        toast.success('Holiday updated.');
      }
      setDate('');
      setName('');
      setEditing(null);
      holidays.reload();
    } catch (cause) {
      // A duplicate date answers 409 with the date in the message.
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors(cause.fieldErrors);
      }
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function remove(): Promise<void> {
    if (deleteTarget === null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.deleteHoliday(deleteTarget.id);
      toast.success('Holiday deleted.');
      setDeleteTarget(null);
      holidays.reload();
    } catch (cause) {
      setError(errorMessage(cause));
      setDeleteTarget(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Holidays"
        subtitle="Public holidays shape the working-day count for leave and payroll."
      />

      {error !== null && (
        <Banner
          onDismiss={() => {
            setError(null);
          }}
        >
          {error}
        </Banner>
      )}

      <Card title={editing === null ? 'Add a holiday' : `Edit ${editing.name}`}>
        <form onSubmit={submit} className="inline-form" noValidate>
          <Field id="holiday-date" label="Date" error={errors.date} required>
            <input
              id="holiday-date"
              type="date"
              value={date}
              required
              aria-invalid={errors.date !== undefined}
              aria-describedby={describedBy('holiday-date', false, errors.date !== undefined)}
              onChange={(event) => {
                setDate(event.target.value);
              }}
            />
          </Field>
          <Field id="holiday-name" label="Name" error={errors.name} required>
            <input
              id="holiday-name"
              value={name}
              required
              maxLength={120}
              aria-invalid={errors.name !== undefined}
              aria-describedby={describedBy('holiday-name', false, errors.name !== undefined)}
              onChange={(event) => {
                setName(event.target.value);
              }}
            />
          </Field>
          <div className="button-row">
            <button type="submit" className="button button-primary" disabled={busy}>
              {editing === null ? 'Add holiday' : 'Save holiday'}
            </button>
            {editing !== null && (
              <button
                type="button"
                className="button"
                onClick={() => {
                  setEditing(null);
                  setDate('');
                  setName('');
                }}
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </Card>

      <Card title="All holidays">
        {holidays.loading && <Loading label="Loading holidays…" />}
        {holidays.error !== null && <Banner>{holidays.error}</Banner>}
        {holidays.data !== null && holidays.data.length === 0 && (
          <EmptyState title="No holiday registered." />
        )}
        {holidays.data !== null && holidays.data.length > 0 && (
          <table className="table">
            <caption className="sr-only">Registered holidays</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Name</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {holidays.data.map((holiday) => (
                <tr key={holiday.id}>
                  <th scope="row">{formatDate(holiday.date)}</th>
                  <td>{holiday.name}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="button button-small"
                      onClick={() => {
                        setEditing(holiday);
                        setDate(holiday.date);
                        setName(holiday.name);
                      }}
                      aria-label={`Edit ${holiday.name}`}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="button button-danger button-small"
                      onClick={() => {
                        setDeleteTarget(holiday);
                      }}
                      aria-label={`Delete ${holiday.name}`}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {deleteTarget !== null && (
        <ConfirmDialog
          title={`Delete ${deleteTarget.name}?`}
          message={`${formatDate(deleteTarget.date)} will count as a normal working day again.`}
          confirmLabel="Delete"
          busy={busy}
          onConfirm={() => {
            void remove();
          }}
          onCancel={() => {
            setDeleteTarget(null);
          }}
        />
      )}
    </>
  );
}
