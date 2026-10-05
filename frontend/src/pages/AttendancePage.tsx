import { useState } from 'react';
import { api } from '../api/api';
import type { AttendanceRangeResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { currentMonth, currentYear, monthInputValue, monthRange, parseMonthInput } from '../lib/dates';
import { formatDate, formatMinutes, formatPeriod, formatTime } from '../lib/format';
import { useResource } from '../lib/useResource';

export function AttendancePage(): JSX.Element {
  const [year, setYear] = useState(currentYear());
  const [month, setMonth] = useState(currentMonth());
  const range = monthRange(year, month);

  const attendance = useResource<AttendanceRangeResponse>(
    (signal) => api.myAttendance(range.from, range.to, signal),
    `attendance-${range.from}-${range.to}`,
  );

  const data = attendance.data;

  return (
    <>
      <PageHeader
        title="My attendance"
        subtitle="Derived from your sessions, the work calendar and approved leave."
      />

      <Card
        title={formatPeriod(year, month)}
        actions={
          <label className="inline-field">
            <span>Month</span>
            <input
              type="month"
              value={monthInputValue(year, month)}
              aria-label="Pick a month"
              onChange={(event) => {
                const parsed = parseMonthInput(event.target.value);
                if (parsed !== null) {
                  setYear(parsed.year);
                  setMonth(parsed.month);
                }
              }}
            />
          </label>
        }
      >
        {attendance.loading && <Loading label="Loading the month…" />}
        {attendance.error !== null && <Banner>{attendance.error}</Banner>}

        {data !== null && (
          <>
            <dl className="stat-row">
              <div className="stat">
                <dt>Working days</dt>
                <dd>{data.workingDays}</dd>
              </div>
              <div className="stat">
                <dt>Present</dt>
                <dd>{data.presentDays}</dd>
              </div>
              <div className="stat">
                <dt>Late</dt>
                <dd>{data.lateDays}</dd>
              </div>
              <div className="stat">
                <dt>Absent</dt>
                <dd>{data.absentDays}</dd>
              </div>
              <div className="stat">
                <dt>On leave</dt>
                <dd>{data.leaveDays}</dd>
              </div>
              <div className="stat">
                <dt>Missing check-out</dt>
                <dd>{data.missingCheckoutDays}</dd>
              </div>
              <div className="stat">
                <dt>Worked</dt>
                <dd>{formatMinutes(data.workedMinutes)}</dd>
              </div>
              <div className="stat">
                <dt>Overtime</dt>
                <dd>{formatMinutes(data.overtimeMinutes)}</dd>
              </div>
            </dl>

            {data.days.length === 0 ? (
              <EmptyState title="No days in this range." />
            ) : (
              <table className="table">
                <caption className="sr-only">
                  Attendance for {formatPeriod(year, month)}
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Day</th>
                    <th scope="col">Kind</th>
                    <th scope="col">Status</th>
                    <th scope="col">First in</th>
                    <th scope="col">Last out</th>
                    <th scope="col">Worked</th>
                    <th scope="col">Late</th>
                    <th scope="col">Overtime</th>
                  </tr>
                </thead>
                <tbody>
                  {data.days.map((day) => (
                    <tr key={day.day}>
                      <th scope="row">{formatDate(day.day)}</th>
                      <td>{day.dayKind}</td>
                      <td>
                        <StatusChip status={day.status} />
                        {day.leave !== null && (
                          <span className="muted"> {day.leave.leaveType}</span>
                        )}
                      </td>
                      <td>{formatTime(day.firstIn)}</td>
                      <td>{formatTime(day.lastOut)}</td>
                      <td>{formatMinutes(day.workedMinutes)}</td>
                      <td>{day.lateMinutes === 0 ? '—' : formatMinutes(day.lateMinutes)}</td>
                      <td>
                        {day.overtimeMinutes === 0 ? '—' : formatMinutes(day.overtimeMinutes)}
                      </td>
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
