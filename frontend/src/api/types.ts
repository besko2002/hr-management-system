/**
 * TypeScript mirrors of the backend DTO records. Field names and nullability follow the
 * Java records exactly; `BigDecimal` arrives as a JSON number, `LocalDate` as
 * `YYYY-MM-DD`, `Instant` as an ISO-8601 string and `LocalDateTime` without a zone.
 */

export type Role = 'ADMIN' | 'HR' | 'EMPLOYEE';
export const ROLES: readonly Role[] = ['EMPLOYEE', 'HR', 'ADMIN'];

export type EmployeeStatus = 'ACTIVE' | 'TERMINATED';

export type LeaveType = 'ANNUAL' | 'SICK' | 'UNPAID';
export const LEAVE_TYPES: readonly LeaveType[] = ['ANNUAL', 'SICK', 'UNPAID'];

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export type AttendanceStatus =
  | 'PRESENT'
  | 'LATE'
  | 'ABSENT'
  | 'ON_LEAVE'
  | 'HOLIDAY'
  | 'WEEKEND'
  | 'MISSING_CHECKOUT';

export type DayKind = 'WORKING_DAY' | 'WEEKEND' | 'HOLIDAY';

export type AttendanceSource = 'SELF' | 'CORRECTION' | 'IMPORT';

export type PayrollRunStatus = 'DRAFT' | 'FINALIZED';

/** `PageResponse<T>`: exactly these five keys, `page` is 0-based. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ----------------------------------------------------------------- auth

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** `AuthenticatedUser`: what login and change-password return. Carries no salary. */
export interface AuthenticatedUser {
  id: string;
  employeeNumber: string;
  fullName: string;
  email: string;
  role: Role;
  jobTitle: string | null;
  departmentName: string | null;
  mustChangePassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthenticatedUser;
}

// ----------------------------------------------------------------- employees

/**
 * `GET /api/employees/{id}` answers with one of two records: `EmployeeResponse`, which
 * carries `salary`, or `EmployeeView`, which has **no `salary` key at all** — that is how
 * the backend hides pay from a manager looking at a report. `salary` is therefore
 * optional here, and only ever rendered when the key is present and not null.
 */
export interface EmployeeProfile {
  id: string;
  employeeNumber: string;
  fullName: string;
  email: string;
  role: Role;
  jobTitle: string | null;
  departmentId: string | null;
  departmentName: string | null;
  managerId: string | null;
  managerName: string | null;
  hireDate: string;
  status: EmployeeStatus;
  createdAt: string;
  updatedAt: string;
  /** Present only on `EmployeeResponse`; `null` when the employee has no salary stored. */
  salary?: number | null;
  /** Present only on `EmployeeResponse`. */
  terminatedAt?: string | null;
  /** Present only on `EmployeeResponse`. */
  mustChangePassword?: boolean;
}

/** The full record, returned when the viewer is the employee themself or HR/ADMIN. */
export interface EmployeeRecord extends EmployeeProfile {
  salary: number | null;
  terminatedAt: string | null;
  mustChangePassword: boolean;
}

export interface CreateEmployeeRequest {
  fullName: string;
  email: string;
  role: Role;
  jobTitle: string | null;
  departmentId: string | null;
  managerId: string | null;
  hireDate: string;
  salary: number | null;
}

export interface UpdateEmployeeRequest {
  fullName: string;
  email: string;
  role: Role;
  jobTitle: string | null;
  departmentId: string | null;
  hireDate: string;
  salary: number | null;
}

export interface PatchEmployeeRequest {
  fullName?: string;
  email?: string;
  role?: Role;
  jobTitle?: string | null;
  departmentId?: string | null;
  hireDate?: string;
  salary?: number | null;
}

export interface CreatedEmployeeResponse {
  employee: EmployeeRecord;
  temporaryPassword: string;
}

export interface TeamMemberResponse {
  id: string;
  employeeNumber: string;
  fullName: string;
  email: string;
  jobTitle: string | null;
  departmentName: string | null;
  managerId: string | null;
  status: EmployeeStatus;
  depth: number;
}

export interface ChainMemberResponse {
  id: string;
  employeeNumber: string;
  fullName: string;
  email: string;
  jobTitle: string | null;
  departmentName: string | null;
  status: EmployeeStatus;
  level: number;
}

export interface OrgChartNode {
  id: string;
  employeeNumber: string;
  fullName: string;
  jobTitle: string | null;
  departmentName: string | null;
  children: OrgChartNode[];
}

export interface EmployeeQuery {
  q?: string;
  departmentId?: string;
  status?: EmployeeStatus;
  page?: number;
  size?: number;
  /** `"property,asc" | "property,desc"`; one of the allowed properties only. */
  sort?: string;
}

export const SORTABLE_EMPLOYEE_FIELDS = [
  'fullName',
  'email',
  'employeeNumber',
  'jobTitle',
  'hireDate',
  'salary',
  'status',
  'createdAt',
] as const;

// ----------------------------------------------------------------- audit

export interface AuditChangeResponse {
  revision: number;
  at: string;
  changedBy: string;
  field: string;
  before: string | null;
  after: string | null;
}

// ----------------------------------------------------------------- departments

export interface DepartmentResponse {
  id: string;
  name: string;
  createdAt: string;
}

export interface DepartmentRequest {
  name: string;
}

// ----------------------------------------------------------------- leave

export interface LeaveTypeResponse {
  code: LeaveType;
  paid: boolean;
  requiresBalance: boolean;
  annualAllowanceDays: number;
}

export interface LeaveBalanceResponse {
  leaveType: LeaveType;
  year: number;
  paid: boolean;
  requiresBalance: boolean;
  entitledDays: number;
  carriedOverDays: number;
  usedDays: number;
  pendingDays: number;
  /** null for types that do not consume a balance, such as UNPAID. */
  remainingDays: number | null;
}

