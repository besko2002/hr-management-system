import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makePage, makeRun, makeRunDetail, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

function payrollRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ role: 'HR' }),
    'GET /api/payroll/runs?page=0&size=12': { body: makePage([makeRun()]) },
    ...overrides,
  };
}

async function fillPeriod(year: string, month: string): Promise<void> {
  const user = userEvent.setup();
  const yearInput = await screen.findByLabelText(/^year/i);
  await user.clear(yearInput);
  await user.type(yearInput, year);
  const monthInput = screen.getByLabelText(/^month/i);
  await user.clear(monthInput);
  await user.type(monthInput, month);
}

describe('payroll runs', () => {
  it('lists the runs of the first page', async () => {
    const mock = installFetchMock(payrollRoutes());

    renderApp('/payroll');

    const table = await screen.findByRole('table', { name: /payroll runs, newest first/i });
    const row = within(table).getByRole('row', { name: /March 2025/ });
    expect(within(row).getByText('Draft')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/payroll/runs')).toEqual(['/api/payroll/runs?page=0&size=12']);
  });

  it('creates a run and opens the new run detail', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      payrollRoutes({
        'POST /api/payroll/runs': { status: 201, body: makeRunDetail() },
        'GET /api/payroll/runs/run-1': { body: makeRunDetail() },
      }),
    );

    renderApp('/payroll');
    await fillPeriod('2025', '3');
    await user.click(screen.getByRole('button', { name: 'Create run' }));

    const posted = mock.callsTo('POST /api/payroll/runs');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({ year: 2025, month: 3 });
    expect(await screen.findByRole('heading', { name: 'Totals' })).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/payroll/runs/run-1')).toHaveLength(1);
  });

  it('shows the 409 message when a run for that month already exists', async () => {
    const user = userEvent.setup();
    installFetchMock(
      payrollRoutes({
        'POST /api/payroll/runs': {
          status: 409,
          body: { status: 409, message: 'A payroll run for 2025-03 already exists' },
        },
      }),
    );

    renderApp('/payroll');
    await fillPeriod('2025', '3');
    await user.click(screen.getByRole('button', { name: 'Create run' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A payroll run for 2025-03 already exists',
    );
    // Still on the list page, no navigation happened.
    expect(screen.getByRole('heading', { name: 'Payroll' })).toBeInTheDocument();
  });

  it('refuses an impossible month before calling the API', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(payrollRoutes());

    renderApp('/payroll');
    await fillPeriod('2025', '13');
    await user.click(screen.getByRole('button', { name: 'Create run' }));

    expect(await screen.findByText('Month must be between 1 and 12.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/payroll/runs')).toHaveLength(0);
  });

  it('refuses a year outside the supported range', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(payrollRoutes());

    renderApp('/payroll');
    await fillPeriod('1999', '3');
    await user.click(screen.getByRole('button', { name: 'Create run' }));

    expect(await screen.findByText('Year must be between 2000 and 2100.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/payroll/runs')).toHaveLength(0);
  });

  it('shows the empty state when payroll has never been run', async () => {
    installFetchMock(payrollRoutes({ 'GET /api/payroll/runs?page=0&size=12': { body: makePage([]) } }));

    renderApp('/payroll');

    expect(await screen.findByText('No payroll run yet.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: /payroll runs/i })).not.toBeInTheDocument();
  });
});
