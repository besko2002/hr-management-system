import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeAttendanceRange, makeBalance, makeLeaveRequest, makePage, makeTeamMember, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';

const HR_PATHS = [
  '/employees',
  '/departments',
  '/holidays',
  '/leave-overview',
  '/payroll',
  '/reports',
];
const MANAGER_PATHS = ['/team', '/approvals', '/team/calendar', '/team/today'];

const ADMIN_NAV = ['Employees', 'Departments', 'Holidays', 'Leave overview', 'Payroll', 'Reports'];
const TEAM_NAV = ['My team', 'Approvals', 'Team calendar', 'Team today'];

describe('role-based access', () => {
  it.each(HR_PATHS)('answers an employee asking for %s with the 403 page', async (path) => {
    installFetchMock(signedIn());

    renderApp(path);

    expect(await screen.findByRole('heading', { name: /do not have access/i })).toBeInTheDocument();
  });

  it.each(MANAGER_PATHS)(
    'answers an employee with no reports asking for %s with the 403 page',
    async (path) => {
      installFetchMock(signedIn({ team: [] }));

      renderApp(path);

      expect(await screen.findByRole('heading', { name: /do not have access/i })).toBeInTheDocument();
    },
  );

  it('hides every manager menu item from an employee with no direct report', async () => {
    installFetchMock({
      ...signedIn({ team: [] }),
      'GET /api/attendance/me': { body: makeAttendanceRange() },
      'GET /api/leave/balances/me': { body: [makeBalance()] },
      'GET /api/leave/requests/me': { body: makePage([makeLeaveRequest()]) },
    });

    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Main navigation' });
    for (const label of [...TEAM_NAV, ...ADMIN_NAV]) {
      expect(within(nav).queryByRole('link', { name: label })).not.toBeInTheDocument();
    }
    expect(within(nav).getByRole('link', { name: 'Leave' })).toBeInTheDocument();
  });

  it('shows the manager menu items once the team probe returns a direct report', async () => {
    installFetchMock({
      ...signedIn({ team: [makeTeamMember()] }),
      'GET /api/leave/requests/pending?page=0&size=10': { body: makePage([]) },
    });

    renderApp('/approvals');

    const nav = await screen.findByRole('navigation', { name: 'Main navigation' });
    for (const label of TEAM_NAV) {
      expect(await within(nav).findByRole('link', { name: label })).toBeInTheDocument();
    }
    // A manager who is not HR still gets no administration menu.
    for (const label of ADMIN_NAV) {
      expect(within(nav).queryByRole('link', { name: label })).not.toBeInTheDocument();
    }
    expect(screen.getByRole('heading', { name: 'Approvals' })).toBeInTheDocument();
  });

  it('shows HR both the administration and the team menus', async () => {
    installFetchMock({
      ...signedIn({ role: 'HR', team: [] }),
      'GET /api/departments': { body: [] },
      'GET /api/employees': { body: makePage([]) },
    });

    renderApp('/employees');

    const nav = await screen.findByRole('navigation', { name: 'Main navigation' });
    for (const label of ADMIN_NAV) {
      expect(await within(nav).findByRole('link', { name: label })).toBeInTheDocument();
    }
    // HR reaches the team screens too, because those endpoints answer company-wide.
    expect(within(nav).getByRole('link', { name: 'Approvals' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Employees' })).toBeInTheDocument();
  });

  it('sends the user back to login with the session-expired notice on a 401 in-app', async () => {
    installFetchMock({
      ...signedIn(),
      'GET /api/leave/balances/me': {
        status: 401,
        body: { status: 401, message: 'Token expired' },
      },
      'GET /api/leave/requests/me?page=0&size=10': { body: makePage([]) },
    });

    renderApp('/leave');

    expect(
      await screen.findByText('Your session expired. Please sign in again to continue.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Main navigation' })).not.toBeInTheDocument();
  });

  it('keeps the admin-only area closed to HR', async () => {
    installFetchMock(signedIn({ role: 'HR' }));

    renderApp('/admin');

    expect(await screen.findByRole('heading', { name: /do not have access/i })).toBeInTheDocument();
  });
});
