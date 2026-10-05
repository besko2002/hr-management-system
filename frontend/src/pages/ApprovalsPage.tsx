import { useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/ApiError';
import type { LeaveRequestResponse, PageResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { NoteDialog } from '../components/ConfirmDialog';
import { Pagination } from '../components/Pagination';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { formatDate } from '../lib/format';
import { useResource } from '../lib/useResource';

const PAGE_SIZE = 10;

interface Decision {
  request: LeaveRequestResponse;
  kind: 'approve' | 'reject';
}

/**
 * Pending leave awaiting the signed-in decider: their direct reports' requests, or every
 * pending request when HR/ADMIN. A rejection must carry a note — the API answers 400
 * without one, so the dialog requires it too.
 */
export interface ApprovalsPageProps {
  /** HR/ADMIN open the same endpoint, which then returns every pending request. */
  title?: string;
  subtitle?: string;
}

export function ApprovalsPage({
  title = 'Approvals',
  subtitle = 'Leave requests waiting for your decision.',
}: ApprovalsPageProps = {}): JSX.Element {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const pending = useResource<PageResponse<LeaveRequestResponse>>(
    (signal) => api.pendingLeaveRequests(page, PAGE_SIZE, signal),
    `pending-leave-${String(page)}`,
  );

  const [decision, setDecision] = useState<Decision | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(note: string): Promise<void> {
    if (decision === null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (decision.kind === 'approve') {
        await api.approveLeave(decision.request.id, note.length === 0 ? null : note);
        toast.success(`Approved ${decision.request.employeeName}'s leave.`);
      } else {
        await api.rejectLeave(decision.request.id, note);
        toast.success(`Rejected ${decision.request.employeeName}'s leave.`);
      }
      setDecision(null);
      pending.reload();
    } catch (cause) {
      setError(errorMessage(cause));
      setDecision(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title={title} subtitle={subtitle} />

      <Card>
        {pending.loading && <Loading label="Loading pending requests…" />}
        {pending.error !== null && <Banner>{pending.error}</Banner>}
        {error !== null && (
          <Banner
            onDismiss={() => {
              setError(null);
            }}
          >
            {error}
          </Banner>
        )}

        {pending.data !== null && pending.data.content.length === 0 && (
          <EmptyState title="Nothing is waiting for you." hint="Every request has been decided." />
        )}

        {pending.data !== null && pending.data.content.length > 0 && (
          <>
            <table className="table">
              <caption className="sr-only">Pending leave requests</caption>
              <thead>
                <tr>
                  <th scope="col">Employee</th>
                  <th scope="col">Type</th>
                  <th scope="col">From</th>
                  <th scope="col">To</th>
                  <th scope="col">Working days</th>
                  <th scope="col">Reason</th>
                  <th scope="col">
                    <span className="sr-only">Decision</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {pending.data.content.map((request) => (
                  <tr key={request.id}>
                    <th scope="row">{request.employeeName}</th>
                    <td>{request.leaveType}</td>
                    <td>{formatDate(request.startDate)}</td>
                    <td>{formatDate(request.endDate)}</td>
                    <td>{request.workingDays}</td>
                    <td>{request.reason ?? '—'}</td>
                    <td className="row-actions">
                      <button
                        type="button"
                        className="button button-primary button-small"
                        onClick={() => {
                          setDecision({ request, kind: 'approve' });
                        }}
                        aria-label={`Approve ${request.employeeName}'s leave request`}
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        className="button button-danger button-small"
                        onClick={() => {
                          setDecision({ request, kind: 'reject' });
                        }}
                        aria-label={`Reject ${request.employeeName}'s leave request`}
                      >
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={pending.data.page}
              totalPages={pending.data.totalPages}
              totalElements={pending.data.totalElements}
              onChange={setPage}
              busy={pending.loading}
            />
          </>
        )}
      </Card>

      {decision !== null && (
        <NoteDialog
          title={
            decision.kind === 'approve'
              ? `Approve ${decision.request.employeeName}'s leave`
              : `Reject ${decision.request.employeeName}'s leave`
          }
          label={decision.kind === 'approve' ? 'Note (optional)' : 'Reason for rejection'}
          required={decision.kind === 'reject'}
          confirmLabel={decision.kind === 'approve' ? 'Approve' : 'Reject'}
          busy={busy}
          onConfirm={(note) => {
            void submit(note);
          }}
          onCancel={() => {
            setDecision(null);
          }}
        />
      )}
    </>
  );
}
