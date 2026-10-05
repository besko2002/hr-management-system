import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { isAdmin, isHrOrAdmin, canSeeTeamScreens } from './roles';
import { useAuth } from './useAuth';

function SessionLoading(): JSX.Element {
  return (
    <div className="centered-page" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p>Restoring your session…</p>
    </div>
  );
}

/** Blocks the app until we know the visitor is signed in. */
export function ProtectedRoute(): JSX.Element {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <SessionLoading />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

/** Keeps signed-in users away from /login. */
export function PublicOnlyRoute(): JSX.Element {
  const { status } = useAuth();

  if (status === 'loading') {
    return <SessionLoading />;
  }
  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

/**
 * A seeded or reset account carries `mustChangePassword`. Nothing else in the app opens
 * until the password is changed — the API would refuse most calls anyway.
 */
export function PasswordChangeGate(): JSX.Element {
  const { mustChangePassword } = useAuth();

  if (mustChangePassword) {
    return <Navigate to="/change-password" replace />;
  }
  return <Outlet />;
}

/** HR and ADMIN screens. The server enforces the same rule; this only hides the UI. */
export function HrRoute(): JSX.Element {
  const { role } = useAuth();
  return isHrOrAdmin(role) ? <Outlet /> : <Navigate to="/403" replace />;
}

/** ADMIN-only screens. */
export function AdminRoute(): JSX.Element {
  const { role } = useAuth();
  return isAdmin(role) ? <Outlet /> : <Navigate to="/403" replace />;
}

/** Manager screens: anyone with direct reports, plus HR/ADMIN. */
export function ManagerRoute(): JSX.Element {
  const { role, isManager, teamResolved } = useAuth();

  if (!teamResolved && !isHrOrAdmin(role)) {
    return <SessionLoading />;
  }
  return canSeeTeamScreens(role, isManager) ? <Outlet /> : <Navigate to="/403" replace />;
}
