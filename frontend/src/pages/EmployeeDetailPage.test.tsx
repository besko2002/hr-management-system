import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeAudit, makeEmployee, makePage, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

function detailRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/employees/emp-1': { body: makeEmployee() },
    'GET /api/departments': { body: [{ id: 'dep-1', name: 'Finance', createdAt: '2023-01-01T00:00:00Z' }] },
    'GET /api/employees': {
      body: makePage([
        makeEmployee(),
        makeEmployee({ id: 'emp-9', fullName: 'Omar Said', employeeNumber: 'EMP-0009' }),
      ]),
    },
    'GET /api/audit/employees/emp-1': { body: makePage([makeAudit()]) },
    ...overrides,
  };
}

describe('employee detail', () => {
  it('asks for confirmation before terminating and only then posts', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      detailRoutes({
        'POST /api/employees/emp-1/terminate': {
          body: makeEmployee({ status: 'TERMINATED', terminatedAt: '2025-05-01T00:00:00Z' }),
        },
      }),
    );

    renderApp('/employees/emp-1');
    await user.click(await screen.findByRole('button', { name: 'Terminate employee' }));

    const dialog = await screen.findByRole('dialog', { name: 'Terminate Nadia Hassan?' });
    expect(mock.callsTo('POST /api/employees/emp-1/terminate')).toHaveLength(0);

    await user.click(within(dialog).getByRole('button', { name: 'Terminate' }));

    expect(mock.callsTo('POST /api/employees/emp-1/terminate')).toHaveLength(1);
    expect(await screen.findByText('Terminated')).toBeInTheDocument();
  });

  it('posts nothing when the termination dialog is dismissed', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(detailRoutes());

    renderApp('/employees/emp-1');
    await user.click(await screen.findByRole('button', { name: 'Terminate employee' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /^(cancel|keep)/i }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mock.callsTo('POST /api/employees/emp-1/terminate')).toHaveLength(0);
  });

  it('sets a new manager through PUT .../manager', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      detailRoutes({
        'PUT /api/employees/emp-1/manager': {
          body: makeEmployee({ managerId: 'emp-9', managerName: 'Omar Said' }),
        },
      }),
    );

    renderApp('/employees/emp-1');
    await user.selectOptions(await screen.findByLabelText(/reports to/i), 'emp-9');

    const put = mock.callsTo('PUT /api/employees/emp-1/manager');
    expect(put).toHaveLength(1);
    expect(put[0].body).toEqual({ managerId: 'emp-9' });
  });

  it('shows the 409 cycle message when the manager change is refused', async () => {
    const user = userEvent.setup();
    installFetchMock(
      detailRoutes({
        'PUT /api/employees/emp-1/manager': {
          status: 409,
          body: { status: 409, message: 'This manager assignment would create a cycle' },
        },
      }),
    );

    renderApp('/employees/emp-1');
    await user.selectOptions(await screen.findByLabelText(/reports to/i), 'emp-9');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This manager assignment would create a cycle',
    );
  });

  it('sends null when the manager is cleared to make an org root', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      detailRoutes({
        'PUT /api/employees/emp-1/manager': { body: makeEmployee({ managerId: null, managerName: null }) },
      }),
    );

    renderApp('/employees/emp-1');
    await user.selectOptions(await screen.findByLabelText(/reports to/i), '');

    expect(mock.callsTo('PUT /api/employees/emp-1/manager')[0].body).toEqual({ managerId: null });
  });

  it('renders the audit trail with the field, both values and who changed it', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      detailRoutes({
        'GET /api/audit/employees/emp-1': {
          body: makePage([
            makeAudit({ field: 'salary', before: '17000.00', after: '18500.00' }),
            makeAudit({
              revision: 13,
              field: 'jobTitle',
              before: 'Junior Analyst',
              after: 'Analyst',
              changedBy: 'hr@hr.local',
            }),
          ]),
        },
      }),
    );

    renderApp('/employees/emp-1');
    await user.click(await screen.findByRole('tab', { name: 'Audit' }));

    const table = await screen.findByRole('table', { name: /audited field changes/i });
    const salaryRow = within(table).getByRole('row', { name: /17,?000\.00/ });
    expect(within(salaryRow).getByText('18500.00')).toBeInTheDocument();
    expect(within(salaryRow).getByText('admin@hr.local')).toBeInTheDocument();
    const titleRow = within(table).getByRole('row', { name: /Junior Analyst/ });
    expect(within(titleRow).getByText('hr@hr.local')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/audit/employees/emp-1')).toContain(
      '/api/audit/employees/emp-1?page=0&size=20',
    );
  });

  it('does not fetch the audit trail before the tab is opened', async () => {
    const mock = installFetchMock(detailRoutes());

    renderApp('/employees/emp-1');
    await screen.findByRole('heading', { name: 'Nadia Hassan' });

    expect(mock.urlsFor('GET', '/api/audit/employees/emp-1')).toHaveLength(0);
  });

  it('shows the audit empty state when nothing tracked has changed', async () => {
    const user = userEvent.setup();
    installFetchMock(detailRoutes({ 'GET /api/audit/employees/emp-1': { body: makePage([]) } }));

    renderApp('/employees/emp-1');
    await user.click(await screen.findByRole('tab', { name: 'Audit' }));

    expect(await screen.findByText('No tracked change yet.')).toBeInTheDocument();
  });
});
