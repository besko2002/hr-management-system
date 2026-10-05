import { Link } from 'react-router-dom';
import { api } from '../api/api';
import type { EmployeeRecord } from '../api/types';
import { readSalary } from '../auth/roles';
import { Banner } from '../components/Banner';
import { Card, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { formatDate, formatMoney, humanizeEnum } from '../lib/format';
import { useResource } from '../lib/useResource';

export function ProfilePage(): JSX.Element {
  const me = useResource<EmployeeRecord>((signal) => api.me(signal), 'me-profile');
  const data = me.data;
  const salary = data === null ? null : readSalary(data);

  return (
    <>
      <PageHeader title="My profile" subtitle="What HR holds about you." />

      {me.loading && <Loading label="Loading your profile…" />}
      {me.error !== null && <Banner>{me.error}</Banner>}

      {data !== null && (
        <div className="grid grid-2">
          <Card title="Details">
            <dl className="detail-list">
              <div>
                <dt>Employee number</dt>
                <dd>{data.employeeNumber}</dd>
              </div>
              <div>
                <dt>Full name</dt>
                <dd>{data.fullName}</dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{data.email}</dd>
              </div>
              <div>
                <dt>Role</dt>
                <dd>{humanizeEnum(data.role)}</dd>
              </div>
              <div>
                <dt>Job title</dt>
                <dd>{data.jobTitle ?? '—'}</dd>
              </div>
              <div>
                <dt>Department</dt>
                <dd>{data.departmentName ?? 'Unassigned'}</dd>
              </div>
              <div>
                <dt>Manager</dt>
                <dd>{data.managerName ?? 'None'}</dd>
              </div>
              <div>
                <dt>Hire date</dt>
                <dd>{formatDate(data.hireDate)}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>
                  <StatusChip status={data.status} />
                </dd>
              </div>
              {/* Pay is rendered only when the API actually sent a salary. */}
              {salary !== null && (
                <div>
                  <dt>Monthly salary</dt>
                  <dd className="numeric">{formatMoney(salary)}</dd>
                </div>
              )}
            </dl>
          </Card>

          <Card title="Security">
            <p>
              Your password is the only credential for this account. Change it if you suspect it
              has been shared.
            </p>
            <p className="spaced">
              <Link className="button button-primary" to="/change-password">
                Change password
              </Link>
            </p>
          </Card>
        </div>
      )}
    </>
  );
}
