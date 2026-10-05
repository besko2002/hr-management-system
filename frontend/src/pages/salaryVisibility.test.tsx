import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeEmployee, makeEmployeeView, makePage, makeTeamMember, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

/**
 * The privacy rule under test: a salary is rendered only when the API payload actually
 * carries a numeric `salary`. A missing key, or a null value, must leave no trace at
 * all — no "Monthly salary" row, no 0, no "—" standing in for a hidden number.
 */

const SALARY_LABEL = /monthly salary/i;

function detailRoutes(
  employee: unknown,
  overrides: Record<string, RouteHandler> = {},
): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/employees/emp-1': { body: employee },
    'GET /api/departments': { body: [{ id: 'dep-1', name: 'Finance', createdAt: '2023-01-01T00:00:00Z' }] },
    'GET /api/employees': { body: makePage([makeEmployee()]) },
    'GET /api/audit/employees/emp-1': { body: makePage([]) },
    ...overrides,
  };
}

describe('salary visibility', () => {
  it('renders the salary on my profile when the payload carries one', async () => {
    installFetchMock({
      ...signedIn(),
      'GET /api/employees/me': { body: makeEmployee({ salary: 18500 }) },
    });

    renderApp('/profile');

    expect(await screen.findByRole('heading', { name: /my profile/i })).toBeInTheDocument();
    expect(await screen.findByText(SALARY_LABEL)).toBeInTheDocument();
    expect(screen.getByText('18,500.00')).toBeInTheDocument();
  });

  it('renders no salary row on my profile when the key is absent from the payload', async () => {
    installFetchMock({
      ...signedIn(),
      'GET /api/employees/me': { body: makeEmployeeView() },
    });

    renderApp('/profile');

    // Wait for the record itself, so the absence below is a real absence, not a race.
    expect(await screen.findByText('EMP-0001')).toBeInTheDocument();
    expect(screen.queryByText(SALARY_LABEL)).not.toBeInTheDocument();
    expect(screen.queryByText('18,500.00')).not.toBeInTheDocument();
    expect(screen.queryByText('0.00')).not.toBeInTheDocument();
    expect(screen.queryByText(/hidden/i)).not.toBeInTheDocument();
  });

  it('renders no salary row on my profile when the value is null', async () => {
    installFetchMock({
      ...signedIn(),
      'GET /api/employees/me': { body: makeEmployee({ salary: null }) },
    });

    renderApp('/profile');

    expect(await screen.findByText('EMP-0001')).toBeInTheDocument();
    expect(screen.queryByText(SALARY_LABEL)).not.toBeInTheDocument();
    expect(screen.queryByText('0.00')).not.toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('renders the salary on the employee detail page when HR receives one', async () => {
    installFetchMock(detailRoutes(makeEmployee({ salary: 22750 })));

    renderApp('/employees/emp-1');

    expect(await screen.findByRole('heading', { name: 'Nadia Hassan' })).toBeInTheDocument();
    expect(screen.getByText(SALARY_LABEL)).toBeInTheDocument();
    expect(screen.getByText('22,750.00')).toBeInTheDocument();
  });

  it('renders no salary row on the employee detail page when the payload omits the key', async () => {
    installFetchMock(detailRoutes(makeEmployeeView()));

    renderApp('/employees/emp-1');

    expect(await screen.findByRole('heading', { name: 'Nadia Hassan' })).toBeInTheDocument();
    expect(screen.getByText(/EMP-0001 · nadia@hr\.local/)).toBeInTheDocument();
    expect(screen.queryByText(SALARY_LABEL)).not.toBeInTheDocument();
    expect(screen.queryByText('18,500.00')).not.toBeInTheDocument();
  });

  it('never shows a salary in the manager team view, even if one leaks into the payload', async () => {
    const member = { ...makeTeamMember(), salary: 31000 };
    installFetchMock({
      ...signedIn({ team: [makeTeamMember()] }),
      'GET /api/employees/me/team': { body: [member] },
    });

    renderApp('/team');

    const table = await screen.findByRole('table');
    expect(within(table).getByText('Youssef Adel')).toBeInTheDocument();
    expect(screen.queryByText(SALARY_LABEL)).not.toBeInTheDocument();
    expect(screen.queryByText('31,000.00')).not.toBeInTheDocument();
    expect(screen.queryByText('31000')).not.toBeInTheDocument();
  });

  it('keeps the salary hidden in the whole-subtree team scope too', async () => {
    const user = userEvent.setup();
    installFetchMock({
      ...signedIn({ team: [makeTeamMember()] }),
      'GET /api/employees/me/team/all': {
        body: [{ ...makeTeamMember({ depth: 2 }), salary: 31000 }],
      },
    });

    renderApp('/team');
    await user.click(await screen.findByRole('button', { name: 'All reports' }));

    expect(await screen.findByText('Youssef Adel')).toBeInTheDocument();
    expect(screen.queryByText('31,000.00')).not.toBeInTheDocument();
    expect(screen.queryByText(SALARY_LABEL)).not.toBeInTheDocument();
  });
});
