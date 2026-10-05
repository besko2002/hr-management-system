import { api } from '../api/api';
import type { TeamTodayResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { formatDate, formatMinutes, formatTime } from '../lib/format';
import { useResource } from '../lib/useResource';

/** Today for the whole subtree. Attendance is a manager's business; pay is not. */
export function TeamTodayPage(): JSX.Element {
  const todayView = useResource<TeamTodayResponse>((signal) => api.teamToday(signal), 'team-today');
  const data = todayView.data;

  return (
    <>
      <PageHeader title="Team today" subtitle="Who is in, late, absent or on leave." />

      <Card title={data === null ? 'Today' : `${formatDate(data.day)} · ${data.dayKind}`}>
        {todayView.loading && <Loading label="Loading today…" />}
        {todayView.error !== null && <Banner>{todayView.error}</Banner>}

        {data !== null && (
          <>
            <dl className="stat-row">
              <div className="stat">
                <dt>Team size</dt>
                <dd>{data.teamSize}</dd>
              </div>
              <div className="stat">
                <dt>Currently in</dt>
                <dd>{data.inCount}</dd>
              </div>
              <div className="stat">
                <dt>Present</dt>
                <dd>{data.presentCount}</dd>
              </div>
              <div className="stat">
                <dt>Late</dt>
                <dd>{data.lateCount}</dd>
              </div>
              <div className="stat">
                <dt>Absent</dt>
                <dd>{data.absentCount}</dd>
              </div>
              <div className="stat">
                <dt>On leave</dt>
                <dd>{data.onLeaveCount}</dd>
              </div>
              <div className="stat">
                <dt>Missing check-out</dt>
                <dd>{data.missingCheckoutCount}</dd>
              </div>
            </dl>

            {data.members.length === 0 ? (
              <EmptyState title="Nobody reports to you." />
            ) : (
              <table className="table">
                <caption className="sr-only">Team attendance today</caption>
                <thead>
                  <tr>
                    <th scope="col">Employee</th>
                    <th scope="col">Job title</th>
                    <th scope="col">Status</th>
                    <th scope="col">First in</th>
                    <th scope="col">Last out</th>
                    <th scope="col">Worked</th>
                    <th scope="col">Late</th>
                  </tr>
                </thead>
                <tbody>
                  {data.members.map((member) => (
                    <tr key={member.employeeId}>
                      <th scope="row">{member.employeeName}</th>
                      <td>{member.jobTitle ?? '—'}</td>
                      <td>
                        <StatusChip status={member.status} />
                        {member.currentlyIn && <span className="muted"> in now</span>}
                      </td>
                      <td>{formatTime(member.firstIn)}</td>
                      <td>{formatTime(member.lastOut)}</td>
                      <td>{formatMinutes(member.workedMinutes)}</td>
                      <td>{member.lateMinutes === 0 ? '—' : formatMinutes(member.lateMinutes)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      </Card>
    </>
  );
}
