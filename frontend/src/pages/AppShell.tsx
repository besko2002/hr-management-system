import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { canSeeTeamScreens, isHrOrAdmin } from '../auth/roles';
import { useAuth } from '../auth/useAuth';
import { humanizeEnum } from '../lib/format';

interface NavItem {
  to: string;
  label: string;
}

/**
 * The frame around every signed-in screen. Menu entries a role cannot use are not
 * rendered at all; the matching routes are guarded too, and the server decides for real.
 */
export function AppShell(): JSX.Element {
  const { user, role, isManager, logout } = useAuth();
  const [navOpen, setNavOpen] = useState(false);

  const mine: NavItem[] = [
    { to: '/', label: 'Dashboard' },
    { to: '/leave', label: 'Leave' },
    { to: '/attendance', label: 'Attendance' },
    { to: '/payslips', label: 'Payslips' },
    { to: '/org-chart', label: 'Org chart' },
    { to: '/profile', label: 'Profile' },
  ];

  const team: NavItem[] = canSeeTeamScreens(role, isManager)
    ? [
        { to: '/team', label: 'My team' },
        { to: '/approvals', label: 'Approvals' },
        { to: '/team/calendar', label: 'Team calendar' },
        { to: '/team/today', label: 'Team today' },
      ]
    : [];

  const admin: NavItem[] = isHrOrAdmin(role)
    ? [
        { to: '/employees', label: 'Employees' },
        { to: '/departments', label: 'Departments' },
        { to: '/holidays', label: 'Holidays' },
        { to: '/leave-overview', label: 'Leave overview' },
        { to: '/payroll', label: 'Payroll' },
        { to: '/reports', label: 'Reports' },
      ]
    : [];

  function renderGroup(title: string, items: NavItem[]): JSX.Element | null {
    if (items.length === 0) {
      return null;
    }
    return (
      <div className="nav-group">
        <p className="nav-group-title">{title}</p>
        <ul>
          {items.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => (isActive ? 'nav-link nav-link-active' : 'nav-link')}
                onClick={() => {
                  setNavOpen(false);
                }}
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  return (
    <div className="shell">
      <header className="topbar">
        <button
          type="button"
          className="icon-button nav-toggle"
          aria-expanded={navOpen}
          aria-controls="main-nav"
          aria-label={navOpen ? 'Hide navigation' : 'Show navigation'}
          onClick={() => {
            setNavOpen((open) => !open);
          }}
        >
          ☰
        </button>
        <p className="topbar-brand">HR Management System</p>
        <div className="topbar-user">
          <span className="topbar-name">{user?.fullName ?? ''}</span>
          <span className="topbar-role">{role === null ? '' : humanizeEnum(role)}</span>
          <button type="button" className="button button-small" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>

      <nav
        id="main-nav"
        className={navOpen ? 'sidebar sidebar-open' : 'sidebar'}
        aria-label="Main navigation"
      >
        {renderGroup('Me', mine)}
        {renderGroup('Team', team)}
        {renderGroup('Administration', admin)}
      </nav>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
