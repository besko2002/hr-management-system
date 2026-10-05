import { useState } from 'react';
import { api } from '../api/api';
import type { TeamMemberResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useResource } from '../lib/useResource';

type Scope = 'direct' | 'all';

/**
 * A manager's team. Neither endpoint carries a salary field, and this screen never shows
 * pay or payslips for a report — that is HR's business only.
 */
export function TeamPage(): JSX.Element {
  const [scope, setScope] = useState<Scope>('direct');

  const team = useResource<TeamMemberResponse[]>(
    (signal) => (scope === 'direct' ? api.myTeam(signal) : api.myTeamAll(signal)),
    `team-${scope}`,
  );

  return (
    <>
      <PageHeader
        title="My team"
        subtitle="Direct reports, or the whole subtree with its depth."
      />

      <Card
        title={scope === 'direct' ? 'Direct reports' : 'All reports'}
        actions={
          <div className="button-row" role="group" aria-label="Team scope">
            <button
              type="button"
              className={scope === 'direct' ? 'button button-primary button-small' : 'button button-small'}
              aria-pressed={scope === 'direct'}
              onClick={() => {
                setScope('direct');
              }}
            >
              Direct
            </button>
            <button
              type="button"
              className={scope === 'all' ? 'button button-primary button-small' : 'button button-small'}
              aria-pressed={scope === 'all'}
              onClick={() => {
                setScope('all');
              }}
            >
              All reports
            </button>
          </div>
        }
      >
        {team.loading && <Loading label="Loading your team…" />}
        {team.error !== null && <Banner>{team.error}</Banner>}
        {team.data !== null && team.data.length === 0 && (
          <EmptyState title="Nobody reports to you." />
        )}
        {team.data !== null && team.data.length > 0 && (
          <table className="table">
            <caption className="sr-only">
              {scope === 'direct' ? 'Direct reports' : 'All reports with their depth'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Employee</th>
                <th scope="col">Number</th>
                <th scope="col">Job title</th>
                <th scope="col">Department</th>
                {scope === 'all' && <th scope="col">Depth</th>}
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {team.data.map((member) => (
                <tr key={member.id}>
                  <th scope="row">{member.fullName}</th>
                  <td>{member.employeeNumber}</td>
                  <td>{member.jobTitle ?? '—'}</td>
                  <td>{member.departmentName ?? 'Unassigned'}</td>
                  {scope === 'all' && <td>{member.depth}</td>}
                  <td>
                    <StatusChip status={member.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}
