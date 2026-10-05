import { createContext } from 'react';
import type { AuthenticatedUser, LoginRequest, Role } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

/**
 * The signed-in identity. Login returns exactly these fields; a reload rebuilds them from
 * `GET /api/employees/me` (there is no `/api/auth/me` in this API).
 */
export type SessionUser = AuthenticatedUser;

export interface AuthContextValue {
  status: AuthStatus;
  user: SessionUser | null;
  role: Role | null;
  /** True once `GET /api/employees/me/team` came back with at least one direct report. */
  isManager: boolean;
  /** False until the team probe settles, so the nav does not flicker manager links. */
  teamResolved: boolean;
  /** The forced change-password screen is shown while this is true. */
  mustChangePassword: boolean;
  /** One-off notice shown on the login page, e.g. after a 401. */
  notice: string | null;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => void;
  clearNotice: () => void;
  /** Applies the user returned by `POST /api/auth/change-password`. */
  applyUser: (user: SessionUser) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
