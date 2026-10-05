import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/api';
import { ApiError } from '../api/ApiError';
import { readToken, writeToken } from '../api/token';
import type { EmployeeRecord, LoginRequest } from '../api/types';
import { SESSION_EXPIRED_NOTICE } from '../lib/messages';
import { AuthContext, type AuthContextValue, type AuthStatus, type SessionUser } from './authContext';

/** `GET /api/employees/me` carries every field the session needs, plus pay we drop here. */
function toSessionUser(me: EmployeeRecord): SessionUser {
  return {
    id: me.id,
    employeeNumber: me.employeeNumber,
    fullName: me.fullName,
    email: me.email,
    role: me.role,
    jobTitle: me.jobTitle,
    departmentName: me.departmentName,
    mustChangePassword: me.mustChangePassword,
  };
}

export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [token, setToken] = useState<string | null>(() => readToken());
  const [user, setUser] = useState<SessionUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>(() =>
    readToken() === null ? 'anonymous' : 'loading',
  );
  const [notice, setNotice] = useState<string | null>(null);
  const [isManager, setIsManager] = useState(false);
  const [teamResolved, setTeamResolved] = useState(false);

  /** Token we already resolved to a user, so login() does not re-fetch /me. */
  const resolvedToken = useRef<string | null>(null);

  const clearSession = useCallback((nextNotice: string | null): void => {
    writeToken(null);
    resolvedToken.current = null;
    setToken(null);
    setUser(null);
    setStatus('anonymous');
    setIsManager(false);
    setTeamResolved(false);
    setNotice(nextNotice);
  }, []);

  // Any authenticated 401 means the token is gone (expired, or the account was
  // terminated): drop the session and let the guards bounce to /login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession(SESSION_EXPIRED_NOTICE);
    });
    return () => {
      setUnauthorizedHandler(null);
    };
  }, [clearSession]);

  // On a cold start with a stored token, confirm who we are.
  useEffect(() => {
    if (token === null || resolvedToken.current === token) {
      return;
    }
    const controller = new AbortController();
    let cancelled = false;
    setStatus('loading');

    api
      .me(controller.signal)
      .then((me) => {
        if (cancelled) {
          return;
        }
        // Only now is the token resolved. Marking it earlier broke React StrictMode,
        // which runs this effect twice: the second run saw "already resolved", skipped
        // the call and the app never left the loading state.
        resolvedToken.current = token;
        setUser(toSessionUser(me));
        setStatus('authenticated');
      })
      .catch((cause: unknown) => {
        if (cancelled) {
          return;
        }
        if (cause instanceof ApiError && cause.status === 401) {
          // The 401 handler already cleared the session and set the notice.
          return;
        }
        clearSession(null);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [token, clearSession]);

  // "Manager" is not a role: it means having direct reports. Probe once per identity.
  // A failure here must never cost the session — it only hides the manager links.
  const userId = user?.id ?? null;
  const forced = user?.mustChangePassword === true;
  useEffect(() => {
    if (userId === null || status !== 'authenticated' || forced) {
      return;
    }
    const controller = new AbortController();
    let cancelled = false;

    api
      .myTeam(controller.signal)
      .then((team) => {
        if (!cancelled) {
          setIsManager(team.length > 0);
          setTeamResolved(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setIsManager(false);
          setTeamResolved(true);
        }
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [userId, status, forced]);

  const login = useCallback(async (payload: LoginRequest): Promise<void> => {
    const response = await api.login(payload);
    writeToken(response.accessToken);
    resolvedToken.current = response.accessToken;
    setToken(response.accessToken);
    setUser(response.user);
    setStatus('authenticated');
    setNotice(null);
  }, []);

  const logout = useCallback((): void => {
    clearSession(null);
  }, [clearSession]);

  const clearNotice = useCallback((): void => {
    setNotice(null);
  }, []);

  const applyUser = useCallback((next: SessionUser): void => {
    setUser(next);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      role: user?.role ?? null,
      isManager,
      teamResolved,
      mustChangePassword: user?.mustChangePassword === true,
      notice,
      login,
      logout,
      clearNotice,
      applyUser,
    }),
    [status, user, isManager, teamResolved, notice, login, logout, clearNotice, applyUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
