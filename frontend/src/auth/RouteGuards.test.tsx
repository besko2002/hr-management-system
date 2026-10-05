import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeEmployee, makeTeamMember, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const DASHBOARD_ROUTES: Record<string, RouteHandler> = {
  'GET /api/attendance/me': { body: { days: [], workingDays: 0 } },
  'GET /api/leave/balances/me': { body: [] },
  'GET /api/leave/requests/me': { body: EMPTY_PAGE },
};

describe('route guards', () => {
  it('sends an anonymous visitor to the login page', async () => {
    const mock = installFetchMock({});

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('sends an anonymous visitor following a deep link to the login page', async () => {
    installFetchMock({});

    renderApp('/payroll');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('keeps a signed-in employee away from the login page', async () => {
    installFetchMock({ ...signedIn(), ...DASHBOARD_ROUTES });

    renderApp('/login');

    expect(await screen.findByRole('heading', { name: /hello/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('hides the administration and team menus from a plain employee', async () => {
    installFetchMock({ ...signedIn(), ...DASHBOARD_ROUTES });

    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Employees' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Payroll' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Approvals' })).not.toBeInTheDocument();
  });

  it('answers an employee who guesses an HR route with the 403 page', async () => {
    installFetchMock({ ...signedIn() });

    renderApp('/employees');

    expect(
      await screen.findByRole('heading', { name: /do not have access/i }),
    ).toBeInTheDocument();
  });

  it('lets HR open the employees screen and shows the administration menu', async () => {
    installFetchMock({
      ...signedIn({ role: 'HR' }),
      'GET /api/employees': { body: EMPTY_PAGE },
      'GET /api/departments': { body: [] },
    });

    renderApp('/employees');

    expect(await screen.findByRole('heading', { name: 'Employees' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Payroll' })).toBeInTheDocument();
  });

  it('refuses the manager screens to an employee with no direct reports', async () => {
    installFetchMock({ ...signedIn({ team: [] }) });

    renderApp('/approvals');

    expect(
      await screen.findByRole('heading', { name: /do not have access/i }),
    ).toBeInTheDocument();
  });

  it('opens the manager screens once the team probe finds a direct report', async () => {
    installFetchMock({
      ...signedIn({ team: [makeTeamMember()] }),
      'GET /api/leave/requests/pending': { body: EMPTY_PAGE },
    });

    renderApp('/approvals');

    expect(await screen.findByRole('heading', { name: 'Approvals' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'My team' })).toBeInTheDocument();
  });

  it('refuses the admin-only area to an HR user', async () => {
    installFetchMock({ ...signedIn({ role: 'HR' }) });

    renderApp('/admin');

    expect(
      await screen.findByRole('heading', { name: /do not have access/i }),
    ).toBeInTheDocument();
  });

  it('shows a friendly not-found page for an unknown path', async () => {
    installFetchMock({ ...signedIn() });

    renderApp('/nowhere');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('forces the change-password screen before any other page', async () => {
    installFetchMock({
      'GET /api/employees/me': { body: makeEmployee({ mustChangePassword: true }) },
    });
    window.localStorage.setItem('hr.accessToken', 'token');

    renderApp('/leave');

    expect(
      await screen.findByRole('heading', { name: /change your password to continue/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Leave' })).not.toBeInTheDocument();
  });
});
