import { useState } from 'react';
import { api } from '../api/api';
import type { LeaveCalendarEntry } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import {
  currentMonth,
  currentYear,
  eachDay,
  monthInputValue,
  monthRange,
  parseMonthInput,
} from '../lib/dates';
import { formatDate, formatPeriod } from '../lib/format';
import { useResource } from '../lib/useResource';

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function coveredDays(entry: LeaveCalendarEntry, from: string, to: string): string[] {
  const start = entry.startDate > from ? entry.startDate : from;
  const end = entry.endDate < to ? entry.endDate : to;
  if (start > end) {
    return [];
  }
  return eachDay(start, end);
}

/**
 * Approved leave for the caller's team (everyone, for HR/ADMIN). The API deliberately
 * omits the reason from this payload, so there is nothing private to render.
 */
export function TeamCalendarPage(): JSX.Element {
  const [year, setYear] = useState(currentYear());
  const [month, setMonth] = useState(currentMonth());
  const range = monthRange(year, month);

  const calendar = useResource<LeaveCalendarEntry[]>(
    (signal) => api.leaveCalendar(range.from, range.to, signal),
    `leave-calendar-${range.from}`,
  );

  const byDay = new Map<string, LeaveCalendarEntry[]>();
  for (const entry of calendar.data ?? []) {
    for (const day of coveredDays(entry, range.from, range.to)) {
      const list = byDay.get(day) ?? [];
      list.push(entry);
      byDay.set(day, list);
    }
  }

  const days = eachDay(range.from, range.to);
  const leadingBlanks = new Date(`${range.from}T00:00:00Z`).getUTCDay();

  return (
    <>
      <PageHeader title="Team calendar" subtitle="Approved leave only. No reasons are shown." />

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
        {calendar.loading && <Loading label="Loading the calendar…" />}
        {calendar.error !== null && <Banner>{calendar.error}</Banner>}

        {calendar.data !== null && calendar.data.length === 0 && (
          <EmptyState title="No approved leave this month." />
        )}

        {calendar.data !== null && calendar.data.length > 0 && (
          <>
            <div className="month-grid" role="group" aria-label={`Leave in ${formatPeriod(year, month)}`}>
              {WEEKDAY_LABELS.map((label) => (
                <p key={label} className="month-weekday">
                  {label}
                </p>
              ))}
              {Array.from({ length: leadingBlanks }, (_, index) => (
                <div key={`blank-${String(index)}`} className="month-cell month-cell-blank" />
              ))}
              {days.map((day) => {
                const entries = byDay.get(day) ?? [];
                return (
                  <div key={day} className="month-cell">
                    <p className="month-day">{Number(day.slice(8, 10))}</p>
                    <ul>
                      {entries.map((entry) => (
                        <li key={`${day}-${entry.requestId}`} className="month-entry">
                          <span className="month-entry-name">{entry.employeeName}</span>
                          <span className="month-entry-type">{entry.leaveType}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                );
              })}
            </div>

            <table className="table table-compact spaced">
              <caption>Approved leave in {formatPeriod(year, month)}</caption>
              <thead>
                <tr>
                  <th scope="col">Employee</th>
                  <th scope="col">Type</th>
                  <th scope="col">From</th>
                  <th scope="col">To</th>
                  <th scope="col">Working days</th>
                </tr>
              </thead>
              <tbody>
                {calendar.data.map((entry) => (
                  <tr key={entry.requestId}>
                    <th scope="row">{entry.employeeName}</th>
                    <td>{entry.leaveType}</td>
                    <td>{formatDate(entry.startDate)}</td>
                    <td>{formatDate(entry.endDate)}</td>
                    <td>{entry.workingDays}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </Card>
    </>
  );
}
