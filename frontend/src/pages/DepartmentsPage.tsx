import { useState } from 'react';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import type { DepartmentResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { describedBy, Field } from '../components/Field';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { formatDate } from '../lib/format';
import { useResource } from '../lib/useResource';
import { validateName } from '../lib/validation';

export function DepartmentsPage(): JSX.Element {
  const toast = useToast();
  const departments = useResource<DepartmentResponse[]>(
    (signal) => api.listDepartments(signal),
    'departments-crud',
  );

  const [name, setName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<DepartmentResponse | null>(null);

  async function create(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const invalid = validateName(name, 'Department name');
    setNameError(invalid);
    if (invalid !== null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createDepartment({ name: name.trim() });
      toast.success('Department created.');
      setName('');
      departments.reload();
    } catch (cause) {
      if (cause instanceof ApiError && cause.fieldErrors.name !== undefined) {
        setNameError(cause.fieldErrors.name);
      }
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function rename(id: string): Promise<void> {
    const invalid = validateName(editingName, 'Department name');
    if (invalid !== null) {
      setError(invalid);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.renameDepartment(id, { name: editingName.trim() });
      toast.success('Department renamed.');
      setEditingId(null);
      departments.reload();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function remove(): Promise<void> {
    if (deleteTarget === null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.deleteDepartment(deleteTarget.id);
      toast.success('Department deleted.');
      setDeleteTarget(null);
      departments.reload();
    } catch (cause) {
      // 409 while the department still has employees: show the API's wording.
      setError(errorMessage(cause));
      setDeleteTarget(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Departments" subtitle="Create, rename and delete departments." />

      {error !== null && (
        <Banner
          onDismiss={() => {
            setError(null);
          }}
        >
          {error}
        </Banner>
      )}

      <Card title="Add a department">
        <form onSubmit={create} className="inline-form" noValidate>
          <Field id="department-name" label="Name" error={nameError} required>
            <input
              id="department-name"
              value={name}
              required
              maxLength={120}
              aria-invalid={nameError !== null}
              aria-describedby={describedBy('department-name', false, nameError !== null)}
              onChange={(event) => {
                setName(event.target.value);
              }}
            />
          </Field>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Add department'}
          </button>
        </form>
      </Card>

      <Card title="All departments">
        {departments.loading && <Loading label="Loading departments…" />}
        {departments.error !== null && <Banner>{departments.error}</Banner>}
        {departments.data !== null && departments.data.length === 0 && (
          <EmptyState title="No department yet." hint="Add the first one above." />
        )}
        {departments.data !== null && departments.data.length > 0 && (
          <table className="table">
            <caption className="sr-only">Departments</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Created</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {departments.data.map((department) => (
                <tr key={department.id}>
                  <th scope="row">
                    {editingId === department.id ? (
                      <>
                        <label className="sr-only" htmlFor={`rename-${department.id}`}>
                          New name for {department.name}
                        </label>
                        <input
                          id={`rename-${department.id}`}
                          value={editingName}
                          maxLength={120}
                          onChange={(event) => {
                            setEditingName(event.target.value);
                          }}
                        />
                      </>
                    ) : (
                      department.name
                    )}
                  </th>
                  <td>{formatDate(department.createdAt.slice(0, 10))}</td>
                  <td className="row-actions">
                    {editingId === department.id ? (
                      <>
                        <button
                          type="button"
                          className="button button-primary button-small"
                          disabled={busy}
                          onClick={() => {
                            void rename(department.id);
                          }}
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          className="button button-small"
                          onClick={() => {
                            setEditingId(null);
                          }}
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="button button-small"
                          onClick={() => {
                            setEditingId(department.id);
                            setEditingName(department.name);
                          }}
                          aria-label={`Rename ${department.name}`}
                        >
                          Rename
                        </button>
                        <button
                          type="button"
                          className="button button-danger button-small"
                          onClick={() => {
                            setDeleteTarget(department);
                          }}
                          aria-label={`Delete ${department.name}`}
                        >
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {deleteTarget !== null && (
        <ConfirmDialog
          title={`Delete ${deleteTarget.name}?`}
          message="A department that still has employees cannot be deleted; the server answers with a conflict and the reason is shown."
          confirmLabel="Delete"
          busy={busy}
          onConfirm={() => {
            void remove();
          }}
          onCancel={() => {
            setDeleteTarget(null);
          }}
        />
      )}
    </>
  );
}
