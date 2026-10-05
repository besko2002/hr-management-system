import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const EID = { id: 'hol-1', date: '2025-04-20', name: 'Sham El-Nessim' };

function holidayRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/leave/holidays': { body: [EID] },
    ...overrides,
  };
}

describe('holidays', () => {
  it('lists the registered holidays by date', async () => {
    const mock = installFetchMock(holidayRoutes());

    renderApp('/holidays');

    const table = await screen.findByRole('table', { name: 'Registered holidays' });
    const row = within(table).getByRole('row', { name: /20 Apr 2025/ });
    expect(within(row).getByText('Sham El-Nessim')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/leave/holidays')).toEqual(['/api/leave/holidays']);
  });

  it('creates a holiday and reloads the list', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      holidayRoutes({
        'POST /api/leave/holidays': {
          status: 201,
          body: { id: 'hol-2', date: '2025-07-23', name: 'Revolution Day' },
        },
      }),
    );

    renderApp('/holidays');
    await user.type(await screen.findByLabelText(/^date/i), '2025-07-23');
    await user.type(screen.getByLabelText(/^name/i), '  Revolution Day  ');
    await user.click(screen.getByRole('button', { name: 'Add holiday' }));

    const posted = mock.callsTo('POST /api/leave/holidays');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({ date: '2025-07-23', name: 'Revolution Day' });
    expect(mock.urlsFor('GET', '/api/leave/holidays').length).toBeGreaterThanOrEqual(2);
  });

  it('names both missing fields instead of posting', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(holidayRoutes());

    renderApp('/holidays');
    await user.click(await screen.findByRole('button', { name: 'Add holiday' }));

    expect(await screen.findByText('Date is required.')).toBeInTheDocument();
    expect(screen.getByText('Holiday name is required.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/leave/holidays')).toHaveLength(0);
  });

  it('shows the 409 message when the date is already a holiday', async () => {
    const user = userEvent.setup();
    installFetchMock(
      holidayRoutes({
        'POST /api/leave/holidays': {
          status: 409,
          body: { status: 409, message: 'A holiday is already registered on 2025-04-20' },
        },
      }),
    );

    renderApp('/holidays');
    await user.type(await screen.findByLabelText(/^date/i), '2025-04-20');
    await user.type(screen.getByLabelText(/^name/i), 'Duplicate');
    await user.click(screen.getByRole('button', { name: 'Add holiday' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A holiday is already registered on 2025-04-20',
    );
  });

  it('deletes a holiday only after the confirmation is accepted', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      holidayRoutes({ 'DELETE /api/leave/holidays/hol-1': { status: 204 } }),
    );

    renderApp('/holidays');
    await user.click(await screen.findByRole('button', { name: 'Delete Sham El-Nessim' }));

    const dialog = await screen.findByRole('dialog', { name: 'Delete Sham El-Nessim?' });
    expect(within(dialog).getByText(/20 Apr 2025 will count as a normal working day again\./)).toBeInTheDocument();
    expect(mock.callsTo('DELETE /api/leave/holidays/hol-1')).toHaveLength(0);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(mock.callsTo('DELETE /api/leave/holidays/hol-1')).toHaveLength(1);
    expect(mock.urlsFor('GET', '/api/leave/holidays').length).toBeGreaterThanOrEqual(2);
  });

  it('edits an existing holiday through PUT', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      holidayRoutes({
        'PUT /api/leave/holidays/hol-1': { body: { ...EID, name: 'Spring Festival' } },
      }),
    );

    renderApp('/holidays');
    await user.click(await screen.findByRole('button', { name: 'Edit Sham El-Nessim' }));
    const nameInput = await screen.findByLabelText(/^name/i);
    await user.clear(nameInput);
    await user.type(nameInput, 'Spring Festival');
    await user.click(screen.getByRole('button', { name: 'Save holiday' }));

    expect(mock.callsTo('PUT /api/leave/holidays/hol-1')[0].body).toEqual({
      date: '2025-04-20',
      name: 'Spring Festival',
    });
  });

  it('shows the empty state when no holiday is registered', async () => {
    installFetchMock(holidayRoutes({ 'GET /api/leave/holidays': { body: [] } }));

    renderApp('/holidays');

    expect(await screen.findByText('No holiday registered.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
