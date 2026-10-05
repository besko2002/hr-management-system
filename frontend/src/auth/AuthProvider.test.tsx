import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TOKEN_KEY, writeToken } from '../api/token';
import { makeEmployee } from '../test/fixtures';
import { installFetchMock, renderApp, renderAppStrict } from '../test/helpers';

const DASHBOARD_ROUTES = {
  'GET /api/attendance/me': { body: { days: [], workingDays: 0 } },
  'GET /api/leave/balances/me': { body: [] },
  'GET /api/leave/requests/me': { body: { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 } },
};

describe('session restore', () => {
  it('restores the session from a stored token even though StrictMode runs effects twice', async () => {
    writeToken('stored-token');
    const mock = installFetchMock({
      'GET /api/employees/me': { body: makeEmployee({ fullName: 'Nadia Hassan' }) },
      'GET /api/employees/me/team': { body: [] },
      ...DASHBOARD_ROUTES,
    });

    renderAppStrict('/');

    // Regression guard: marking the token "resolved" before /api/employees/me came back
    // left the second StrictMode effect skipping the call, and the app never left the
    // loading state.
    expect(await screen.findByText('Nadia Hassan')).toBeInTheDocument();
    expect(screen.queryByText('Restoring your session…')).not.toBeInTheDocument();
    expect(mock.callsTo('GET /api/employees/me').length).toBeGreaterThanOrEqual(1);
  });

  it('shows the dashboard for a stored token outside StrictMode too', async () => {
    writeToken('stored-token');
    installFetchMock({
      'GET /api/employees/me': { body: makeEmployee() },
      'GET /api/employees/me/team': { body: [] },
      ...DASHBOARD_ROUTES,
    });

    renderApp('/');

    expect(await screen.findByRole('heading', { name: /hello, nadia hassan/i })).toBeInTheDocument();
  });

  it('clears the session and explains the expiry when the restore call is 401', async () => {
    writeToken('expired-token');
    installFetchMock({
      'GET /api/employees/me': { status: 401, body: { status: 401, message: 'Token expired' } },
    });

    renderApp('/');

    expect(await screen.findByText(/session expired/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() => {
      expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull();
    });
  });

  it('drops the session without a notice when the restore call fails for another reason', async () => {
    writeToken('stored-token');
    installFetchMock({
      'GET /api/employees/me': { status: 500, body: { status: 500, message: 'Boom' } },
    });

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument();
  });

  it('signs the user out and returns to the login page', async () => {
    writeToken('stored-token');
    installFetchMock({
      'GET /api/employees/me': { body: makeEmployee() },
      'GET /api/employees/me/team': { body: [] },
      ...DASHBOARD_ROUTES,
    });

    renderApp('/');
    (await screen.findByRole('button', { name: 'Sign out' })).click();

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() => {
      expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull();
    });
  });
});
