import { useState } from 'react';
import { ApiError, errorMessage } from '../api/ApiError';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { describedBy, Field } from '../components/Field';
import { validateEmail } from '../lib/validation';

/** The only way in: there is no public registration, HR/ADMIN create accounts. */
export function LoginPage(): JSX.Element {
  const { login, notice, clearNotice } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const nextEmailError = validateEmail(email);
    const nextPasswordError = password.length === 0 ? 'Password is required.' : null;
    setEmailError(nextEmailError);
    setPasswordError(nextPasswordError);
    if (nextEmailError !== null || nextPasswordError !== null) {
      return;
    }

    setBusy(true);
    setFormError(null);
    clearNotice();
    try {
      await login({ email: email.trim(), password });
    } catch (cause) {
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setEmailError(cause.fieldErrors.email ?? null);
        setPasswordError(cause.fieldErrors.password ?? null);
      }
      setFormError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page auth-page--split">
      <aside className="auth-hero" aria-label="About the system">
        <div className="auth-hero-inner">
          <div className="auth-logo" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="34" height="34" role="presentation">
              <rect width="32" height="32" rx="9" fill="rgb(255 255 255 / 16%)" />
              <circle cx="16" cy="9.5" r="3.2" fill="#fff" />
              <circle cx="8.5" cy="22" r="3" fill="#fff" opacity="0.85" />
              <circle cx="23.5" cy="22" r="3" fill="#fff" opacity="0.85" />
              <path d="M16 12.8v3.4M16 16.2 9.6 19.3M16 16.2l6.4 3.1" stroke="#fff" strokeWidth="1.6" fill="none" strokeLinecap="round" />
            </svg>
            <span>HR Management</span>
          </div>
          <h2 className="auth-hero-title">People, time and pay, in one place.</h2>
          <p className="auth-hero-sub">
            A workspace for employees, managers and HR, built around how your company is actually
            organised.
          </p>
          <ul className="auth-features">
            <li>
              <span className="auth-feature-icon" aria-hidden="true">◈</span>
              <span><strong>Org-aware access</strong>Managers see their whole team, never anyone&apos;s salary.</span>
            </li>
            <li>
              <span className="auth-feature-icon" aria-hidden="true">✦</span>
              <span><strong>Leave that adds up</strong>Balances, approvals and working days, handled for you.</span>
            </li>
            <li>
              <span className="auth-feature-icon" aria-hidden="true">▤</span>
              <span><strong>Attendance and payroll</strong>Clock in, review the month, download the payslip.</span>
            </li>
            <li>
              <span className="auth-feature-icon" aria-hidden="true">◎</span>
              <span><strong>Every change on record</strong>A full audit trail for sensitive data.</span>
            </li>
          </ul>
        </div>
      </aside>
      <section className="auth-panel">
      <div className="auth-card auth-card--login">
        <p className="auth-brand">Welcome back</p>
        <h1>Sign in</h1>
        <p className="auth-lead">
          Use the work email your HR team registered. Accounts are created by HR — there is no
          self-service sign-up.
        </p>

        {notice !== null && (
          <Banner tone="info" onDismiss={clearNotice}>
            {notice}
          </Banner>
        )}
        {formError !== null && <Banner tone="error">{formError}</Banner>}

        <form onSubmit={onSubmit} noValidate>
          <Field id="email" label="Work email" error={emailError} required>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              value={email}
              required
              aria-invalid={emailError !== null}
              aria-describedby={describedBy('email', false, emailError !== null)}
              onChange={(event) => {
                setEmail(event.target.value);
              }}
            />
          </Field>

          <Field id="password" label="Password" error={passwordError} required>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              required
              aria-invalid={passwordError !== null}
              aria-describedby={describedBy('password', false, passwordError !== null)}
              onChange={(event) => {
                setPassword(event.target.value);
              }}
            />
          </Field>

          <button type="submit" className="button button-primary button-block" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
      <p className="auth-footnote">Need an account? Ask your HR team.</p>
      </section>
    </main>
  );
}
