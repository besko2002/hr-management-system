import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeEmployee, makePage, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const DEFAULT_URL = '/api/employees?page=0&size=20&sort=fullName%2Casc';

function employeesRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/departments': {
      body: [
        { id: 'dep-1', name: 'Finance', createdAt: '2023-01-01T00:00:00Z' },
        { id: 'dep-2', name: 'Engineering', createdAt: '2023-01-01T00:00:00Z' },
      ],
    },
    'GET /api/employees': { body: makePage([makeEmployee()], { totalElements: 42, totalPages: 3 }) },
    ...overrides,
  };
}

describe('employees list', () => {
  it('loads the first page sorted by name and lists the records', async () => {
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');

    const table = await screen.findByRole('table', { name: /employees matching the current filters/i });
    expect(within(table).getByRole('link', { name: 'Nadia Hassan' })).toHaveAttribute(
      'href',
      '/employees/emp-1',
    );
    expect(mock.urlsFor('GET', '/api/employees')).toEqual([DEFAULT_URL]);
  });

  it('puts the trimmed search term in the q parameter after the debounce', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');
    await screen.findByRole('table');
    await user.type(screen.getByLabelText('Search'), '  nadia  ');

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?q=nadia&page=0&size=20&sort=fullName%2Casc',
      );
    });
  });

  it('sends departmentId and status when both filters are chosen', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');
    await screen.findByRole('table');
    await user.selectOptions(screen.getByLabelText('Department'), 'dep-2');
    await user.selectOptions(screen.getByLabelText('Status'), 'TERMINATED');

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?departmentId=dep-2&status=TERMINATED&page=0&size=20&sort=fullName%2Casc',
      );
    });
  });

  it('sends the chosen sort key', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');
    await screen.findByRole('table');
    await user.selectOptions(screen.getByLabelText('Sort'), 'hireDate,desc');

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?page=0&size=20&sort=hireDate%2Cdesc',
      );
    });
  });

  it('asks for the next page when Next is pressed', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Next' }));

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?page=1&size=20&sort=fullName%2Casc',
      );
    });
  });

  it('resets to the first page when a filter changes after paging', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(employeesRoutes());

    renderApp('/employees');
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?page=1&size=20&sort=fullName%2Casc',
      );
    });
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE');

    await waitFor(() => {
      expect(mock.urlsFor('GET', '/api/employees')).toContain(
        '/api/employees?status=ACTIVE&page=0&size=20&sort=fullName%2Casc',
      );
    });
  });

  it('offers the departments it fetched as filter options', async () => {
    installFetchMock(employeesRoutes());

    renderApp('/employees');

    const select = await screen.findByLabelText('Department');
    expect(within(select).getByRole('option', { name: 'All departments' })).toHaveValue('');
    expect(within(select).getByRole('option', { name: 'Engineering' })).toHaveValue('dep-2');
  });

  it('shows the empty state when no record matches', async () => {
    installFetchMock(employeesRoutes({ 'GET /api/employees': { body: makePage([]) } }));

    renderApp('/employees');

    expect(await screen.findByText('No employee matches these filters.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
