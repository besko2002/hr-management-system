import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import type { DepartmentResponse, EmployeeRecord, EmployeeStatus, PageResponse } from '../api/types';
import { readSalary } from '../auth/roles';
import { Banner } from '../components/Banner';
import { Pagination } from '../components/Pagination';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { formatDate, formatMoney } from '../lib/format';
import { useResource } from '../lib/useResource';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

type SortValue =
  | 'fullName,asc'
  | 'fullName,desc'
  | 'hireDate,desc'
  | 'hireDate,asc'
  | 'salary,desc'
  | 'employeeNumber,asc';

const SORT_OPTIONS: { value: SortValue; label: string }[] = [
  { value: 'fullName,asc', label: 'Name A–Z' },
  { value: 'fullName,desc', label: 'Name Z–A' },
  { value: 'hireDate,desc', label: 'Newest hires' },
  { value: 'hireDate,asc', label: 'Longest serving' },
  { value: 'salary,desc', label: 'Highest salary' },
  { value: 'employeeNumber,asc', label: 'Employee number' },
];

export function EmployeesPage(): JSX.Element {
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState<EmployeeStatus | ''>('');
  const [sort, setSort] = useState<SortValue>('fullName,asc');
  const [page, setPage] = useState(0);

  // One request per settled keystroke burst, not per character.
  useEffect(() => {
    const handle = window.setTimeout(() => {
      setDebounced(search.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(handle);
    };
  }, [search]);

  const departments = useResource<DepartmentResponse[]>(
    (signal) => api.listDepartments(signal),
    'departments-filter',
  );

  const employees = useResource<PageResponse<EmployeeRecord>>(
    (signal) =>
      api.listEmployees(
        {
          q: debounced.length === 0 ? undefined : debounced,
          departmentId: departmentId === '' ? undefined : departmentId,
          status: status === '' ? undefined : status,
          page,
          size: PAGE_SIZE,
          sort,
        },
        signal,
      ),
    `employees-${debounced}-${departmentId}-${status}-${sort}-${String(page)}`,
  );

  return (
    <>
      <PageHeader
        title="Employees"
        subtitle="Search, filter and open a record."
        actions={
          <Link className="button button-primary" to="/employees/new">
            New employee
          </Link>
        }
      />

      <Card title="Filters">
        <div className="filter-row">
          <label className="inline-field" htmlFor="employee-search">
            <span>Search</span>
            <input
              id="employee-search"
              type="search"
              value={search}
              placeholder="Name, email or number"
              onChange={(event) => {
                setSearch(event.target.value);
              }}
            />
          </label>

          <label className="inline-field" htmlFor="employee-department">
            <span>Department</span>
            <select
              id="employee-department"
              value={departmentId}
              onChange={(event) => {
                setDepartmentId(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All departments</option>
              {(departments.data ?? []).map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name}
                </option>
              ))}
            </select>
          </label>

          <label className="inline-field" htmlFor="employee-status">
            <span>Status</span>
            <select
              id="employee-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as EmployeeStatus | '');
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="TERMINATED">Terminated</option>
            </select>
          </label>

          <label className="inline-field" htmlFor="employee-sort">
            <span>Sort</span>
            <select
              id="employee-sort"
              value={sort}
              onChange={(event) => {
                setSort(event.target.value as SortValue);
                setPage(0);
              }}
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Card>

      <Card title="Results">
        {employees.loading && <Loading label="Loading employees…" />}
        {employees.error !== null && <Banner>{employees.error}</Banner>}
        {departments.error !== null && <Banner tone="warning">{departments.error}</Banner>}

        {employees.data !== null && employees.data.content.length === 0 && (
          <EmptyState title="No employee matches these filters." hint="Try a wider search." />
        )}

        {employees.data !== null && employees.data.content.length > 0 && (
          <>
            <table className="table">
              <caption className="sr-only">Employees matching the current filters</caption>
              <thead>
                <tr>
                  <th scope="col">Employee</th>
                  <th scope="col">Number</th>
                  <th scope="col">Email</th>
                  <th scope="col">Job title</th>
                  <th scope="col">Department</th>
                  <th scope="col">Role</th>
                  <th scope="col">Hired</th>
                  <th scope="col">Salary</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {employees.data.content.map((employee) => {
                  const salary = readSalary(employee);
                  return (
                    <tr key={employee.id}>
                      <th scope="row">
                        <Link to={`/employees/${employee.id}`}>{employee.fullName}</Link>
                      </th>
                      <td>{employee.employeeNumber}</td>
                      <td>{employee.email}</td>
                      <td>{employee.jobTitle ?? '—'}</td>
                      <td>{employee.departmentName ?? 'Unassigned'}</td>
                      <td>{employee.role}</td>
                      <td>{formatDate(employee.hireDate)}</td>
                      {/* Blank when the API sent no salary — never a zero or a placeholder. */}
                      <td className="numeric">{salary === null ? '' : formatMoney(salary)}</td>
                      <td>
                        <StatusChip status={employee.status} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <Pagination
              page={employees.data.page}
              totalPages={employees.data.totalPages}
              totalElements={employees.data.totalElements}
              onChange={setPage}
              busy={employees.loading}
            />
          </>
        )}
      </Card>
    </>
  );
}
