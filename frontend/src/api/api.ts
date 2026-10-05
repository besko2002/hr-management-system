import { ApiError, NETWORK_ERROR_MESSAGE, NETWORK_ERROR_STATUS, toApiError } from './ApiError';
import { readToken } from './token';
import type {
  AttendanceRangeResponse,
  AttendanceSummaryRow,
  AuditChangeResponse,
  AuthenticatedUser,
  ChangePasswordRequest,
  CreateEmployeeRequest,
  CreateLeaveRequestBody,
  CreateRunBody,
  CreatedEmployeeResponse,
  DepartmentRequest,
  DepartmentResponse,
  EmployeeProfile,
  EmployeeQuery,
  EmployeeRecord,
  HeadcountRow,
  HolidayRequestBody,
  HolidayResponse,
  LeaveBalanceResponse,
  LeaveCalendarEntry,
  LeaveRequestResponse,
  LeaveStatus,
  LeaveSummaryRow,
  LoginRequest,
  LoginResponse,
  OrgChartNode,
  PageResponse,
  PatchEmployeeRequest,
  PayrollRunDetailResponse,
  PayrollRunResponse,
  PayrollSummaryRow,
  PayslipResponse,
  SessionResponse,
  TeamMemberResponse,
  TeamTodayResponse,
  UpdateEmployeeRequest,
} from './types';

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * Registers the callback fired when an *authenticated* call comes back 401, i.e. the JWT
 * expired or the account was terminated. The auth provider uses it to drop the session
 * and bounce the user to /login with a notice.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Serialised as a JSON request body. */
  json?: unknown;
  /** Set to false for /api/auth/login, the only public endpoint we call. */
  auth?: boolean;
  signal?: AbortSignal;
}

function parseJsonText(text: string): unknown {
  if (text.trim().length === 0) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

async function readBody(response: Response): Promise<unknown> {
  try {
    return parseJsonText(await response.text());
  } catch {
    return null;
  }
}

function authHeaders(auth: boolean, accept: string): Record<string, string> {
  const headers: Record<string, string> = { Accept: accept };
  if (auth) {
    const token = readToken();
    if (token !== null && token.length > 0) {
      headers.Authorization = `Bearer ${token}`;
    }
  }
  return headers;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', json, auth = true, signal } = options;
  const headers = authHeaders(auth, 'application/json');

  let body: string | undefined;
  if (json !== undefined) {
    body = JSON.stringify(json);
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body, signal });
  } catch (cause) {
    if (cause instanceof Error && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError(NETWORK_ERROR_STATUS, NETWORK_ERROR_MESSAGE);
  }

  if (!response.ok) {
    const error = toApiError(response.status, response.statusText, await readBody(response));
    if (response.status === 401 && auth) {
      unauthorizedHandler?.();
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await readBody(response)) as T;
}

/**
 * Downloads a binary file (payslip PDF, payroll .xlsx) with the bearer token attached —
 * a plain link cannot carry the header, so the bytes come back as a Blob and the caller
 * hands it to {@link saveBlob}. Errors still arrive as `ApiError` JSON.
 */
export async function requestBlob(path: string, signal?: AbortSignal): Promise<Blob> {
  const headers = authHeaders(true, 'application/octet-stream');

  let response: Response;
  try {
    response = await fetch(path, { method: 'GET', headers, signal });
  } catch (cause) {
    if (cause instanceof Error && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError(NETWORK_ERROR_STATUS, NETWORK_ERROR_MESSAGE);
  }

  if (!response.ok) {
    const error = toApiError(response.status, response.statusText, await readBody(response));
    if (response.status === 401) {
      unauthorizedHandler?.();
    }
    throw error;
  }
  return await response.blob();
}

const seg = (value: string): string => encodeURIComponent(value);

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    search.append(key, String(value));
  }
  const text = search.toString();
  return text.length > 0 ? `?${text}` : '';
}

