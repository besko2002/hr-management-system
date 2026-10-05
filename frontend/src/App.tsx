import { Route, Routes } from 'react-router-dom';
import {
  AdminRoute,
  HrRoute,
  ManagerRoute,
  PasswordChangeGate,
  ProtectedRoute,
  PublicOnlyRoute,
} from './auth/RouteGuards';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { AppShell } from './pages/AppShell';
import { AttendancePage } from './pages/AttendancePage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { CreateEmployeePage } from './pages/CreateEmployeePage';
import { DashboardPage } from './pages/DashboardPage';
import { DepartmentsPage } from './pages/DepartmentsPage';
import { EmployeeDetailPage } from './pages/EmployeeDetailPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { ForbiddenPage, NotFoundPage } from './pages/ForbiddenPage';
import { HolidaysPage } from './pages/HolidaysPage';
import { LeaveOverviewPage } from './pages/LeaveOverviewPage';
import { LeavePage } from './pages/LeavePage';
import { LoginPage } from './pages/LoginPage';
import { OrgChartPage } from './pages/OrgChartPage';
import { PayrollPage } from './pages/PayrollPage';
import { PayrollRunPage } from './pages/PayrollRunPage';
import { PayslipsPage } from './pages/PayslipsPage';
import { ProfilePage } from './pages/ProfilePage';
import { ReportsPage } from './pages/ReportsPage';
import { TeamCalendarPage } from './pages/TeamCalendarPage';
import { TeamPage } from './pages/TeamPage';
import { TeamTodayPage } from './pages/TeamTodayPage';

/**
 * Every signed-in route sits behind `ProtectedRoute`, then behind the forced
 * change-password gate, then behind a role guard where the role matters. A guard that
 * refuses sends the visitor to /403: the API enforces the same boundary server-side.
 */
export function App(): JSX.Element {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />

        <Route element={<PasswordChangeGate />}>
          <Route path="/" element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="leave" element={<LeavePage />} />
            <Route path="attendance" element={<AttendancePage />} />
            <Route path="payslips" element={<PayslipsPage />} />
            <Route path="org-chart" element={<OrgChartPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="403" element={<ForbiddenPage />} />

            <Route element={<ManagerRoute />}>
              <Route path="team" element={<TeamPage />} />
              <Route path="approvals" element={<ApprovalsPage />} />
              <Route path="team/calendar" element={<TeamCalendarPage />} />
              <Route path="team/today" element={<TeamTodayPage />} />
            </Route>

            <Route element={<HrRoute />}>
              <Route path="employees" element={<EmployeesPage />} />
              <Route path="employees/new" element={<CreateEmployeePage />} />
              <Route path="employees/:id" element={<EmployeeDetailPage />} />
              <Route path="departments" element={<DepartmentsPage />} />
              <Route path="holidays" element={<HolidaysPage />} />
              <Route path="leave-overview" element={<LeaveOverviewPage />} />
              <Route path="payroll" element={<PayrollPage />} />
              <Route path="payroll/:runId" element={<PayrollRunPage />} />
              <Route path="reports" element={<ReportsPage />} />
            </Route>

            {/* Reserved for admin-only screens; the guard is wired and tested. */}
            <Route element={<AdminRoute />}>
              <Route path="admin" element={<ForbiddenPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}
