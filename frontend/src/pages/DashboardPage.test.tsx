import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import {
  makeAttendanceRange,
  makeBalance,
  makeDay,
  makeLeaveRequest,
  makePage,
  signedIn,
} from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

function dashboardRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn(),
    'GET /api/attendance/me': {
      body: makeAttendanceRange({ days: [makeDay({ status: 'PRESENT', openSession: true })] }),
    },
    'GET /api/leave/balances/me': {
      body: [makeBalance(), makeBalance({ leaveType: 'SICK', entitledDays: 10, remainingDays: 9 })],
    },
    'GET /api/leave/requests/me': { body: makePage([makeLeaveRequest()]) },
    ...overrides,
  };
}

describe('dashboard', () => {
  it('shows today, the balances and the recent requests', async () => {
    installFetchMock(dashboardRoutes());

    renderApp('/');

    expect(await screen.findByRole('heading', { name: /hello, nadia hassan/i })).toBeInTheDocument();
    expect(await screen.findByText('Present')).toBeInTheDocument();
    expect(screen.getByText('You are currently checked in.')).toBeInTheDocument();
    expect(await screen.findByText('17 days')).toBeInTheDocument();
    expect(screen.getByText('9 days')).toBeInTheDocument();
    const recent = await screen.findByRole('table', { name: /five most recent leave requests/i });
    expect(within(recent).getByText('ANNUAL')).toBeInTheDocument();
  });

  it('checks in and reloads today', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      dashboardRoutes({
        'POST /api/attendance/check-in': { status: 201, body: { id: 'session-1' } },
      }),
    );

    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Check in' }));

    expect(await screen.findByText('Checked in.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/attendance/check-in')).toHaveLength(1);
    // The day is refetched so the new session shows up.
    expect(mock.urlsFor('GET', '/api/attendance/me').length).toBeGreaterThanOrEqual(2);
  });

  it('renders the 409 message when a session is already open', async () => {
    const user = userEvent.setup();
    installFetchMock(
      dashboardRoutes({
        'POST /api/attendance/check-in': {
          status: 409,
          body: {
            status: 409,
            message: 'You already have an open attendance session today; check out first',
          },
        },
      }),
    );

    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Check in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You already have an open attendance session today; check out first',
    );
  });

  it('checks out and confirms it', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      dashboardRoutes({ 'POST /api/attendance/check-out': { body: { id: 'session-1' } } }),
    );

    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Check out' }));

    expect(await screen.findByText('Checked out.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/attendance/check-out')).toHaveLength(1);
  });

  it('renders the 409 message when there is nothing to check out of', async () => {
    const user = userEvent.setup();
    installFetchMock(
      dashboardRoutes({
        'POST /api/attendance/check-out': {
          status: 409,
          body: { status: 409, message: 'There is no open attendance session today' },
        },
      }),
    );

    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Check out' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'There is no open attendance session today',
    );
  });

  it('shows an empty state when there is no leave request yet', async () => {
    installFetchMock(dashboardRoutes({ 'GET /api/leave/requests/me': { body: makePage([]) } }));

    renderApp('/');

    expect(await screen.findByText('No leave requests yet.')).toBeInTheDocument();
  });
});
