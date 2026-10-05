import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeAttendanceRange, makeDay, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

/** The range the page asks for on mount, derived from today exactly as the page does. */
function currentMonthRange(): { from: string; to: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const pad = (value: number): string => String(value).padStart(2, '0');
  const lastDay = new Date(year, month, 0).getDate();
  return { from: `${String(year)}-${pad(month)}-01`, to: `${String(year)}-${pad(month)}-${pad(lastDay)}` };
}

function attendanceRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn(),
    'GET /api/attendance/me': { body: makeAttendanceRange() },
    ...overrides,
  };
}

describe('attendance page', () => {
  it('asks for the current month and renders the summary totals', async () => {
    const mock = installFetchMock(attendanceRoutes());

    renderApp('/attendance');

    expect(await screen.findByText('Working days')).toBeInTheDocument();
    const { from, to } = currentMonthRange();
    expect(mock.urlsFor('GET', '/api/attendance/me')).toEqual([
      `/api/attendance/me?from=${from}&to=${to}`,
    ]);
    // workedMinutes 9000 and overtimeMinutes 120 are shown as hours and minutes.
    expect(screen.getByText('150h 0m')).toBeInTheDocument();
    expect(screen.getByText('2h 0m')).toBeInTheDocument();
    expect(screen.getByText('21')).toBeInTheDocument();
  });

  it('refetches the chosen month when the picker changes', async () => {
    const mock = installFetchMock(attendanceRoutes());

    renderApp('/attendance');
    await screen.findByText('Working days');
    fireEvent.change(screen.getByLabelText('Pick a month'), { target: { value: '2024-02' } });

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/attendance/me')).toContain(
        '/api/attendance/me?from=2024-02-01&to=2024-02-29',
      );
    });
    expect(await screen.findByRole('heading', { name: 'February 2024' })).toBeInTheDocument();
  });

  it('ignores an unparseable month and keeps the loaded range', async () => {
    const mock = installFetchMock(attendanceRoutes());

    renderApp('/attendance');
    await screen.findByText('Working days');
    const before = mock.urlsFor('GET', '/api/attendance/me').length;
    fireEvent.change(screen.getByLabelText('Pick a month'), { target: { value: '2024-13' } });

    expect(mock.urlsFor('GET', '/api/attendance/me')).toHaveLength(before);
  });

  it('renders one row per day with its times, and a dash for zero late or overtime', async () => {
    installFetchMock(
      attendanceRoutes({
        'GET /api/attendance/me': {
          body: makeAttendanceRange({
            days: [
              makeDay({
                day: '2025-03-04',
                firstIn: '2025-03-04T09:00:00',
                lastOut: '2025-03-04T17:05:00',
                lateMinutes: 0,
                overtimeMinutes: 0,
              }),
            ],
          }),
        },
      }),
    );

    renderApp('/attendance');

    const table = await screen.findByRole('table');
    const row = within(table).getByRole('row', { name: /4 Mar 2025/ });
    expect(within(row).getByText('09:00')).toBeInTheDocument();
    expect(within(row).getByText('17:05')).toBeInTheDocument();
    expect(within(row).getByText('Present')).toBeInTheDocument();
    expect(within(row).getAllByText('—')).toHaveLength(2);
  });

  it('shows the leave type on a day spent on approved leave', async () => {
    installFetchMock(
      attendanceRoutes({
        'GET /api/attendance/me': {
          body: makeAttendanceRange({
            days: [
              makeDay({
                status: 'ON_LEAVE',
                firstIn: null,
                lastOut: null,
                leave: { leaveType: 'SICK', paid: true },
              }),
            ],
          }),
        },
      }),
    );

    renderApp('/attendance');

    const table = await screen.findByRole('table');
    expect(within(table).getByText('On leave')).toBeInTheDocument();
    expect(within(table).getByText('SICK')).toBeInTheDocument();
  });

  it('shows the empty state when the range holds no day', async () => {
    installFetchMock(
      attendanceRoutes({
        'GET /api/attendance/me': { body: makeAttendanceRange({ days: [] }) },
      }),
    );

    renderApp('/attendance');

    expect(await screen.findByText('No days in this range.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('surfaces a server failure in an alert instead of an empty page', async () => {
    installFetchMock(
      attendanceRoutes({
        'GET /api/attendance/me': {
          status: 500,
          body: { status: 500, message: 'Attendance is unavailable' },
        },
      }),
    );

    renderApp('/attendance');

    expect(await screen.findByRole('alert')).toHaveTextContent('Attendance is unavailable');
  });
});
