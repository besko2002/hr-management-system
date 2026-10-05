import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { makeOrgNode, signedIn } from '../test/fixtures';
import { installFetchMock, renderApp } from '../test/helpers';
import type { RouteHandler } from '../test/helpers';

const TREE = [
  makeOrgNode({
    id: 'emp-9',
    fullName: 'Omar Said',
    children: [
      makeOrgNode({
        id: 'emp-1',
        fullName: 'Nadia Hassan',
        jobTitle: 'Analyst',
        children: [makeOrgNode({ id: 'emp-2', fullName: 'Youssef Adel', jobTitle: null, departmentName: null })],
      }),
    ],
  }),
];

function orgRoutes(overrides: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    ...signedIn(),
    'GET /api/org-chart': { body: TREE },
    ...overrides,
  };
}

describe('org chart', () => {
  it('renders the tree expanded with each node’s meta line', async () => {
    const mock = installFetchMock(orgRoutes());

    renderApp('/org-chart');

    const tree = await screen.findByRole('list', { name: 'Organisation chart' });
    expect(within(tree).getByText('Omar Said')).toBeInTheDocument();
    expect(within(tree).getByText('Nadia Hassan')).toBeInTheDocument();
    expect(within(tree).getByText('Youssef Adel')).toBeInTheDocument();
    expect(within(tree).getByText('No job title')).toBeInTheDocument();
    expect(mock.urlsFor('GET', '/api/org-chart')).toEqual(['/api/org-chart']);
  });

  it('marks a node with reports as expanded and counts them', async () => {
    installFetchMock(orgRoutes());

    renderApp('/org-chart');

    const toggle = await screen.findByRole('button', { name: 'Collapse the reports of Omar Said' });
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByText('1 direct report')).toHaveLength(2);
  });

  it('collapses a node, hiding its whole subtree', async () => {
    const user = userEvent.setup();
    installFetchMock(orgRoutes());

    renderApp('/org-chart');
    const tree = await screen.findByRole('list', { name: 'Organisation chart' });
    await user.click(screen.getByRole('button', { name: 'Collapse the reports of Omar Said' }));

    expect(within(tree).queryByText('Nadia Hassan')).not.toBeInTheDocument();
    expect(within(tree).queryByText('Youssef Adel')).not.toBeInTheDocument();
    expect(within(tree).getByText('Omar Said')).toBeInTheDocument();
    const toggle = screen.getByRole('button', { name: 'Expand the reports of Omar Said' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('expands the node again and brings the subtree back', async () => {
    const user = userEvent.setup();
    installFetchMock(orgRoutes());

    renderApp('/org-chart');
    const tree = await screen.findByRole('list', { name: 'Organisation chart' });
    await user.click(screen.getByRole('button', { name: 'Collapse the reports of Omar Said' }));
    await user.click(screen.getByRole('button', { name: 'Expand the reports of Omar Said' }));

    expect(within(tree).getByText('Nadia Hassan')).toBeInTheDocument();
    expect(within(tree).getByText('Youssef Adel')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Collapse the reports of Omar Said' }),
    ).toHaveAttribute('aria-expanded', 'true');
  });

  it('collapses an inner node without touching its parent', async () => {
    const user = userEvent.setup();
    installFetchMock(orgRoutes());

    renderApp('/org-chart');
    const tree = await screen.findByRole('list', { name: 'Organisation chart' });
    await user.click(screen.getByRole('button', { name: 'Collapse the reports of Nadia Hassan' }));

    expect(within(tree).getByText('Omar Said')).toBeInTheDocument();
    expect(within(tree).getByText('Nadia Hassan')).toBeInTheDocument();
    expect(within(tree).queryByText('Youssef Adel')).not.toBeInTheDocument();
  });

  it('gives a leaf no toggle at all', async () => {
    installFetchMock(orgRoutes());

    renderApp('/org-chart');

    const tree = await screen.findByRole('list', { name: 'Organisation chart' });
    expect(within(tree).getByText('Youssef Adel')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /reports of Youssef Adel/ }),
    ).not.toBeInTheDocument();
  });

  it('shows the empty state when the tree has no root', async () => {
    installFetchMock(orgRoutes({ 'GET /api/org-chart': { body: [] } }));

    renderApp('/org-chart');

    expect(await screen.findByText('The org chart is empty.')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Organisation chart' })).not.toBeInTheDocument();
  });
});
