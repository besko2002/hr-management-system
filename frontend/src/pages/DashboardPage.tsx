import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import { errorMessage } from '../api/ApiError';
import type { AttendanceRangeResponse, LeaveBalanceResponse, LeaveRequestResponse, PageResponse } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/useToast';
import { today } from '../lib/dates';
import { formatDate, formatMinutes, formatTime } from '../lib/format';
import { useResource } from '../lib/useResource';

const RECENT_SIZE = 5;

export function DashboardPage(): JSX.Element {
  const { user } = useAuth();
  const toast = useToast();
  const day = today();

  const attendance = useResource<AttendanceRangeResponse>(
    (signal) => api.myAttendance(day, day, signal),
    `attendance-today-${day}`,
  );
  const balances = useResource<LeaveBalanceResponse[]>(
    (signal) => api.myLeaveBalances(undefined, signal),
    'my-balances',
  );
  const requests = useResource<PageResponse<LeaveRequestResponse>>(
    (signal) => api.myLeaveRequests({ page: 0, size: RECENT_SIZE }, signal),
    'my-recent-leave',
  );

  const [clockError, setClockError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'in' | 'out' | null>(null);

  const dayToday = attendance.data?.days[0] ?? null;
  const openSession = dayToday?.openSession === true;

  async function clock(direction: 'in' | 'out'): Promise<void> {
    setBusy(direction);
    setClockError(null);
    try {
      if (direction === 'in') {
        await api.checkIn();
        toast.success('Checked in.');
      } else {
        await api.checkOut();
        toast.success('Checked out.');
      }
      attendance.reload();
    } catch (cause) {
      // 409 is the normal "already open" / "nothing open" answer: show its message.
      setClockError(errorMessage(cause));
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader
        title={`Hello, ${user?.fullName ?? 'there'}`}
        subtitle={`Today is ${formatDate(day)}.`}
      />

      <div className="grid grid-2">
        <Card title="Today's attendance">
          {attendance.loading && <Loading label="Loading today…" />}
          {attendance.error !== null && <Banner>{attendance.error}</Banner>}
          {clockError !== null && (
            <Banner
              onDismiss={() => {
                setClockError(null);
              }}
            >
              {clockError}
            </Banner>
          )}

          {dayToday !== null && (
            <>
              <dl className="stat-row">
                <div className="stat">
                  <dt>Status</dt>
                  <dd>
                    <StatusChip status={dayToday.status} />
                  </dd>
                </div>
                <div className="stat">
                  <dt>First check-in</dt>
                  <dd>{formatTime(dayToday.firstIn)}</dd>
                </div>
                <div className="stat">
                  <dt>Last check-out</dt>
                  <dd>{formatTime(dayToday.lastOut)}</dd>
                </div>
                <div className="stat">
                  <dt>Worked</dt>
                  <dd>{formatMinutes(dayToday.workedMinutes)}</dd>
                </div>
              </dl>
              <p className="muted">
                {openSession
                  ? 'You are currently checked in.'
                  : 'No session is open right now.'}
              </p>
            </>
          )}

          <div className="button-row">
            <button
              type="button"
              className="button button-primary"
              disabled={busy !== null}
              onClick={() => {
                void clock('in');
              }}
            >
              {busy === 'in' ? 'Checking in…' : 'Check in'}
            </button>
            <button
              type="button"
              className="button"
              disabled={busy !== null}
              onClick={() => {
                void clock('out');
              }}
            >
              {busy === 'out' ? 'Checking out…' : 'Check out'}
            </button>
          </div>
        </Card>

        <Card title="Leave balances" actions={<Link to="/leave">Request leave</Link>}>
          {balances.loading && <Loading label="Loading balances…" />}
          {balances.error !== null && <Banner>{balances.error}</Banner>}
          {balances.data !== null && balances.data.length === 0 && (
            <EmptyState title="No balances for this year yet." />
          )}
          {balances.data !== null && balances.data.length > 0 && (
            <ul className="balance-cards">
              {balances.data.map((balance) => (
                <li key={balance.leaveType} className="balance-card">
                  <p className="balance-type">{balance.leaveType}</p>
                  <p className="balance-remaining">
                    {balance.remainingDays === null ? 'No balance' : `${balance.remainingDays} days`}
                  </p>
                  <p className="balance-detail">
                    {balance.requiresBalance
                      ? `${balance.entitledDays + balance.carriedOverDays} entitled · ${balance.usedDays} used · ${balance.pendingDays} pending`
                      : balance.paid
                        ? 'Paid, no balance needed'
                        : 'Unpaid, no balance needed'}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card title="My recent leave requests" actions={<Link to="/leave">See all</Link>}>
        {requests.loading && <Loading label="Loading requests…" />}
        {requests.error !== null && <Banner>{requests.error}</Banner>}
        {requests.data !== null && requests.data.content.length === 0 && (
          <EmptyState title="No leave requests yet." hint="Your next request will show up here." />
        )}
        {requests.data !== null && requests.data.content.length > 0 && (
          <table className="table">
            <caption className="sr-only">My five most recent leave requests</caption>
            <thead>
              <tr>
                <th scope="col">Type</th>
                <th scope="col">From</th>
                <th scope="col">To</th>
                <th scope="col">Working days</th>
                <th scope="col">Status</th>
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
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}
