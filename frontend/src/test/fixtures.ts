import { writeToken } from '../api/token';
import type {
  AttendanceRangeResponse,
  AuditChangeResponse,
  DayResponse,
  EmployeeProfile,
  EmployeeRecord,
  HeadcountRow,
  LeaveBalanceResponse,
  LeaveRequestResponse,
  OrgChartNode,
  PageResponse,
  PayrollRunDetailResponse,
  PayrollRunResponse,
  PayslipResponse,
  Role,
  TeamMemberResponse,
} from '../api/types';
import type { RouteHandler } from './helpers';

export const TOKEN = 'test-jwt-token';

export function makeEmployee(overrides: Partial<EmployeeRecord> = {}): EmployeeRecord {
  return {
    id: 'emp-1',
    employeeNumber: 'EMP-0001',
    fullName: 'Nadia Hassan',
    email: 'nadia@hr.local',
    role: 'EMPLOYEE',
    jobTitle: 'Analyst',
    departmentId: 'dep-1',
    departmentName: 'Finance',
    managerId: 'emp-9',
    managerName: 'Omar Said',
    hireDate: '2023-04-02',
    status: 'ACTIVE',
    createdAt: '2023-04-02T08:00:00Z',
    updatedAt: '2025-01-05T08:00:00Z',
    salary: 18500,
    terminatedAt: null,
    mustChangePassword: false,
    ...overrides,
  };
}

/**
 * The `EmployeeView` shape a non-HR manager receives: the `salary` key is absent from the
 * payload entirely, not null. Built by deleting the key so the JSON really lacks it.
 */
export function makeEmployeeView(overrides: Partial<EmployeeProfile> = {}): EmployeeProfile {
  const base: Record<string, unknown> = { ...makeEmployee(), ...overrides };
  delete base.salary;
  delete base.terminatedAt;
  delete base.mustChangePassword;
  return base as unknown as EmployeeProfile;
}

export function makePage<T>(content: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  };
}

export function makeTeamMember(overrides: Partial<TeamMemberResponse> = {}): TeamMemberResponse {
  return {
    id: 'emp-2',
    employeeNumber: 'EMP-0002',
    fullName: 'Youssef Adel',
    email: 'youssef@hr.local',
    jobTitle: 'Developer',
    departmentName: 'Engineering',
    managerId: 'emp-1',
    status: 'ACTIVE',
    depth: 1,
    ...overrides,
  };
}

export function makeBalance(overrides: Partial<LeaveBalanceResponse> = {}): LeaveBalanceResponse {
  return {
    leaveType: 'ANNUAL',
    year: 2025,
    paid: true,
    requiresBalance: true,
    entitledDays: 21,
    carriedOverDays: 2,
    usedDays: 5,
    pendingDays: 1,
    remainingDays: 17,
    ...overrides,
  };
}

export function makeLeaveRequest(
  overrides: Partial<LeaveRequestResponse> = {},
): LeaveRequestResponse {
  return {
    id: 'leave-1',
    employeeId: 'emp-1',
    employeeName: 'Nadia Hassan',
    leaveType: 'ANNUAL',
    startDate: '2099-06-01',
    endDate: '2099-06-05',
    year: 2099,
    workingDays: 3,
    reason: 'Family trip',
    status: 'PENDING',
    decidedById: null,
    decidedByName: null,
    decidedAt: null,
    decisionNote: null,
    createdAt: '2025-01-05T08:00:00Z',
    ...overrides,
  };
}

export function makeDay(overrides: Partial<DayResponse> = {}): DayResponse {
  return {
    day: '2025-03-04',
    dayKind: 'WORKING_DAY',
    status: 'PRESENT',
    leave: null,
    firstIn: '2025-03-04T09:00:00',
    lastOut: '2025-03-04T17:05:00',
    sessionCount: 1,
    workedMinutes: 485,
    lateMinutes: 0,
    overtimeMinutes: 5,
    openSession: false,
    sessions: [],
    ...overrides,
  };
}

export function makeAttendanceRange(
  overrides: Partial<AttendanceRangeResponse> = {},
): AttendanceRangeResponse {
  return {
    employeeId: 'emp-1',
    employeeName: 'Nadia Hassan',
    from: '2025-03-01',
    to: '2025-03-31',
    workingDays: 21,
    presentDays: 18,
    lateDays: 2,
    absentDays: 1,
    leaveDays: 0,
    unpaidLeaveDays: 0,
    missingCheckoutDays: 0,
    workedMinutes: 9000,
    lateMinutes: 25,
    overtimeMinutes: 120,
    holidayMinutes: 0,
    days: [makeDay()],
    ...overrides,
  };
}

