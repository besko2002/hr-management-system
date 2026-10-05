import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeBalance, makeLeaveRequest, makePage, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

function leaveRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn(),
    'GET /api/leave/balances/me': { body: [makeBalance()] },
    'GET /api/leave/requests/me?page=0&size=10': { body: makePage([makeLeaveRequest()]) },
    ...overrides,
  };
}

async function fillDates(start: string, end: string): Promise<void> {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/start date/i), start);
  await user.type(screen.getByLabelText(/end date/i), end);
}

describe('leave page', () => {
  it('rejects an end date that falls before the start date without calling the API', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(leaveRoutes());

    renderApp('/leave');
    await fillDates('2099-06-10', '2099-06-02');
    await user.click(screen.getByRole('button', { name: 'Submit request' }));

    expect(
      await screen.findByText('The end date cannot be before the start date.'),
    ).toBeInTheDocument();
    expect(mock.callsTo('POST /api/leave/requests')).toHaveLength(0);
    expect(screen.getByLabelText(/end date/i)).toHaveAttribute('aria-invalid', 'true');
  });

  it('files a request and shows the working days the API computed', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      leaveRoutes({
        'POST /api/leave/requests': {
          status: 201,
          body: makeLeaveRequest({ workingDays: 4, startDate: '2099-06-01', endDate: '2099-06-05' }),
        },
      }),
    );

    renderApp('/leave');
    await fillDates('2099-06-01', '2099-06-05');
    await user.click(screen.getByRole('button', { name: 'Submit request' }));

    expect(await screen.findByText(/4 working day\(s\) from/i)).toBeInTheDocument();
    const posted = mock.callsTo('POST /api/leave/requests');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({
      type: 'ANNUAL',
      startDate: '2099-06-01',
      endDate: '2099-06-05',
      reason: null,
    });
    // Balances and the list are refetched so the new pending days show up.
    expect(mock.urlsFor('GET', '/api/leave/balances/me').length).toBeGreaterThanOrEqual(2);
  });

  it('shows the 409 message when the range overlaps an existing request', async () => {
    const user = userEvent.setup();
    installFetchMock(
      leaveRoutes({
        'POST /api/leave/requests': {
          status: 409,
          body: { status: 409, message: 'This range overlaps leave request leave-1' },
        },
      }),
    );

    renderApp('/leave');
    await fillDates('2099-06-01', '2099-06-05');
    await user.click(screen.getByRole('button', { name: 'Submit request' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This range overlaps leave request leave-1',
    );
  });

  it('maps a 400 fieldErrors payload onto the offending field', async () => {
    const user = userEvent.setup();
    installFetchMock(
      leaveRoutes({
        'POST /api/leave/requests': {
          status: 400,
          body: {
            status: 400,
            message: 'Validation failed',
            fieldErrors: { startDate: 'A leave request cannot start in the past' },
          },
        },
      }),
    );

    renderApp('/leave');
    await fillDates('2099-06-01', '2099-06-05');
    await user.click(screen.getByRole('button', { name: 'Submit request' }));

    expect(
      await screen.findByText('A leave request cannot start in the past'),
    ).toBeInTheDocument();
  });

  it('cancels a PENDING request after the confirmation is accepted', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      leaveRoutes({
        'POST /api/leave/requests/leave-1/cancel': {
          body: makeLeaveRequest({ status: 'CANCELLED' }),
        },
      }),
    );

    renderApp('/leave');
    const table = await screen.findByRole('table', { name: /my leave requests/i });
    await user.click(within(table).getByRole('button', { name: 'Cancel' }));
    await user.click(await screen.findByRole('button', { name: 'Cancel request' }));

    expect(mock.callsTo('POST /api/leave/requests/leave-1/cancel')).toHaveLength(1);
    expect(mock.urlsFor('GET', '/api/leave/requests/me').length).toBeGreaterThanOrEqual(2);
  });

  it('offers no cancel button for a request that is already rejected', async () => {
    installFetchMock(
      leaveRoutes({
        'GET /api/leave/requests/me?page=0&size=10': {
          body: makePage([makeLeaveRequest({ status: 'REJECTED', decisionNote: 'No cover' })]),
        },
      }),
    );

    renderApp('/leave');

    const table = await screen.findByRole('table', { name: /my leave requests/i });
    expect(within(table).getByText('No cover')).toBeInTheDocument();
    expect(within(table).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
  });

  it('puts the chosen status filter in the query string', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      leaveRoutes({
        'GET /api/leave/requests/me?page=0&size=10&status=APPROVED': {
          body: makePage([makeLeaveRequest({ status: 'APPROVED' })]),
        },
      }),
    );

    renderApp('/leave');
    await user.selectOptions(await screen.findByLabelText('Filter by status'), 'APPROVED');

    const table = await screen.findByRole('table', { name: /my leave requests/i });
    expect(await within(table).findByText('Approved')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/leave/requests/me')).toContain(
      '/api/leave/requests/me?page=0&size=10&status=APPROVED',
    );
  });
});
