import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { ApiError, errorMessage } from '../api/ApiError';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { describedBy, Field } from '../components/Field';
import { useToast } from '../components/useToast';
import { validateNewPassword } from '../lib/validation';

/**
 * Doubles as the forced screen for a freshly created account and as the voluntary change
 * from the profile page. While `mustChangePassword` is set, the route guard sends every
 * other path here.
 */
export function ChangePasswordPage(): JSX.Element {
  const { mustChangePassword, applyUser, logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const next: Record<string, string | null> = {
      currentPassword: currentPassword.length === 0 ? 'Your current password is required.' : null,
      newPassword: validateNewPassword(newPassword),
      confirmation: newPassword !== confirmation ? 'The two passwords do not match.' : null,
    };
    setErrors(next);
    if (Object.values(next).some((value) => value !== null)) {
      return;
    }

    setBusy(true);
    setFormError(null);
    try {
      const user = await api.changePassword({ currentPassword, newPassword });
      applyUser(user);
      toast.success('Your password was changed.');
      navigate('/', { replace: true });
    } catch (cause) {
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setErrors({
          currentPassword: cause.fieldErrors.currentPassword ?? null,
          newPassword: cause.fieldErrors.newPassword ?? null,
        });
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
        <h1>{mustChangePassword ? 'Change your password to continue' : 'Change password'}</h1>
        {mustChangePassword && (
          <Banner tone="warning">
            This account still uses the temporary password issued by HR. Choose a new one before
            using the system.
          </Banner>
        )}
        {formError !== null && <Banner tone="error">{formError}</Banner>}

        <form onSubmit={onSubmit} noValidate>
          <Field
            id="currentPassword"
            label="Current password"
            error={errors.currentPassword}
            required
          >
            <input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              required
              aria-invalid={errors.currentPassword != null}
              aria-describedby={describedBy('currentPassword', false, errors.currentPassword != null)}
              onChange={(event) => {
                setCurrentPassword(event.target.value);
              }}
            />
          </Field>

          <Field
            id="newPassword"
            label="New password"
            error={errors.newPassword}
            hint="At least 8 characters, at most 72."
            required
          >
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              required
              minLength={8}
              maxLength={72}
              aria-invalid={errors.newPassword != null}
              aria-describedby={describedBy('newPassword', true, errors.newPassword != null)}
              onChange={(event) => {
                setNewPassword(event.target.value);
              }}
            />
          </Field>

          <Field id="confirmation" label="Repeat new password" error={errors.confirmation} required>
            <input
              id="confirmation"
              type="password"
              autoComplete="new-password"
              value={confirmation}
              required
              aria-invalid={errors.confirmation != null}
              aria-describedby={describedBy('confirmation', false, errors.confirmation != null)}
              onChange={(event) => {
                setConfirmation(event.target.value);
              }}
            />
          </Field>

          <button type="submit" className="button button-primary button-block" disabled={busy}>
            {busy ? 'Saving…' : 'Change password'}
          </button>
        </form>

        <button type="button" className="button-link" onClick={logout}>
          Sign out instead
        </button>
      </div>
    </main>
  );
}
