import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type { PageResponse, PayrollRunResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { describedBy, Field } from '../components/Field';
import { Pagination } from '../components/Pagination';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/useToast';
import { currentMonth, currentYear } from '../lib/dates';
import { formatDateTime, formatPeriod } from '../lib/format';
import { useResource } from '../lib/useResource';
import type { FieldErrors } from '../lib/validation';

const PAGE_SIZE = 12;

export function PayrollPage(): JSX.Element {
  const toast = useToast();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const runs = useResource<PageResponse<PayrollRunResponse>>(
    (signal) => api.listPayrollRuns(page, PAGE_SIZE, signal),
    `payroll-runs-${String(page)}`,
  );

  const [year, setYear] = useState(String(currentYear()));
  const [month, setMonth] = useState(String(currentMonth()));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function createRun(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const next: FieldErrors = {};
    const yearValue = Number(year);
    const monthValue = Number(month);
    if (!Number.isInteger(yearValue) || yearValue < 2000 || yearValue > 2100) {
      next.year = 'Year must be between 2000 and 2100.';
    }
    if (!Number.isInteger(monthValue) || monthValue < 1 || monthValue > 12) {
      next.month = 'Month must be between 1 and 12.';
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const detail = await api.createPayrollRun({ year: yearValue, month: monthValue });
      toast.success(`Payroll run created for ${formatPeriod(yearValue, monthValue)}.`);
      navigate(`/payroll/${detail.run.id}`);
    } catch (cause) {
      // 409 when a run for that period already exists; 400 for a future month.
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors(cause.fieldErrors);
      }
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Payroll" subtitle="One run per month, idempotent and auditable." />

      {error !== null && (
        <Banner
          onDismiss={() => {
            setError(null);
          }}
        >
          {error}
        </Banner>
      )}

      <Card title="Create a run">
        <form onSubmit={createRun} className="inline-form" noValidate>
          <Field id="run-year" label="Year" error={errors.year} required>
            <input
              id="run-year"
              type="number"
              min={2000}
              max={2100}
              value={year}
              required
              aria-invalid={errors.year !== undefined}
              aria-describedby={describedBy('run-year', false, errors.year !== undefined)}
              onChange={(event) => {
                setYear(event.target.value);
              }}
            />
          </Field>
          <Field id="run-month" label="Month" error={errors.month} required>
            <input
              id="run-month"
              type="number"
              min={1}
              max={12}
              value={month}
              required
              aria-invalid={errors.month !== undefined}
              aria-describedby={describedBy('run-month', false, errors.month !== undefined)}
              onChange={(event) => {
                setMonth(event.target.value);
              }}
            />
          </Field>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Running payroll…' : 'Create run'}
          </button>
        </form>
        <p className="muted">
          A second run for the same month is refused with a conflict; a future month is refused
          too.
        </p>
      </Card>

      <Card title="Runs">
        {runs.loading && <Loading label="Loading payroll runs…" />}
        {runs.error !== null && <Banner>{runs.error}</Banner>}
        {runs.data !== null && runs.data.content.length === 0 && (
          <EmptyState title="No payroll run yet." hint="Create the first one above." />
        )}
        {runs.data !== null && runs.data.content.length > 0 && (
          <>
            <table className="table">
              <caption className="sr-only">Payroll runs, newest first</caption>
              <thead>
                <tr>
                  <th scope="col">Period</th>
                  <th scope="col">Status</th>
                  <th scope="col">Payslips</th>
                  <th scope="col">Created</th>
                  <th scope="col">Finalized</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.content.map((run) => (
                  <tr key={run.id}>
                    <th scope="row">
                      <Link to={`/payroll/${run.id}`}>{formatPeriod(run.year, run.month)}</Link>
                    </th>
                    <td>
                      <StatusChip status={run.status} />
                    </td>
                    <td>{run.payslipCount}</td>
                    <td>{formatDateTime(run.createdAt)}</td>
                    <td>{run.finalizedAt === null ? '—' : formatDateTime(run.finalizedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={runs.data.page}
              totalPages={runs.data.totalPages}
              totalElements={runs.data.totalElements}
              onChange={setPage}
              busy={runs.loading}
            />
          </>
        )}
      </Card>
    </>
  );
}
