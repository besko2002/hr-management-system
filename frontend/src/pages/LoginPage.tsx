import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage } from '../api/ApiError';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { RotatingWord } from '../components/RotatingWord';
import { describedBy, Field } from '../components/Field';
import { useHeroMotion } from '../lib/useHeroMotion';
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
  const [showPassword, setShowPassword] = useState(false);
  const heroRef = useHeroMotion();

  // The form stays hidden until the visitor hovers, focuses or clicks the entrance button.
  // Hovering only peeks (it closes again when the pointer leaves); a click or keyboard focus pins it open.
  const [open, setOpen] = useState(false);
  const [focusRequest, setFocusRequest] = useState(0);
  const pinned = useRef(false);
  const hideTimer = useRef<number | undefined>(undefined);
  const zoneRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);

  function reveal(pin: boolean): void {
    window.clearTimeout(hideTimer.current);
    if (pin) {
      pinned.current = true;
    }
    if (!open) {
      setOpen(true);
    }
  }

  function scheduleHide(): void {
    if (pinned.current) {
      return;
    }
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      if (!pinned.current && !(zoneRef.current?.contains(document.activeElement) ?? false)) {
        setOpen(false);
      }
    }, 450);
  }

  function hide(): void {
    window.clearTimeout(hideTimer.current);
    pinned.current = false;
    setOpen(false);
    triggerRef.current?.focus();
  }

  // Only an explicit click / Enter on the entrance button moves focus into the form: just tabbing
  // onto the button reveals it without stealing focus from the keyboard user.
  useEffect(() => {
    if (open && focusRequest > 0) {
      emailRef.current?.focus();
    }
  }, [open, focusRequest]);

  useEffect(() => {
    return () => {
      window.clearTimeout(hideTimer.current);
    };
  }, []);

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
    <main className={open ? 'auth-page auth-page--one auth-page--open' : 'auth-page auth-page--one'}>
      <section className="auth-hero" aria-label="About the system" ref={heroRef}>
        <span className="auth-orb auth-orb--a" aria-hidden="true" />
        <span className="auth-orb auth-orb--b" aria-hidden="true" />
        <span className="auth-orb auth-orb--c" aria-hidden="true" />

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

        <div className="auth-hero-inner">
          <h2 className="auth-hero-title">
            Your <RotatingWord words={['people', 'leave', 'attendance', 'payroll']} />
            <br />
            <span className="auth-hero-accent">in one place.</span>
          </h2>
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

        <div
          className="auth-zone"
          ref={zoneRef}
          onPointerEnter={() => {
            reveal(false);
          }}
          onPointerLeave={scheduleHide}
          onFocus={() => {
            reveal(true);
          }}
          onPointerDown={() => {
            // Hovering reveals the form right over the button, so the click usually lands on the form:
            // any press inside this area pins it open.
            reveal(true);
          }}
          onClick={(event) => {
            // Focus is requested on click, not on pointer-down: the browser moves focus on mouse-down
            // and would undo it. A click on empty card space puts the cursor in the email field.
            const target = event.target instanceof Element ? event.target : null;
            if (target !== null && target.closest('input, button, select, textarea, a, label') === null) {
              setFocusRequest((count) => count + 1);
            }
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape' && open) {
              hide();
            }
          }}
        >
          <button
            type="button"
            ref={triggerRef}
            className="auth-open"
            aria-expanded={open}
            aria-controls="auth-login-card"
            onClick={() => {
              reveal(true);
              setFocusRequest((count) => count + 1);
            }}
          >
            <span className="auth-open-glow" aria-hidden="true" />
            <span>Enter workspace</span>
            <span className="auth-open-arrow" aria-hidden="true">→</span>
          </button>
          <p className="auth-open-hint">Hover or tap to reveal the form</p>

          <div id="auth-login-card" className="auth-card auth-card--login">
            <button type="button" className="auth-close" aria-label="Close form" onClick={hide}>
              ×
            </button>
            <p className="auth-brand">Welcome back</p>
            <h1 aria-label="Sign in" className="auth-title">
              {Array.from('Sign in').map((letter, index) => (
                <span
                  key={`${String(index)}-${letter}`}
                  className="auth-title-letter"
                  aria-hidden="true"
                  style={{ '--i': index } as React.CSSProperties}
                >
                  {letter === ' ' ? '\u00a0' : letter}
                </span>
              ))}
            </h1>
            <span className="auth-title-line" aria-hidden="true" />
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
                  ref={emailRef}
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
                <div className="password-wrap">
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    required
                    aria-invalid={passwordError !== null}
                    aria-describedby={describedBy('password', false, passwordError !== null)}
                    onChange={(event) => {
                      setPassword(event.target.value);
                    }}
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    aria-label={showPassword ? 'Hide characters' : 'Show characters'}
                    aria-pressed={showPassword}
                    onClick={() => {
                      setShowPassword((current) => !current);
                    }}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </Field>

              <button type="submit" className="button button-primary button-block" disabled={busy}>
                {busy ? 'Signing in…' : 'Sign in'}
              </button>
            </form>
            <p className="auth-footnote">Need an account? Ask your HR team.</p>
          </div>
        </div>
      </section>
    </main>
  );
}
