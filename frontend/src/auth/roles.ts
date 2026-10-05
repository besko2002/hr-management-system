import type { Role } from '../api/types';

export function isAdmin(role: Role | null): boolean {
  return role === 'ADMIN';
}

/** HR and ADMIN share every administrative screen; only the ADMIN role itself differs. */
export function isHrOrAdmin(role: Role | null): boolean {
  return role === 'HR' || role === 'ADMIN';
}

/**
 * Who may open the manager screens: anyone with direct reports, plus HR/ADMIN, whose
 * pending-approvals and calendar endpoints return the whole company.
 */
export function canSeeTeamScreens(role: Role | null, isManager: boolean): boolean {
  return isManager || isHrOrAdmin(role);
}

/**
 * The only place that decides whether a salary may be rendered: the key must be present
 * in the payload and carry a value. A manager's view of a report (`EmployeeView`) has no
 * `salary` key at all, and an employee with no salary stored has it as null — neither may
 * be shown as "0" or "hidden".
 */
export function readSalary(payload: { salary?: number | null }): number | null {
  if (!('salary' in payload)) {
    return null;
  }
  const value = payload.salary;
  return typeof value === 'number' ? value : null;
}

export function hasSalary(payload: { salary?: number | null }): boolean {
  return readSalary(payload) !== null;
}
