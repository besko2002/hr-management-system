import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type { CreatedEmployeeResponse, DepartmentResponse, EmployeeRecord, PageResponse, Role } from '../api/types';
import { isAdmin } from '../auth/roles';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { describedBy, Field } from '../components/Field';
import { Modal } from '../components/Modal';
import { Card, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { today } from '../lib/dates';
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

/** Shows the one-time temporary password, with a copy button and a clear warning. */
function TemporaryPasswordModal({
  created,
  onClose,
}: {
  created: CreatedEmployeeResponse;
  onClose: () => void;
}): JSX.Element {
  const [copied, setCopied] = useState<'idle' | 'done' | 'failed'>('idle');

  async function copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(created.temporaryPassword);
      setCopied('done');
    } catch {
      setCopied('failed');
    }
  }

  return (
    <Modal
      title="Temporary password"
      onClose={onClose}
      footer={
        <button type="button" className="button button-primary" onClick={onClose}>
          I have shared it
        </button>
      }
    >
      <Banner tone="warning">
        This password is shown once and is never retrievable again. Hand it to{' '}
        {created.employee.fullName} now; if it is lost, the account needs a new password set by
        HR.
      </Banner>
      <p className="spaced">
        <strong>{created.employee.fullName}</strong> ({created.employee.employeeNumber}) can sign
        in with <code>{created.employee.email}</code> and will be asked to change this password
        immediately.
      </p>
      <div className="secret-row">
        <code className="secret" aria-label="Temporary password">
          {created.temporaryPassword}
        </code>
        <button
          type="button"
          className="button"
          onClick={() => {
            void copy();
          }}
        >
          Copy
        </button>
      </div>
      <p aria-live="polite" className="muted">
        {copied === 'done' && 'Copied to the clipboard.'}
        {copied === 'failed' && 'Could not reach the clipboard — copy it by hand.'}
      </p>
    </Modal>
  );
}

