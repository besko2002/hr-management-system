import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeEmployee, makePage, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const TEMP_PASSWORD = 'Tmp-9f3a!2bQ';

function createRoutes(
  overrides: Record<string, RouteHandler> = {},
  options: { role?: 'HR' | 'ADMIN' } = {},
): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: options.role ?? 'HR' }),
    'GET /api/departments': { body: [{ id: 'dep-1', name: 'Finance', createdAt: '2023-01-01T00:00:00Z' }] },
    'GET /api/employees': { body: makePage([makeEmployee({ id: 'emp-9', fullName: 'Omar Said' })]) },
    'POST /api/employees': {
      status: 201,
      body: { employee: makeEmployee({ id: 'emp-77' }), temporaryPassword: TEMP_PASSWORD },
    },
    'GET /api/employees/emp-77': { body: makeEmployee({ id: 'emp-77' }) },
    'GET /api/audit/employees/emp-77': { body: makePage([]) },
    ...overrides,
  };
}

async function fillRequiredFields(): Promise<void> {
  const user = userEvent.setup();
  await user.clear(await screen.findByLabelText(/full name/i));
  await user.type(screen.getByLabelText(/full name/i), 'Laila Kamel');
  await user.type(screen.getByLabelText(/work email/i), 'laila@hr.local');
}

describe('create employee', () => {
  it('posts the typed record and shows the temporary password once', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(createRoutes());

    renderApp('/employees/new');
    await fillRequiredFields();
    await user.click(screen.getByRole('button', { name: 'Create employee' }));

    const dialog = await screen.findByRole('dialog', { name: 'Temporary password' });
    expect(within(dialog).getByLabelText('Temporary password')).toHaveTextContent(TEMP_PASSWORD);

    const posted = mock.callsTo('POST /api/employees');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toMatchObject({
      fullName: 'Laila Kamel',
      email: 'laila@hr.local',
      role: 'EMPLOYEE',
      jobTitle: null,
      departmentId: null,
      managerId: null,
      salary: null,
    });
  });

  it('removes the temporary password from the page once the modal is closed', async () => {
    const user = userEvent.setup();
    installFetchMock(createRoutes());

    renderApp('/employees/new');
    await fillRequiredFields();
    await user.click(screen.getByRole('button', { name: 'Create employee' }));
    await screen.findByRole('dialog', { name: 'Temporary password' });
    await user.click(screen.getByRole('button', { name: 'I have shared it' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText(TEMP_PASSWORD)).not.toBeInTheDocument();
    // Closing hands over to the new record, with no way back to the secret.
    expect(await screen.findByRole('link', { name: 'Back to employees' })).toBeInTheDocument();
  });

  it('offers no ADMIN role option to an HR user', async () => {
    installFetchMock(createRoutes({}, { role: 'HR' }));

    renderApp('/employees/new');

    const select = await screen.findByLabelText(/^role/i);
    expect(within(select).getByRole('option', { name: 'EMPLOYEE' })).toBeInTheDocument();
    expect(within(select).getByRole('option', { name: 'HR' })).toBeInTheDocument();
    expect(within(select).queryByRole('option', { name: 'ADMIN' })).not.toBeInTheDocument();
    expect(screen.getByText('Only an ADMIN can create another ADMIN.')).toBeInTheDocument();
  });

  it('offers the ADMIN role option to an ADMIN user', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(createRoutes({}, { role: 'ADMIN' }));

    renderApp('/employees/new');
    const select = await screen.findByLabelText(/^role/i);
    expect(within(select).getByRole('option', { name: 'ADMIN' })).toBeInTheDocument();
    expect(screen.queryByText('Only an ADMIN can create another ADMIN.')).not.toBeInTheDocument();

    await user.selectOptions(select, 'ADMIN');
    await fillRequiredFields();
    await user.click(screen.getByRole('button', { name: 'Create employee' }));

    expect(mock.callsTo('POST /api/employees')[0].body).toMatchObject({ role: 'ADMIN' });
  });

  it('blocks the submit and names the missing fields', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(createRoutes());

    renderApp('/employees/new');
    await user.click(await screen.findByRole('button', { name: 'Create employee' }));

    expect(await screen.findByText('Full name is required.')).toBeInTheDocument();
    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/employees')).toHaveLength(0);
  });

  it('rejects a malformed salary before posting', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(createRoutes());

    renderApp('/employees/new');
    await fillRequiredFields();
    await user.type(screen.getByLabelText(/monthly salary/i), '12.345');
    await user.click(screen.getByRole('button', { name: 'Create employee' }));

    expect(
      await screen.findByText('Salary must be a positive amount with at most 2 decimals.'),
    ).toBeInTheDocument();
    expect(mock.callsTo('POST /api/employees')).toHaveLength(0);
  });

  it('shows the server message when the email is already taken', async () => {
    const user = userEvent.setup();
    installFetchMock(
      createRoutes({
        'POST /api/employees': {
          status: 409,
          body: { status: 409, message: 'An employee with this email already exists' },
        },
      }),
    );

    renderApp('/employees/new');
    await fillRequiredFields();
    await user.click(screen.getByRole('button', { name: 'Create employee' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An employee with this email already exists',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
