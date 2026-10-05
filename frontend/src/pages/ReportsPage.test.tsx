import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { makeHeadcount, signedIn, TOKEN } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const YEAR = new Date().getFullYear();
const MONTH = new Date().getMonth() + 1;

function reportRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/reports/headcount': {
      body: [makeHeadcount(), makeHeadcount({ department: 'Engineering', count: 7 })],
    },
    ...overrides,
  };
}

afterEach(() => {
  Reflect.deleteProperty(URL, 'createObjectURL');
  Reflect.deleteProperty(URL, 'revokeObjectURL');
});

describe('reports', () => {
  it('charts the headcount rows as an accessible table', async () => {
    const mock = installFetchMock(reportRoutes());

    renderApp('/reports');

    const chart = await screen.findByRole('table', {
      name: 'Employees per department and status',
    });
    expect(within(chart).getByRole('columnheader', { name: 'Employees' })).toBeInTheDocument();
    const row = within(chart).getByRole('row', { name: /Finance · Active/ });
    expect(within(row).getByRole('cell', { name: '4' })).toBeInTheDocument();
    expect(within(chart).getByRole('row', { name: /Engineering · Active/ })).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/reports/headcount')).toEqual(['/api/reports/headcount']);
  });

  it('shows a friendly empty state instead of an empty headcount chart', async () => {
    installFetchMock(reportRoutes({ 'GET /api/reports/headcount': { body: [] } }));

    renderApp('/reports');

    expect(await screen.findByText('No employee to count.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('asks the leave summary for the current year and charts it', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      reportRoutes({
        [`GET /api/reports/leave-summary?year=${String(YEAR)}`]: {
          body: [
            {
              department: 'Finance',
              leaveType: 'ANNUAL',
              usedDays: 12,
              pendingDays: 3,
              remainingDays: 9,
            },
          ],
        },
      }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Leave summary' }));

    const chart = await screen.findByRole('table', {
      name: 'Used leave days per department and type',
    });
    expect(within(chart).getByRole('row', { name: /Finance · ANNUAL/ })).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/reports/leave-summary')).toEqual([
      `/api/reports/leave-summary?year=${String(YEAR)}`,
    ]);
  });

  it('shows the empty state when no leave was recorded in the year', async () => {
    const user = userEvent.setup();
    installFetchMock(
      reportRoutes({ [`GET /api/reports/leave-summary?year=${String(YEAR)}`]: { body: [] } }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Leave summary' }));

    expect(await screen.findByText('No leave recorded for this year.')).toBeInTheDocument();
  });

  it('charts the payroll net pay per month with formatted money', async () => {
    const user = userEvent.setup();
    installFetchMock(
      reportRoutes({
        [`GET /api/reports/payroll-summary?year=${String(YEAR)}`]: {
          body: [
            { month: 3, grossEarnings: 18665.18, insurance: 1386, tax: 1527.92, netPay: 15751.26, payslipCount: 1 },
          ],
        },
      }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Payroll summary' }));

    const chart = await screen.findByRole('table', { name: 'Net pay per month' });
    const row = within(chart).getByRole('row', { name: /March/ });
    expect(within(row).getByRole('cell', { name: '15,751.26' })).toBeInTheDocument();
  });

  it('shows the empty state when no payroll run was finalized', async () => {
    const user = userEvent.setup();
    installFetchMock(
      reportRoutes({ [`GET /api/reports/payroll-summary?year=${String(YEAR)}`]: { body: [] } }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Payroll summary' }));

    expect(await screen.findByText('No finalized payroll run in this year.')).toBeInTheDocument();
  });

  it('asks the attendance summary for the current year and month', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      reportRoutes({
        [`GET /api/reports/attendance-summary?year=${String(YEAR)}&month=${String(MONTH)}`]: {
          body: [{ department: 'Finance', lateDays: 2, absentDays: 1, overtimeMinutes: 95 }],
        },
      }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Attendance summary' }));

    const chart = await screen.findByRole('table', { name: 'Absent days per department' });
    expect(within(chart).getByRole('row', { name: /Finance/ })).toBeInTheDocument();
    expect(await screen.findByText('1h 35m')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/reports/attendance-summary')).toEqual([
      `/api/reports/attendance-summary?year=${String(YEAR)}&month=${String(MONTH)}`,
    ]);
  });

  it('shows the empty state when no attendance was recorded in the month', async () => {
    const user = userEvent.setup();
    installFetchMock(
      reportRoutes({
        [`GET /api/reports/attendance-summary?year=${String(YEAR)}&month=${String(MONTH)}`]: {
          body: [],
        },
      }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('tab', { name: 'Attendance summary' }));

    expect(await screen.findByText('No attendance recorded for this month.')).toBeInTheDocument();
  });

  it('exports the headcount as an authenticated blob', async () => {
    const user = userEvent.setup();
    const saved: Blob[] = [];
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: (blob: Blob) => {
        saved.push(blob);
        return 'blob:headcount';
      },
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: () => undefined,
    });
    const mock = installFetchMock(
      reportRoutes({ 'GET /api/reports/headcount.xlsx': { blob: new Blob(['xlsx']) } }),
    );

    renderApp('/reports');
    await user.click(await screen.findByRole('button', { name: 'Download XLSX' }));

    expect(await screen.findByText('Headcount exported.')).toBeInTheDocument();
    const call = mock.callsTo('GET /api/reports/headcount.xlsx')[0];
    expect(call.headers.Authorization).toBe(`Bearer ${TOKEN}`);
    expect(saved).toHaveLength(1);
  });
});
