import { useState } from 'react';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type { LeaveBalanceResponse, LeaveRequestResponse, LeaveStatus, LeaveType, PageResponse } from '../api/types';
import { LEAVE_TYPES } from '../api/types';
import { Banner } from '../components/Banner';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { describedBy, Field } from '../components/Field';
import { Pagination } from '../components/Pagination';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/useToast';
import { today } from '../lib/dates';
import { formatDate } from '../lib/format';
import { useResource } from '../lib/useResource';
import { hasErrors, validateLeaveRequest, type FieldErrors } from '../lib/validation';

const PAGE_SIZE = 10;

/** PENDING, and APPROVED before it starts, are the only cancellable states. */
function cancellable(request: LeaveRequestResponse): boolean {
  if (request.status === 'PENDING') {
    return true;
  }
  return request.status === 'APPROVED' && request.startDate > today();
}

export function LeavePage(): JSX.Element {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<LeaveStatus | ''>('');

  const requests = useResource<PageResponse<LeaveRequestResponse>>(
    (signal) =>
      api.myLeaveRequests(
        {
          page,
          size: PAGE_SIZE,
          status: statusFilter === '' ? undefined : statusFilter,
        },
        signal,
      ),
    `my-leave-${String(page)}-${statusFilter}`,
  );
  const balances = useResource<LeaveBalanceResponse[]>(
    (signal) => api.myLeaveBalances(undefined, signal),
    'my-balances-leave-page',
  );

  const [type, setType] = useState<LeaveType>('ANNUAL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<LeaveRequestResponse | null>(null);
  const [cancelTarget, setCancelTarget] = useState<LeaveRequestResponse | null>(null);
  const [cancelBusy, setCancelBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const next = validateLeaveRequest({ type, startDate, endDate, reason });
    setErrors(next);
    if (hasErrors(next)) {
      return;
    }

    setBusy(true);
    setFormError(null);
    setCreated(null);
    try {
      const request = await api.createLeaveRequest({
        type,
        startDate,
        endDate,
        reason: reason.trim().length === 0 ? null : reason.trim(),
      });
      setCreated(request);
      toast.success(`Leave request filed for ${String(request.workingDays)} working day(s).`);
      setStartDate('');
      setEndDate('');
      setReason('');
      setPage(0);
      requests.reload();
      balances.reload();
    } catch (cause) {
      // 400 carries fieldErrors (or the zero-working-days message); 409 carries an
      // overlap or insufficient-balance message. Both are shown as the API worded them.
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors(cause.fieldErrors);
      }
      setFormError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function confirmCancel(): Promise<void> {
    if (cancelTarget === null) {
      return;
    }
    setCancelBusy(true);
    try {
      await api.cancelLeave(cancelTarget.id, null);
      toast.success('Leave request cancelled.');
      setCancelTarget(null);
      requests.reload();
      balances.reload();
    } catch (cause) {
      setFormError(errorMessage(cause));
      setCancelTarget(null);
    } finally {
      setCancelBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Leave" subtitle="File a request and follow its decision." />

      <div className="grid grid-2">
        <Card title="Request leave">
          {formError !== null && (
            <Banner
              onDismiss={() => {
                setFormError(null);
              }}
            >
              {formError}
            </Banner>
          )}
          {created !== null && (
            <Banner tone="success">
              Filed: {created.workingDays} working day(s) from {formatDate(created.startDate)} to{' '}
              {formatDate(created.endDate)}, now {created.status.toLowerCase()}.
            </Banner>
          )}

          <form onSubmit={onSubmit} noValidate>
            <Field id="leave-type" label="Leave type" error={errors.type} required>
              <select
                id="leave-type"
                value={type}
                onChange={(event) => {
                  setType(event.target.value as LeaveType);
                }}
              >
                {LEAVE_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </Field>

            <div className="field-row">
              <Field id="startDate" label="Start date" error={errors.startDate} required>
                <input
                  id="startDate"
                  type="date"
                  value={startDate}
                  required
                  aria-invalid={errors.startDate !== undefined}
                  aria-describedby={describedBy('startDate', false, errors.startDate !== undefined)}
                  onChange={(event) => {
                    setStartDate(event.target.value);
                  }}
                />
              </Field>
              <Field id="endDate" label="End date" error={errors.endDate} required>
                <input
                  id="endDate"
                  type="date"
                  value={endDate}
                  required
                  aria-invalid={errors.endDate !== undefined}
                  aria-describedby={describedBy('endDate', false, errors.endDate !== undefined)}
                  onChange={(event) => {
                    setEndDate(event.target.value);
                  }}
                />
              </Field>
            </div>

            <Field
              id="reason"
              label="Reason"
              error={errors.reason}
              hint="Optional, at most 500 characters."
            >
              <textarea
                id="reason"
                rows={3}
                value={reason}
                maxLength={500}
                aria-invalid={errors.reason !== undefined}
                aria-describedby={describedBy('reason', true, errors.reason !== undefined)}
                onChange={(event) => {
                  setReason(event.target.value);
                }}
              />
            </Field>

            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit request'}
            </button>
          </form>
        </Card>

        <Card title="My balances">
          {balances.loading && <Loading label="Loading balances…" />}
          {balances.error !== null && <Banner>{balances.error}</Banner>}
          {balances.data !== null && (
            <table className="table table-compact">
              <caption className="sr-only">Leave balances for the current year</caption>
              <thead>
                <tr>
                  <th scope="col">Type</th>
                  <th scope="col">Entitled</th>
                  <th scope="col">Carried over</th>
                  <th scope="col">Used</th>
                  <th scope="col">Pending</th>
                  <th scope="col">Remaining</th>
                </tr>
              </thead>
              <tbody>
                {balances.data.map((balance) => (
                  <tr key={balance.leaveType}>
                    <th scope="row">{balance.leaveType}</th>
                    <td>{balance.entitledDays}</td>
                    <td>{balance.carriedOverDays}</td>
                    <td>{balance.usedDays}</td>
                    <td>{balance.pendingDays}</td>
                    <td>{balance.remainingDays === null ? 'n/a' : balance.remainingDays}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <Card
        title="My requests"
        actions={
          <label className="inline-field">
            <span>Status</span>
            <select
              value={statusFilter}
              aria-label="Filter by status"
              onChange={(event) => {
                setStatusFilter(event.target.value as LeaveStatus | '');
                setPage(0);
              }}
            >
              <option value="">All</option>
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </label>
        }
      >
        {requests.loading && <Loading label="Loading requests…" />}
        {requests.error !== null && <Banner>{requests.error}</Banner>}
        {requests.data !== null && requests.data.content.length === 0 && (
          <EmptyState title="Nothing to show." hint="No leave request matches this filter." />
        )}
        {requests.data !== null && requests.data.content.length > 0 && (
          <>
            <table className="table">
              <caption className="sr-only">My leave requests</caption>
              <thead>
                <tr>
                  <th scope="col">Type</th>
                  <th scope="col">From</th>
                  <th scope="col">To</th>
                  <th scope="col">Days</th>
                  <th scope="col">Status</th>
                  <th scope="col">Decision</th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {requests.data.content.map((request) => (
                  <tr key={request.id}>
                    <td>{request.leaveType}</td>
                    <td>{formatDate(request.startDate)}</td>
                    <td>{formatDate(request.endDate)}</td>
                    <td>{request.workingDays}</td>
                    <td>
                      <StatusChip status={request.status} />
                    </td>
                    <td>
                      {request.decisionNote ?? (request.decidedByName === null ? '—' : 'No note')}
                    </td>
                    <td>
                      {cancellable(request) && (
                        <button
                          type="button"
                          className="button button-small"
                          onClick={() => {
                            setCancelTarget(request);
                          }}
                        >
                          Cancel
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={requests.data.page}
              totalPages={requests.data.totalPages}
              totalElements={requests.data.totalElements}
              onChange={setPage}
              busy={requests.loading}
            />
          </>
        )}
      </Card>

      {cancelTarget !== null && (
        <ConfirmDialog
          title="Cancel this leave request?"
          message={`${cancelTarget.leaveType} from ${formatDate(cancelTarget.startDate)} to ${formatDate(cancelTarget.endDate)} will be cancelled. An approved leave can only be cancelled before it starts.`}
          confirmLabel="Cancel request"
          cancelLabel="Keep it"
          busy={cancelBusy}
          onConfirm={() => {
            void confirmCancel();
          }}
          onCancel={() => {
            setCancelTarget(null);
          }}
        />
      )}
    </>
  );
}