export const api = {
  // ----------------------------------------------------------------- auth
  login: (payload: LoginRequest): Promise<LoginResponse> =>
    request<LoginResponse>('/api/auth/login', { method: 'POST', json: payload, auth: false }),

  changePassword: (payload: ChangePasswordRequest): Promise<AuthenticatedUser> =>
    request<AuthenticatedUser>('/api/auth/change-password', { method: 'POST', json: payload }),

  // ----------------------------------------------------------------- employees
  me: (signal?: AbortSignal): Promise<EmployeeRecord> =>
    request<EmployeeRecord>('/api/employees/me', { signal }),

  myTeam: (signal?: AbortSignal): Promise<TeamMemberResponse[]> =>
    request<TeamMemberResponse[]>('/api/employees/me/team', { signal }),

  myTeamAll: (signal?: AbortSignal): Promise<TeamMemberResponse[]> =>
    request<TeamMemberResponse[]>('/api/employees/me/team/all', { signal }),

  listEmployees: (params: EmployeeQuery, signal?: AbortSignal): Promise<PageResponse<EmployeeRecord>> =>
    request<PageResponse<EmployeeRecord>>(
      `/api/employees${query({
        q: params.q,
        departmentId: params.departmentId,
        status: params.status,
        page: params.page,
        size: params.size,
        sort: params.sort,
      })}`,
      { signal },
    ),

  getEmployee: (id: string, signal?: AbortSignal): Promise<EmployeeProfile> =>
    request<EmployeeProfile>(`/api/employees/${seg(id)}`, { signal }),

  createEmployee: (payload: CreateEmployeeRequest): Promise<CreatedEmployeeResponse> =>
    request<CreatedEmployeeResponse>('/api/employees', { method: 'POST', json: payload }),

  updateEmployee: (id: string, payload: UpdateEmployeeRequest): Promise<EmployeeRecord> =>
    request<EmployeeRecord>(`/api/employees/${seg(id)}`, { method: 'PUT', json: payload }),

  patchEmployee: (id: string, payload: PatchEmployeeRequest): Promise<EmployeeRecord> =>
    request<EmployeeRecord>(`/api/employees/${seg(id)}`, { method: 'PATCH', json: payload }),

  terminateEmployee: (id: string): Promise<EmployeeRecord> =>
    request<EmployeeRecord>(`/api/employees/${seg(id)}/terminate`, { method: 'POST' }),

  setManager: (id: string, managerId: string | null): Promise<EmployeeProfile> =>
    request<EmployeeProfile>(`/api/employees/${seg(id)}/manager`, {
      method: 'PUT',
      json: { managerId },
    }),

  orgChart: (signal?: AbortSignal): Promise<OrgChartNode[]> =>
    request<OrgChartNode[]>('/api/org-chart', { signal }),

  // ----------------------------------------------------------------- audit
  employeeAudit: (
    id: string,
    page: number,
    size: number,
    signal?: AbortSignal,
  ): Promise<PageResponse<AuditChangeResponse>> =>
    request<PageResponse<AuditChangeResponse>>(
      `/api/audit/employees/${seg(id)}${query({ page, size })}`,
      { signal },
    ),

  // ----------------------------------------------------------------- departments
  listDepartments: (signal?: AbortSignal): Promise<DepartmentResponse[]> =>
    request<DepartmentResponse[]>('/api/departments', { signal }),

  createDepartment: (payload: DepartmentRequest): Promise<DepartmentResponse> =>
    request<DepartmentResponse>('/api/departments', { method: 'POST', json: payload }),

  renameDepartment: (id: string, payload: DepartmentRequest): Promise<DepartmentResponse> =>
    request<DepartmentResponse>(`/api/departments/${seg(id)}`, { method: 'PUT', json: payload }),

  deleteDepartment: (id: string): Promise<void> =>
    request<void>(`/api/departments/${seg(id)}`, { method: 'DELETE' }),

  // ----------------------------------------------------------------- leave
  myLeaveBalances: (year?: number, signal?: AbortSignal): Promise<LeaveBalanceResponse[]> =>
    request<LeaveBalanceResponse[]>(`/api/leave/balances/me${query({ year })}`, { signal }),

  createLeaveRequest: (payload: CreateLeaveRequestBody): Promise<LeaveRequestResponse> =>
    request<LeaveRequestResponse>('/api/leave/requests', { method: 'POST', json: payload }),

  myLeaveRequests: (
    params: { status?: LeaveStatus; year?: number; page?: number; size?: number },
    signal?: AbortSignal,
  ): Promise<PageResponse<LeaveRequestResponse>> =>
    request<PageResponse<LeaveRequestResponse>>(`/api/leave/requests/me${query({ ...params })}`, {
      signal,
    }),

  pendingLeaveRequests: (
    page: number,
    size: number,
    signal?: AbortSignal,
  ): Promise<PageResponse<LeaveRequestResponse>> =>
    request<PageResponse<LeaveRequestResponse>>(
      `/api/leave/requests/pending${query({ page, size })}`,
      { signal },
    ),

  approveLeave: (id: string, decisionNote: string | null): Promise<LeaveRequestResponse> =>
    request<LeaveRequestResponse>(`/api/leave/requests/${seg(id)}/approve`, {
      method: 'POST',
      json: { decisionNote },
    }),

  rejectLeave: (id: string, decisionNote: string): Promise<LeaveRequestResponse> =>
    request<LeaveRequestResponse>(`/api/leave/requests/${seg(id)}/reject`, {
      method: 'POST',
      json: { decisionNote },
    }),

  cancelLeave: (id: string, decisionNote: string | null): Promise<LeaveRequestResponse> =>
    request<LeaveRequestResponse>(`/api/leave/requests/${seg(id)}/cancel`, {
      method: 'POST',
      json: { decisionNote },
    }),

  leaveCalendar: (from: string, to: string, signal?: AbortSignal): Promise<LeaveCalendarEntry[]> =>
    request<LeaveCalendarEntry[]>(`/api/leave/calendar${query({ from, to })}`, { signal }),

  listHolidays: (
    params: { from?: string; to?: string } = {},
    signal?: AbortSignal,
  ): Promise<HolidayResponse[]> =>
    request<HolidayResponse[]>(`/api/leave/holidays${query({ ...params })}`, { signal }),

  createHoliday: (payload: HolidayRequestBody): Promise<HolidayResponse> =>
    request<HolidayResponse>('/api/leave/holidays', { method: 'POST', json: payload }),

  updateHoliday: (id: string, payload: HolidayRequestBody): Promise<HolidayResponse> =>
    request<HolidayResponse>(`/api/leave/holidays/${seg(id)}`, { method: 'PUT', json: payload }),

  deleteHoliday: (id: string): Promise<void> =>
    request<void>(`/api/leave/holidays/${seg(id)}`, { method: 'DELETE' }),

  // ----------------------------------------------------------------- attendance
  checkIn: (): Promise<SessionResponse> =>
    request<SessionResponse>('/api/attendance/check-in', { method: 'POST' }),

  checkOut: (): Promise<SessionResponse> =>
    request<SessionResponse>('/api/attendance/check-out', { method: 'POST' }),

  myAttendance: (from: string, to: string, signal?: AbortSignal): Promise<AttendanceRangeResponse> =>
    request<AttendanceRangeResponse>(`/api/attendance/me${query({ from, to })}`, { signal }),

  employeeAttendance: (
    employeeId: string,
    from: string,
    to: string,
    signal?: AbortSignal,
  ): Promise<AttendanceRangeResponse> =>
    request<AttendanceRangeResponse>(
      `/api/attendance/employees/${seg(employeeId)}${query({ from, to })}`,
      { signal },
    ),

  teamToday: (signal?: AbortSignal): Promise<TeamTodayResponse> =>
    request<TeamTodayResponse>('/api/attendance/team/today', { signal }),

  // ----------------------------------------------------------------- payroll
  listPayrollRuns: (
    page: number,
    size: number,
    signal?: AbortSignal,
  ): Promise<PageResponse<PayrollRunResponse>> =>
    request<PageResponse<PayrollRunResponse>>(`/api/payroll/runs${query({ page, size })}`, {
      signal,
    }),

  createPayrollRun: (payload: CreateRunBody): Promise<PayrollRunDetailResponse> =>
    request<PayrollRunDetailResponse>('/api/payroll/runs', { method: 'POST', json: payload }),

  getPayrollRun: (runId: string, signal?: AbortSignal): Promise<PayrollRunDetailResponse> =>
    request<PayrollRunDetailResponse>(`/api/payroll/runs/${seg(runId)}`, { signal }),

  recalculatePayrollRun: (runId: string): Promise<PayrollRunDetailResponse> =>
    request<PayrollRunDetailResponse>(`/api/payroll/runs/${seg(runId)}/recalculate`, {
      method: 'POST',
    }),

  finalizePayrollRun: (runId: string): Promise<PayrollRunResponse> =>
    request<PayrollRunResponse>(`/api/payroll/runs/${seg(runId)}/finalize`, { method: 'POST' }),

  deletePayrollRun: (runId: string): Promise<void> =>
    request<void>(`/api/payroll/runs/${seg(runId)}`, { method: 'DELETE' }),

  payrollRunXlsx: (runId: string): Promise<Blob> =>
    requestBlob(`/api/payroll/runs/${seg(runId)}/export.xlsx`),

  myPayslips: (signal?: AbortSignal): Promise<PayslipResponse[]> =>
    request<PayslipResponse[]>('/api/payroll/payslips/me', { signal }),

  getPayslip: (payslipId: string, signal?: AbortSignal): Promise<PayslipResponse> =>
    request<PayslipResponse>(`/api/payroll/payslips/${seg(payslipId)}`, { signal }),

  payslipPdf: (payslipId: string): Promise<Blob> =>
    requestBlob(`/api/payroll/payslips/${seg(payslipId)}/pdf`),

  // ----------------------------------------------------------------- reports
  headcountReport: (signal?: AbortSignal): Promise<HeadcountRow[]> =>
    request<HeadcountRow[]>('/api/reports/headcount', { signal }),

  leaveSummaryReport: (year: number, signal?: AbortSignal): Promise<LeaveSummaryRow[]> =>
    request<LeaveSummaryRow[]>(`/api/reports/leave-summary${query({ year })}`, { signal }),

  payrollSummaryReport: (year: number, signal?: AbortSignal): Promise<PayrollSummaryRow[]> =>
    request<PayrollSummaryRow[]>(`/api/reports/payroll-summary${query({ year })}`, { signal }),

  attendanceSummaryReport: (
    year: number,
    month: number,
    signal?: AbortSignal,
  ): Promise<AttendanceSummaryRow[]> =>
    request<AttendanceSummaryRow[]>(`/api/reports/attendance-summary${query({ year, month })}`, {
      signal,
    }),

  headcountXlsx: (): Promise<Blob> => requestBlob('/api/reports/headcount.xlsx'),

  attendanceXlsx: (year: number, month: number): Promise<Blob> =>
    requestBlob(`/api/attendance/export.xlsx${query({ year, month })}`),
};

export type Api = typeof api;
