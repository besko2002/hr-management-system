import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const FINANCE = { id: 'dep-1', name: 'Finance', createdAt: '2023-01-02T08:00:00Z' };

function departmentRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/departments': { body: [FINANCE] },
    ...overrides,
  };
}

describe('departments', () => {
  it('lists the departments with their creation date', async () => {
    installFetchMock(departmentRoutes());

    renderApp('/departments');

    const table = await screen.findByRole('table', { name: 'Departments' });
    const row = within(table).getByRole('row', { name: /finance/i });
    expect(within(row).getByText('2 Jan 2023')).toBeInTheDocument();
  });

  it('creates a department with the trimmed name and reloads the list', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      departmentRoutes({
        'POST /api/departments': {
          status: 201,
          body: { id: 'dep-2', name: 'Engineering', createdAt: '2025-01-01T08:00:00Z' },
        },
      }),
    );

    renderApp('/departments');
    await user.type(await screen.findByLabelText(/^name/i), '  Engineering  ');
    await user.click(screen.getByRole('button', { name: 'Add department' }));

    const posted = mock.callsTo('POST /api/departments');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({ name: 'Engineering' });
    expect(mock.urlsFor('GET', '/api/departments').length).toBeGreaterThanOrEqual(2);
  });

  it('refuses an empty name without calling the API', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(departmentRoutes());

    renderApp('/departments');
    await user.click(await screen.findByRole('button', { name: 'Add department' }));

    expect(await screen.findByText('Department name is required.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/departments')).toHaveLength(0);
  });

  it('asks for confirmation before deleting', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(departmentRoutes({ 'DELETE /api/departments/dep-1': { status: 204 } }));

    renderApp('/departments');
    await user.click(await screen.findByRole('button', { name: 'Delete Finance' }));

    const dialog = await screen.findByRole('dialog', { name: 'Delete Finance?' });
    expect(mock.callsTo('DELETE /api/departments/dep-1')).toHaveLength(0);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(mock.callsTo('DELETE /api/departments/dep-1')).toHaveLength(1);
  });

  it('shows the 409 message when the department still has employees', async () => {
    const user = userEvent.setup();
    installFetchMock(
      departmentRoutes({
        'DELETE /api/departments/dep-1': {
          status: 409,
          body: { status: 409, message: 'This department still has 4 employees' },
        },
      }),
    );

    renderApp('/departments');
    await user.click(await screen.findByRole('button', { name: 'Delete Finance' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This department still has 4 employees',
    );
    // The department is still listed, since the delete was refused.
    expect(screen.getByRole('row', { name: /finance/i })).toBeInTheDocument();
  });

  it('renames a department through PUT', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      departmentRoutes({
        'PUT /api/departments/dep-1': { body: { ...FINANCE, name: 'Finance & Legal' } },
      }),
    );

    renderApp('/departments');
    await user.click(await screen.findByRole('button', { name: 'Rename Finance' }));
    const input = await screen.findByLabelText('New name for Finance');
    await user.clear(input);
    await user.type(input, 'Finance & Legal');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(mock.callsTo('PUT /api/departments/dep-1')[0].body).toEqual({ name: 'Finance & Legal' });
  });

  it('shows the empty state when there is no department yet', async () => {
    installFetchMock(departmentRoutes({ 'GET /api/departments': { body: [] } }));

    renderApp('/departments');

    expect(await screen.findByText('No department yet.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
