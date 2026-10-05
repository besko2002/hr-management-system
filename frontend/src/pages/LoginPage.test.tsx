import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../api/token';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const AFTER_LOGIN: Record<string, RouteHandler> = {
  'GET /api/employees/me/team': { body: [] },
  'GET /api/attendance/me': { body: { days: [], workingDays: 0 } },
  'GET /api/leave/balances/me': { body: [] },
  'GET /api/leave/requests/me': { body: EMPTY_PAGE },
};

function loginResponse(overrides: Record<string, unknown> = {}): unknown {
  return {
    accessToken: 'fresh-token',
    tokenType: 'Bearer',
    expiresIn: 28800,
    user: {
      id: 'emp-1',
      employeeNumber: 'EMP-0001',
      fullName: 'Nadia Hassan',
      email: 'nadia@hr.local',
      role: 'EMPLOYEE',
      jobTitle: 'Analyst',
      departmentName: 'Finance',
      mustChangePassword: false,
      ...overrides,
    },
  };
}

describe('login page', () => {
  it('validates the form before calling the API', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({});

    renderApp('/login');
    await user.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('rejects an email that is not an email without a round trip', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({});

    renderApp('/login');
    await user.type(screen.getByLabelText(/work email/i), 'not-an-email');
    await user.type(screen.getByLabelText(/password/i), 'Admin@12345');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('signs in, stores the token and lands on the dashboard', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      'POST /api/auth/login': { body: loginResponse() },
      ...AFTER_LOGIN,
    });

    renderApp('/login');
    await user.type(screen.getByLabelText(/work email/i), 'nadia@hr.local');
    await user.type(screen.getByLabelText(/password/i), 'Secret@12345');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: /hello, nadia hassan/i })).toBeInTheDocument();
    expect(window.localStorage.getItem(TOKEN_KEY)).toBe('fresh-token');
    expect(mock.callsTo('POST /api/auth/login')[0].body).toEqual({
      email: 'nadia@hr.local',
      password: 'Secret@12345',
    });
  });

  it('shows the backend message when the credentials are wrong', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'POST /api/auth/login': {
        status: 401,
        body: { status: 401, message: 'Invalid email or password' },
      },
    });

    renderApp('/login');
    await user.type(screen.getByLabelText(/work email/i), 'nadia@hr.local');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(window.localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('sends a must-change-password account straight to the password screen', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'POST /api/auth/login': { body: loginResponse({ mustChangePassword: true }) },
    });

    renderApp('/login');
    await user.type(screen.getByLabelText(/work email/i), 'nadia@hr.local');
    await user.type(screen.getByLabelText(/password/i), 'Temp@12345');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(
      await screen.findByRole('heading', { name: /change your password to continue/i }),
    ).toBeInTheDocument();
  });
});