export function CreateEmployeePage(): JSX.Element {
  const { role } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const departments = useResource<DepartmentResponse[]>(
    (signal) => api.listDepartments(signal),
    'departments-create',
  );
  const managers = useResource<PageResponse<EmployeeRecord>>(
    (signal) => api.listEmployees({ status: 'ACTIVE', page: 0, size: 100, sort: 'fullName,asc' }, signal),
    'managers-create',
  );

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [employeeRole, setEmployeeRole] = useState<Role>('EMPLOYEE');
  const [jobTitle, setJobTitle] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [managerId, setManagerId] = useState('');
  const [hireDate, setHireDate] = useState(today());
  const [salary, setSalary] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<CreatedEmployeeResponse | null>(null);

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    const nameError = validateFullName(fullName);
    if (nameError !== null) {
      next.fullName = nameError;
    }
    const emailError = validateEmail(email);
    if (emailError !== null) {
      next.email = emailError;
    }
    const titleError = validateJobTitle(jobTitle);
    if (titleError !== null) {
      next.jobTitle = titleError;
    }
    const dateError = validateIsoDate(hireDate, 'Hire date');
    if (dateError !== null) {
      next.hireDate = dateError;
    }
    const salaryError = validateSalary(salary);
    if (salaryError !== null) {
      next.salary = salaryError;
    }
    return next;
  }

  async function onSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const next = validate();
    setErrors(next);
    if (hasErrors(next)) {
      return;
    }

    setBusy(true);
    setFormError(null);
    try {
      const response = await api.createEmployee({
        fullName: fullName.trim(),
        email: email.trim(),
        role: employeeRole,
        jobTitle: jobTitle.trim().length === 0 ? null : jobTitle.trim(),
        departmentId: departmentId === '' ? null : departmentId,
        managerId: managerId === '' ? null : managerId,
        hireDate,
        salary: salary.trim().length === 0 ? null : Number(salary),
      });
      setCreated(response);
      toast.success(`${response.employee.fullName} was created.`);
    } catch (cause) {
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors(cause.fieldErrors);
      }
      setFormError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="New employee"
        subtitle="The account is created with a temporary password shown once."
      />

      <Card>
        {formError !== null && (
          <Banner
            onDismiss={() => {
              setFormError(null);
            }}
          >
            {formError}
          </Banner>
        )}

        <form onSubmit={onSubmit} noValidate>
          <div className="field-row">
            <Field id="new-fullName" label="Full name" error={errors.fullName} required>
              <input
                id="new-fullName"
                value={fullName}
                required
                maxLength={150}
                aria-invalid={errors.fullName !== undefined}
                aria-describedby={describedBy('new-fullName', false, errors.fullName !== undefined)}
                onChange={(event) => {
                  setFullName(event.target.value);
                }}
              />
            </Field>
            <Field id="new-email" label="Work email" error={errors.email} required>
              <input
                id="new-email"
                type="email"
                value={email}
                required
                maxLength={255}
                aria-invalid={errors.email !== undefined}
                aria-describedby={describedBy('new-email', false, errors.email !== undefined)}
                onChange={(event) => {
                  setEmail(event.target.value);
                }}
              />
            </Field>
          </div>

          <div className="field-row">
            <Field
              id="new-role"
              label="Role"
              error={errors.role}
              hint={isAdmin(role) ? undefined : 'Only an ADMIN can create another ADMIN.'}
              required
            >
              <select
                id="new-role"
                value={employeeRole}
                aria-describedby={describedBy('new-role', !isAdmin(role), errors.role !== undefined)}
                onChange={(event) => {
                  setEmployeeRole(event.target.value as Role);
                }}
              >
                <option value="EMPLOYEE">EMPLOYEE</option>
                <option value="HR">HR</option>
                {isAdmin(role) && <option value="ADMIN">ADMIN</option>}
              </select>
            </Field>
            <Field id="new-jobTitle" label="Job title" error={errors.jobTitle}>
              <input
                id="new-jobTitle"
                value={jobTitle}
                maxLength={120}
                aria-invalid={errors.jobTitle !== undefined}
                aria-describedby={describedBy('new-jobTitle', false, errors.jobTitle !== undefined)}
                onChange={(event) => {
                  setJobTitle(event.target.value);
                }}
              />
            </Field>
          </div>

          <div className="field-row">
            <Field id="new-department" label="Department" error={errors.departmentId}>
              <select
                id="new-department"
                value={departmentId}
                onChange={(event) => {
                  setDepartmentId(event.target.value);
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
            <Field id="new-manager" label="Manager" error={errors.managerId}>
              <select
                id="new-manager"
                value={managerId}
                onChange={(event) => {
                  setManagerId(event.target.value);
                }}
              >
                <option value="">No manager (org root)</option>
                {(managers.data?.content ?? []).map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.fullName} · {employee.employeeNumber}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          <div className="field-row">
            <Field id="new-hireDate" label="Hire date" error={errors.hireDate} required>
              <input
                id="new-hireDate"
                type="date"
                value={hireDate}
                required
                aria-invalid={errors.hireDate !== undefined}
                aria-describedby={describedBy('new-hireDate', false, errors.hireDate !== undefined)}
                onChange={(event) => {
                  setHireDate(event.target.value);
                }}
              />
            </Field>
            <Field
              id="new-salary"
              label="Monthly salary"
              error={errors.salary}
              hint="Optional. At most 2 decimals."
            >
              <input
                id="new-salary"
                type="text"
                inputMode="decimal"
                value={salary}
                aria-invalid={errors.salary !== undefined}
                aria-describedby={describedBy('new-salary', true, errors.salary !== undefined)}
                onChange={(event) => {
                  setSalary(event.target.value);
                }}
              />
            </Field>
          </div>

          <div className="button-row">
            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? 'Creating…' : 'Create employee'}
            </button>
            <button
              type="button"
              className="button"
              onClick={() => {
                navigate('/employees');
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      </Card>

      {created !== null && (
        <TemporaryPasswordModal
          created={created}
          onClose={() => {
            const id = created.employee.id;
            setCreated(null);
            navigate(`/employees/${id}`);
          }}
        />
      )}
    </>
  );
}