export interface CreateLeaveRequestBody {
  type: LeaveType;
  startDate: string;
  endDate: string;
  reason: string | null;
}

export interface LeaveRequestResponse {
  id: string;
  employeeId: string;
  employeeName: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  year: number;
  workingDays: number;
  reason: string | null;
  status: LeaveStatus;
  decidedById: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface LeaveCalendarEntry {
  requestId: string;
  employeeId: string;
  employeeName: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  workingDays: number;
}

export interface HolidayResponse {
  id: string;
  date: string;
  name: string;
}

export interface HolidayRequestBody {
  date: string;
  name: string;
}

// ----------------------------------------------------------------- attendance

export interface SessionResponse {
  id: string;
  employeeId: string;
  workDate: string;
  checkIn: string;
  checkOut: string | null;
  localCheckIn: string;
  localCheckOut: string | null;
  minutes: number | null;
  source: AttendanceSource;
  correctedById: string | null;
  correctionReason: string | null;
}

export type LeaveCoverage = {
  leaveType: LeaveType;
  paid: boolean;
} | null;

export interface DayResponse {
  day: string;
  dayKind: DayKind;
  status: AttendanceStatus;
  leave: LeaveCoverage;
  firstIn: string | null;
  lastOut: string | null;
  sessionCount: number;
  workedMinutes: number;
  lateMinutes: number;
  overtimeMinutes: number;
  openSession: boolean;
  sessions: SessionResponse[];
}

export interface AttendanceRangeResponse {
  employeeId: string;
  employeeName: string;
  from: string;
  to: string;
  workingDays: number;
  presentDays: number;
  lateDays: number;
  absentDays: number;
  leaveDays: number;
  unpaidLeaveDays: number;
  missingCheckoutDays: number;
  workedMinutes: number;
  lateMinutes: number;
  overtimeMinutes: number;
  holidayMinutes: number;
  days: DayResponse[];
}

export interface TeamTodayEntry {
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  jobTitle: string | null;
  status: AttendanceStatus;
  leave: LeaveCoverage;
  firstIn: string | null;
  lastOut: string | null;
  currentlyIn: boolean;
  workedMinutes: number;
  lateMinutes: number;
  overtimeMinutes: number;
}

export interface TeamTodayResponse {
  day: string;
  dayKind: DayKind;
  teamSize: number;
  inCount: number;
  presentCount: number;
  lateCount: number;
  absentCount: number;
  onLeaveCount: number;
  missingCheckoutCount: number;
  members: TeamTodayEntry[];
}

// ----------------------------------------------------------------- payroll

export interface PayrollRunResponse {
  id: string;
  year: number;
  month: number;
  status: PayrollRunStatus;
  payslipCount: number;
  createdById: string | null;
  createdAt: string;
  finalizedById: string | null;
  finalizedAt: string | null;
}

export interface PayrollTotals {
  employees: number;
  baseSalary: number;
  proratedBase: number;
  overtimePay: number;
  holidayPay: number;
  unpaidLeaveDeduction: number;
  absenceDeduction: number;
  grossEarnings: number;
  insurance: number;
  tax: number;
  netPay: number;
}

export interface TaxBracketCharge {
  from: number;
  to: number | null;
  rate: number;
  taxedAmount: number;
  tax: number;
}

export interface TaxBracket {
  upTo: number | null;
  rate: number;
}

export interface PayrollRates {
  workingHoursPerDay: number;
  overtimeMultiplier: number;
  holidayOvertimeMultiplier: number;
  insuranceEmployeeRate: number;
  minInsurableWage: number;
  maxInsurableWage: number;
  personalExemptionMonthly: number;
  taxBrackets: TaxBracket[];
}

export interface PayslipResponse {
  id: string;
  runId: string;
  year: number;
  month: number;
  runStatus: PayrollRunStatus;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  jobTitle: string | null;
  departmentName: string | null;
  hireDate: string;
  terminatedAt: string | null;
  baseSalary: number;
  workingDaysInMonth: number;
  payableWorkingDays: number;
  unpaidLeaveDays: number;
  absentDays: number;
  lateMinutes: number;
  overtimeMinutes: number;
  holidayMinutes: number;
  dailyRate: number;
  hourlyRate: number;
  overtimeHours: number;
  holidayHours: number;
  proratedBase: number;
  overtimePay: number;
  holidayPay: number;
  unpaidLeaveDeduction: number;
  absenceDeduction: number;
  grossEarnings: number;
  insurableWage: number;
  insurance: number;
  personalExemption: number;
  taxableIncome: number;
  tax: number;
  netPay: number;
  netFloored: boolean;
  warning: string | null;
  taxBreakdown: TaxBracketCharge[];
  ratesUsed: PayrollRates;
  createdAt: string;
}

export interface PayrollRunDetailResponse {
  run: PayrollRunResponse;
  totals: PayrollTotals;
  payslips: PayslipResponse[];
}

export interface CreateRunBody {
  year: number;
  month: number;
}

// ----------------------------------------------------------------- reports

export interface HeadcountRow {
  department: string;
  status: string;
  count: number;
}

export interface LeaveSummaryRow {
  department: string;
  leaveType: string;
  usedDays: number;
  pendingDays: number;
  remainingDays: number;
}

export interface PayrollSummaryRow {
  month: number;
  grossEarnings: number;
  insurance: number;
  tax: number;
  netPay: number;
  payslipCount: number;
}

export interface AttendanceSummaryRow {
  department: string;
  lateDays: number;
  absentDays: number;
  overtimeMinutes: number;
}
