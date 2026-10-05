import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type {
  AuditChangeResponse,
  DepartmentResponse,
  EmployeeProfile,
  EmployeeRecord,
  PageResponse,
  Role,
} from '../api/types';
import { isAdmin, readSalary } from '../auth/roles';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { describedBy, Field } from '../components/Field';
import { Pagination } from '../components/Pagination';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/useToast';
import { formatDate, formatDateTime, formatMoney, humanizeEnum } from '../lib/format';
import { useResource } from '../lib/useResource';
import {
  hasErrors,
  validateEmail,
  validateFullName,
  validateIsoDate,
  validateJobTitle,
  validateSalary,
  type FieldErrors,
} from '../lib/validation';

const AUDIT_SIZE = 20;

type Tab = 'details' | 'audit';

export function EmployeeDetailPage(): JSX.Element {
  const { id = '' } = useParams<{ id: string }>();
  const { role } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState<Tab>('details');

  const employee = useResource<EmployeeProfile>(
    (signal) => api.getEmployee(id, signal),
    `employee-${id}`,
  );
  const departments = useResource<DepartmentResponse[]>(
    (signal) => api.listDepartments(signal),
    'departments-detail',
  );
  const candidates = useResource<PageResponse<EmployeeRecord>>(
    (signal) =>
      api.listEmployees({ status: 'ACTIVE', page: 0, size: 100, sort: 'fullName,asc' }, signal),
    'manager-candidates',
  );

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<{
    fullName: string;
    email: string;
    role: Role;
    jobTitle: string;
    departmentId: string;
    hireDate: string;
    salary: string;
  } | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [managerBusy, setManagerBusy] = useState(false);
  const [managerError, setManagerError] = useState<string | null>(null);
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [auditPage, setAuditPage] = useState(0);

  // The audit trail is only fetched once its tab is open: it is a separate HR-only call.
  const audit = useResource<PageResponse<AuditChangeResponse>>(
    (signal) =>
      tab === 'audit'
        ? api.employeeAudit(id, auditPage, AUDIT_SIZE, signal)
        : Promise.resolve({ content: [], page: 0, size: AUDIT_SIZE, totalElements: 0, totalPages: 0 }),
    `audit-${id}-${String(auditPage)}-${tab}`,
  );

  const data = employee.data;
  const salary = data === null ? null : readSalary(data);

  function startEditing(record: EmployeeProfile): void {
    setForm({
      fullName: record.fullName,
      email: record.email,
      role: record.role,
      jobTitle: record.jobTitle ?? '',
      departmentId: record.departmentId ?? '',
      hireDate: record.hireDate,
      salary: readSalary(record) === null ? '' : String(readSalary(record)),
    });
    setErrors({});
    setActionError(null);
    setEditing(true);
  }

  async function save(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (form === null) {
      return;
    }
    const next: FieldErrors = {};
    const nameError = validateFullName(form.fullName);
    if (nameError !== null) {
      next.fullName = nameError;
    }
    const emailError = validateEmail(form.email);
    if (emailError !== null) {
      next.email = emailError;
    }
    const titleError = validateJobTitle(form.jobTitle);
    if (titleError !== null) {
      next.jobTitle = titleError;
    }
    const dateError = validateIsoDate(form.hireDate, 'Hire date');
    if (dateError !== null) {
      next.hireDate = dateError;
    }
    const salaryError = validateSalary(form.salary);
    if (salaryError !== null) {
      next.salary = salaryError;
    }
    setErrors(next);
    if (hasErrors(next)) {
      return;
    }

    setBusy(true);
    setActionError(null);
    try {
      const updated = await api.updateEmployee(id, {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        role: form.role,
        jobTitle: form.jobTitle.trim().length === 0 ? null : form.jobTitle.trim(),
        departmentId: form.departmentId === '' ? null : form.departmentId,
        hireDate: form.hireDate,
        salary: form.salary.trim().length === 0 ? null : Number(form.salary),
      });
      employee.setData(updated);
      setEditing(false);
      toast.success('Employee updated.');
      audit.reload();
    } catch (cause) {
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors(cause.fieldErrors);
      }
      setActionError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function changeManager(managerId: string): Promise<void> {
    setManagerBusy(true);
    setManagerError(null);
    try {
      const updated = await api.setManager(id, managerId === '' ? null : managerId);
      employee.setData(updated);
      toast.success('Manager updated.');
      audit.reload();
    } catch (cause) {
      // 409 here is the cycle guard ("would create a cycle") or a terminated manager.
      setManagerError(errorMessage(cause));
    } finally {
      setManagerBusy(false);
    }
  }

  async function terminate(): Promise<void> {
    setBusy(true);
    setActionError(null);
    try {
      const updated = await api.terminateEmployee(id);
      employee.setData(updated);
      setTerminateOpen(false);
      toast.success('Employee terminated.');
      audit.reload();
    } catch (cause) {
      setActionError(errorMessage(cause));
      setTerminateOpen(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title={data?.fullName ?? 'Employee'}
        subtitle={data === null ? undefined : `${data.employeeNumber} · ${data.email}`}
        actions={<Link to="/employees">Back to employees</Link>}
      />

      {employee.loading && <Loading label="Loading the employee…" />}
      {employee.error !== null && <Banner>{employee.error}</Banner>}
      {actionError !== null && (
        <Banner
          onDismiss={() => {
            setActionError(null);
          }}
        >
          {actionError}
        </Banner>
      )}

      {data !== null && (
        <>
          <div className="tabs" role="tablist" aria-label="Employee sections">
            <button
              type="button"
              role="tab"
              id="tab-details"
              aria-selected={tab === 'details'}
              aria-controls="panel-details"
              className={tab === 'details' ? 'tab tab-active' : 'tab'}
              onClick={() => {
                setTab('details');
              }}
            >
              Details
            </button>
            <button
              type="button"
              role="tab"
              id="tab-audit"
              aria-selected={tab === 'audit'}
              aria-controls="panel-audit"
              className={tab === 'audit' ? 'tab tab-active' : 'tab'}
              onClick={() => {
                setTab('audit');
              }}
            >
              Audit
            </button>
          </div>

          {tab === 'details' && (
            <div id="panel-details" role="tabpanel" aria-labelledby="tab-details">
              <div className="grid grid-2">
                <Card
                  title="Record"
                  actions={
                    editing ? undefined : (
                      <button
                        type="button"
                        className="button button-small"
                        onClick={() => {
                          startEditing(data);
                        }}
                      >
                        Edit
                      </button>
                    )
                  }
                >
                  {!editing && (
                    <dl className="detail-list">
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
                      {/* Rendered only when the payload carries a salary. */}
                      {salary !== null && (
                        <div>
                          <dt>Monthly salary</dt>
                          <dd className="numeric">{formatMoney(salary)}</dd>
                        </div>
                      )}
                    </dl>
                  )}

                  {editing && form !== null && (
                    <form onSubmit={save} noValidate>
                      <Field id="edit-fullName" label="Full name" error={errors.fullName} required>
                        <input
                          id="edit-fullName"
                          value={form.fullName}
                          required
                          aria-invalid={errors.fullName !== undefined}
                          aria-describedby={describedBy('edit-fullName', false, errors.fullName !== undefined)}
                          onChange={(event) => {
                            setForm({ ...form, fullName: event.target.value });
                          }}
                        />
                      </Field>
                      <Field id="edit-email" label="Work email" error={errors.email} required>
                        <input
                          id="edit-email"
                          type="email"
                          value={form.email}
                          required
                          aria-invalid={errors.email !== undefined}
                          aria-describedby={describedBy('edit-email', false, errors.email !== undefined)}
                          onChange={(event) => {
                            setForm({ ...form, email: event.target.value });
                          }}
                        />
                      </Field>
                      <Field
                        id="edit-role"
                        label="Role"
                        error={errors.role}
                        hint={isAdmin(role) ? undefined : 'Only an ADMIN can grant the ADMIN role.'}
                      >
                        <select
                          id="edit-role"
                          value={form.role}
                          onChange={(event) => {
                            setForm({ ...form, role: event.target.value as Role });
                          }}
                        >
                          <option value="EMPLOYEE">EMPLOYEE</option>
                          <option value="HR">HR</option>
                          {(isAdmin(role) || form.role === 'ADMIN') && (
                            <option value="ADMIN">ADMIN</option>
                          )}
                        </select>
                      </Field>
                      <Field id="edit-jobTitle" label="Job title" error={errors.jobTitle}>
                        <input
                          id="edit-jobTitle"
                          value={form.jobTitle}
                          maxLength={120}
                          aria-invalid={errors.jobTitle !== undefined}
                          aria-describedby={describedBy('edit-jobTitle', false, errors.jobTitle !== undefined)}
                          onChange={(event) => {
                            setForm({ ...form, jobTitle: event.target.value });
                          }}
                        />
                      </Field>
                      <Field id="edit-department" label="Department" error={errors.departmentId}>
                        <select
                          id="edit-department"
                          value={form.departmentId}
                          onChange={(event) => {
                            setForm({ ...form, departmentId: event.target.value });
                          }}
                        >
                          <option value="">Unassigned</option>
                          {(departments.data ?? []).map((department) => (
                            <option key={department.id} value={department.id}>
                              {department.name}
                            </option>
                          ))}
                        </select>
                      </Field>
                      <Field id="edit-hireDate" label="Hire date" error={errors.hireDate} required>
                        <input
                          id="edit-hireDate"
                          type="date"
                          value={form.hireDate}
                          required
                          aria-invalid={errors.hireDate !== undefined}
                          aria-describedby={describedBy('edit-hireDate', false, errors.hireDate !== undefined)}
                          onChange={(event) => {
                            setForm({ ...form, hireDate: event.target.value });
                          }}
                        />
                      </Field>
                      <Field
                        id="edit-salary"
                        label="Monthly salary"
                        error={errors.salary}
                        hint="Leaving this empty clears the stored salary."
                      >
                        <input
                          id="edit-salary"
                          type="text"
                          inputMode="decimal"
                          value={form.salary}
                          aria-invalid={errors.salary !== undefined}
                          aria-describedby={describedBy('edit-salary', true, errors.salary !== undefined)}
                          onChange={(event) => {
                            setForm({ ...form, salary: event.target.value });
                          }}
                        />
                      </Field>

                      <div className="button-row">
                        <button type="submit" className="button button-primary" disabled={busy}>
                          {busy ? 'Saving…' : 'Save changes'}
                        </button>
                        <button
                          type="button"
                          className="button"
                          onClick={() => {
                            setEditing(false);
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                </Card>

                <div className="stack">
                  <Card title="Manager">
                    {managerError !== null && (
                      <Banner
                        onDismiss={() => {
                          setManagerError(null);
                        }}
                      >
                        {managerError}
                      </Banner>
                    )}
                    <Field id="set-manager" label="Reports to">
                      <select
                        id="set-manager"
                        value={data.managerId ?? ''}
                        disabled={managerBusy}
                        onChange={(event) => {
                          void changeManager(event.target.value);
                        }}
                      >
                        <option value="">No manager (org root)</option>
                        {(candidates.data?.content ?? [])
                          .filter((candidate) => candidate.id !== data.id)
                          .map((candidate) => (
                            <option key={candidate.id} value={candidate.id}>
                              {candidate.fullName} · {candidate.employeeNumber}
                            </option>
                          ))}
                      </select>
                    </Field>
                    <p className="muted">
                      A move that would make somebody their own manager is refused by the server
                      with a conflict, and the message is shown here.
                    </p>
                  </Card>

                  <Card title="Danger zone">
                    {data.status === 'TERMINATED' ? (
                      <p>
                        This employee was terminated{' '}
                        {data.terminatedAt === undefined || data.terminatedAt === null
                          ? ''
                          : `on ${formatDate(data.terminatedAt)}`}
                        . The account can no longer sign in.
                      </p>
                    ) : (
                      <>
                        <p>
                          Terminating ends access immediately and moves the direct reports to this
                          person&apos;s manager.
                        </p>
                        <button
                          type="button"
                          className="button button-danger"
                          onClick={() => {
                            setTerminateOpen(true);
                          }}
                        >
                          Terminate employee
                        </button>
                      </>
                    )}
                  </Card>
                </div>
              </div>
            </div>
          )}

          {tab === 'audit' && (
            <div id="panel-audit" role="tabpanel" aria-labelledby="tab-audit">
              <Card title="Change history">
                {audit.loading && <Loading label="Loading the audit trail…" />}
                {audit.error !== null && <Banner>{audit.error}</Banner>}
                {audit.data !== null && audit.data.content.length === 0 && (
                  <EmptyState title="No tracked change yet." hint="Salary, job title, manager, status, role and department changes appear here." />
                )}
                {audit.data !== null && audit.data.content.length > 0 && (
                  <>
                    <table className="table">
                      <caption className="sr-only">Audited field changes</caption>
                      <thead>
                        <tr>
                          <th scope="col">Field</th>
                          <th scope="col">Before</th>
                          <th scope="col">After</th>
                          <th scope="col">Changed by</th>
                          <th scope="col">At</th>
                        </tr>
                      </thead>
                      <tbody>
                        {audit.data.content.map((change) => (
                          <tr key={`${String(change.revision)}-${change.field}`}>
                            <th scope="row">{change.field}</th>
                            <td>{change.before ?? '—'}</td>
                            <td>{change.after ?? '—'}</td>
                            <td>{change.changedBy}</td>
                            <td>{formatDateTime(change.at)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <Pagination
                      page={audit.data.page}
                      totalPages={audit.data.totalPages}
                      totalElements={audit.data.totalElements}
                      onChange={setAuditPage}
                      busy={audit.loading}
                    />
                  </>
                )}
              </Card>
            </div>
          )}
        </>
      )}

      {terminateOpen && data !== null && (
        <ConfirmDialog
          title={`Terminate ${data.fullName}?`}
          message={`${data.fullName} loses access immediately and any direct reports move to their manager. This cannot be undone from this screen.`}
          confirmLabel="Terminate"
          busy={busy}
          onConfirm={() => {
            void terminate();
          }}
          onCancel={() => {
            setTerminateOpen(false);
          }}
        />
      )}
    </>
  );
}
