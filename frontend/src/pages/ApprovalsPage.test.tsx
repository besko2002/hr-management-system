import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeLeaveRequest, makePage, makeTeamMember, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const PENDING = makeLeaveRequest({
  id: 'leave-7',
  employeeName: 'Youssef Adel',
  workingDays: 4,
  reason: 'Family trip',
});

function approvalsRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn({ team: [makeTeamMember()] }),
    'GET /api/leave/requests/pending?page=0&size=10': { body: makePage([PENDING]) },
    ...overrides,
  };
}

describe('approvals', () => {
  it('lists the pending requests a manager has to decide', async () => {
    const mock = installFetchMock(approvalsRoutes());

    renderApp('/approvals');

    const table = await screen.findByRole('table');
    const row = within(table).getByRole('row', { name: /youssef adel/i });
    expect(within(row).getByText('ANNUAL')).toBeInTheDocument();
    expect(within(row).getByText('4')).toBeInTheDocument();
    expect(within(row).getByText('Family trip')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/leave/requests/pending')).toEqual([
      '/api/leave/requests/pending?page=0&size=10',
    ]);
  });

  it('shows the empty state when nothing is waiting', async () => {
    installFetchMock(
      approvalsRoutes({ 'GET /api/leave/requests/pending?page=0&size=10': { body: makePage([]) } }),
    );

    renderApp('/approvals');

    expect(await screen.findByText('Nothing is waiting for you.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('approves a request through POST .../approve and reloads the list', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      approvalsRoutes({
        'POST /api/leave/requests/leave-7/approve': {
          body: makeLeaveRequest({ id: 'leave-7', status: 'APPROVED' }),
        },
      }),
    );

    renderApp('/approvals');
    await user.click(await screen.findByRole('button', { name: /^approve youssef adel/i }));
    await user.click(await screen.findByRole('button', { name: 'Approve' }));

    const posted = mock.callsTo('POST /api/leave/requests/leave-7/approve');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({ decisionNote: null });
    // The decided request must disappear, so the list is refetched.
    expect(mock.urlsFor('GET', '/api/leave/requests/pending').length).toBeGreaterThanOrEqual(2);
  });

  it('refuses to reject without a note and posts nothing', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      approvalsRoutes({ 'POST /api/leave/requests/leave-7/reject': { body: PENDING } }),
    );

    renderApp('/approvals');
    await user.click(await screen.findByRole('button', { name: /^reject youssef adel/i }));
    await user.click(await screen.findByRole('button', { name: 'Reject' }));

    expect(await screen.findByText('A note is required to reject a request.')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/leave/requests/leave-7/reject')).toHaveLength(0);
    // The dialog stays open so the note can be supplied.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('rejects with the typed decisionNote once a note is given', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      approvalsRoutes({
        'POST /api/leave/requests/leave-7/reject': {
          body: makeLeaveRequest({ id: 'leave-7', status: 'REJECTED' }),
        },
      }),
    );

    renderApp('/approvals');
    await user.click(await screen.findByRole('button', { name: /^reject youssef adel/i }));
    await user.type(
      await screen.findByLabelText(/reason for rejection/i),
      '  Team is understaffed  ',
    );
    await user.click(screen.getByRole('button', { name: 'Reject' }));

    const posted = mock.callsTo('POST /api/leave/requests/leave-7/reject');
    expect(posted).toHaveLength(1);
    expect(posted[0].body).toEqual({ decisionNote: 'Team is understaffed' });
  });

  it('shows the server message when the decision conflicts with a 409', async () => {
    const user = userEvent.setup();
    installFetchMock(
      approvalsRoutes({
        'POST /api/leave/requests/leave-7/approve': {
          status: 409,
          body: { status: 409, message: 'This leave request has already been decided' },
        },
      }),
    );

    renderApp('/approvals');
    await user.click(await screen.findByRole('button', { name: /^approve youssef adel/i }));
    await user.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This leave request has already been decided',
    );
  });

  it('sends an optional note with an approval when one is typed', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock(
      approvalsRoutes({
        'POST /api/leave/requests/leave-7/approve': {
          body: makeLeaveRequest({ id: 'leave-7', status: 'APPROVED' }),
        },
      }),
    );

    renderApp('/approvals');
    await user.click(await screen.findByRole('button', { name: /^approve youssef adel/i }));
    await user.type(await screen.findByLabelText(/note \(optional\)/i), 'Enjoy');
    await user.click(screen.getByRole('button', { name: 'Approve' }));

    expect(mock.callsTo('POST /api/leave/requests/leave-7/approve')[0].body).toEqual({
      decisionNote: 'Enjoy',
    });
  });
});
