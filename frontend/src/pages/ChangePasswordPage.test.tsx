import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeEmployee, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const DASHBOARD_ROUTES: Record<string, RouteHandler> = {
  'GET /api/employees/me/team': { body: [] },
  'GET /api/attendance/me': { body: { days: [], workingDays: 0 } },
  'GET /api/leave/balances/me': { body: [] },
  'GET /api/leave/requests/me': { body: EMPTY_PAGE },
};

function forcedSession(): Record<string, RouteHandler> {
  const routes = signedIn();
  return {
    ...routes,
    'GET /api/employees/me': { body: makeEmployee({ mustChangePassword: true }) },
  };
}

describe('forced change-password flow', () => {
  it('posts the change and unlocks the rest of the app', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      ...forcedSession(),
      'POST /api/auth/change-password': {
        body: {
          id: 'emp-1',
          employeeNumber: 'EMP-0001',
          fullName: 'Nadia Hassan',
          email: 'nadia@hr.local',
          role: 'EMPLOYEE',
          jobTitle: 'Analyst',
          departmentName: 'Finance',
          mustChangePassword: false,
        },
      },
      ...DASHBOARD_ROUTES,
    });

    renderApp('/');

    await user.type(await screen.findByLabelText(/current password/i), 'Temp@12345');
    await user.type(screen.getByLabelText('New password *'), 'Brand@New123');
    await user.type(screen.getByLabelText(/repeat new password/i), 'Brand@New123');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByRole('heading', { name: /hello/i })).toBeInTheDocument();
    expect(mock.callsTo('POST /api/auth/change-password')[0].body).toEqual({
      currentPassword: 'Temp@12345',
      newPassword: 'Brand@New123',
    });
  });

  it('refuses a new password shorter than the backend minimum', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(forcedSession());

    renderApp('/');

    await user.type(await screen.findByLabelText(/current password/i), 'Temp@12345');
    await user.type(screen.getByLabelText('New password *'), 'short');
    await user.type(screen.getByLabelText(/repeat new password/i), 'short');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(
      screen.getByText('The new password must be at least 8 characters.'),
    ).toBeInTheDocument();
    expect(mock.callsTo('POST /api/auth/change-password')).toHaveLength(0);
  });

  it('refuses two passwords that do not match', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(forcedSession());

    renderApp('/');

    await user.type(await screen.findByLabelText(/current password/i), 'Temp@12345');
    await user.type(screen.getByLabelText('New password *'), 'Brand@New123');
    await user.type(screen.getByLabelText(/repeat new password/i), 'Brand@New124');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(screen.getByText('The two passwords do not match.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/auth/change-password')).toHaveLength(0);
  });

  it('shows the backend message when the current password is wrong', async () => {
    const user = userEvent.setup();
    installFetchMock({
      ...forcedSession(),
      'POST /api/auth/change-password': {
        status: 400,
        body: { status: 400, message: 'The current password is wrong' },
      },
    });

    renderApp('/');

    await user.type(await screen.findByLabelText(/current password/i), 'Nope@12345');
    await user.type(screen.getByLabelText('New password *'), 'Brand@New123');
    await user.type(screen.getByLabelText(/repeat new password/i), 'Brand@New123');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The current password is wrong');
  });
});
