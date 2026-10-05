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
    <main className="auth-page">
      <div className="auth-card">
        <p className="auth-brand">HR Management System</p>
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
    </main>
  );
}