export function makePayslip(overrides: Partial<PayslipResponse> = {}): PayslipResponse {
  return {
    id: 'pay-1',
    runId: 'run-1',
    year: 2025,
    month: 3,
    runStatus: 'FINALIZED',
    employeeId: 'emp-1',
    employeeNumber: 'EMP-0001',
    employeeName: 'Nadia Hassan',
    jobTitle: 'Analyst',
    departmentName: 'Finance',
    hireDate: '2023-04-02',
    terminatedAt: null,
    baseSalary: 18500,
    workingDaysInMonth: 21,
    payableWorkingDays: 21,
    unpaidLeaveDays: 0,
    absentDays: 0,
    lateMinutes: 0,
    overtimeMinutes: 60,
    holidayMinutes: 0,
    dailyRate: 880.95,
    hourlyRate: 110.12,
    overtimeHours: 1,
    holidayHours: 0,
    proratedBase: 18500,
    overtimePay: 165.18,
    holidayPay: 0,
    unpaidLeaveDeduction: 0,
    absenceDeduction: 0,
    grossEarnings: 18665.18,
    insurableWage: 12600,
    insurance: 1386,
    personalExemption: 2000,
    taxableIncome: 15279.18,
    tax: 1527.92,
    netPay: 15751.26,
    netFloored: false,
    warning: null,
    taxBreakdown: [],
    ratesUsed: {
      workingHoursPerDay: 8,
      overtimeMultiplier: 1.5,
      holidayOvertimeMultiplier: 2,
      insuranceEmployeeRate: 0.11,
      minInsurableWage: 2000,
      maxInsurableWage: 12600,
      personalExemptionMonthly: 2000,
      taxBrackets: [{ upTo: null, rate: 0.1 }],
    },
    createdAt: '2025-04-01T08:00:00Z',
    ...overrides,
  };
}

export function makeRun(overrides: Partial<PayrollRunResponse> = {}): PayrollRunResponse {
  return {
    id: 'run-1',
    year: 2025,
    month: 3,
    status: 'DRAFT',
    payslipCount: 1,
    createdById: 'emp-9',
    createdAt: '2025-04-01T08:00:00Z',
    finalizedById: null,
    finalizedAt: null,
    ...overrides,
  };
}

export function makeRunDetail(
  overrides: Partial<PayrollRunDetailResponse> = {},
): PayrollRunDetailResponse {
  return {
    run: makeRun(),
    totals: {
      employees: 1,
      baseSalary: 18500,
      proratedBase: 18500,
      overtimePay: 165.18,
      holidayPay: 0,
      unpaidLeaveDeduction: 0,
      absenceDeduction: 0,
      grossEarnings: 18665.18,
      insurance: 1386,
      tax: 1527.92,
      netPay: 15751.26,
    },
    payslips: [makePayslip({ runStatus: 'DRAFT' })],
    ...overrides,
  };
}

export function makeAudit(overrides: Partial<AuditChangeResponse> = {}): AuditChangeResponse {
  return {
    revision: 12,
    at: '2025-02-01T10:30:00Z',
    changedBy: 'admin@hr.local',
    field: 'salary',
    before: '17000.00',
    after: '18500.00',
    ...overrides,
  };
}

export function makeOrgNode(overrides: Partial<OrgChartNode> = {}): OrgChartNode {
  return {
    id: 'emp-9',
    employeeNumber: 'EMP-0009',
    fullName: 'Omar Said',
    jobTitle: 'Head of Finance',
    departmentName: 'Finance',
    children: [],
    ...overrides,
  };
}

export function makeHeadcount(overrides: Partial<HeadcountRow> = {}): HeadcountRow {
  return { department: 'Finance', status: 'ACTIVE', count: 4, ...overrides };
}

/**
 * Stores a token and returns the two calls every signed-in render makes: the session
 * restore (`/api/employees/me`) and the manager probe (`/api/employees/me/team`).
 */
export function signedIn(
  options: {
    role?: Role;
    team?: TeamMemberResponse[];
    me?: Partial<EmployeeRecord>;
  } = {},
): Record<string, RouteHandler> {
  writeToken(TOKEN);
  const { role = 'EMPLOYEE', team = [], me = {} } = options;
  return {
    'GET /api/employees/me': { body: makeEmployee({ role, ...me }) },
    'GET /api/employees/me/team': { body: team },
  };
}
