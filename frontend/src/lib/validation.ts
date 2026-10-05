/**
 * Client-side validation that mirrors the backend bean-validation constraints, so the
 * common mistakes are caught before a round trip. The server stays the authority: any
 * 400 it returns is still rendered from `ApiError.fieldErrors`.
 */

import { inclusiveDays } from './dates';

export type FieldErrors = Record<string, string>;

/** `@NotBlank @Email @Size(max = 255)` on every email field. */
export function validateEmail(value: string): string | null {
  const email = value.trim();
  if (email.length === 0) {
    return 'Email is required.';
  }
  if (email.length > 255) {
    return 'Email must be at most 255 characters.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return 'Enter a valid email address.';
  }
  return null;
}

/** `@NotBlank @Size(min = 8, max = 72)` on `ChangePasswordRequest.newPassword`. */
export function validateNewPassword(value: string): string | null {
  if (value.length === 0) {
    return 'A new password is required.';
  }
  if (value.length < 8) {
    return 'The new password must be at least 8 characters.';
  }
  if (value.length > 72) {
    return 'The new password must be at most 72 characters.';
  }
  return null;
}

/** `@NotBlank @Size(max = 150)` on `fullName`. */
export function validateFullName(value: string): string | null {
  const name = value.trim();
  if (name.length === 0) {
    return 'Full name is required.';
  }
  if (name.length > 150) {
    return 'Full name must be at most 150 characters.';
  }
  return null;
}

/** `@Size(max = 120)` on `jobTitle`; optional. */
export function validateJobTitle(value: string): string | null {
  if (value.trim().length > 120) {
    return 'Job title must be at most 120 characters.';
  }
  return null;
}

/** `@Size(max = 120)` and `@NotBlank` on a department or holiday name. */
export function validateName(value: string, label: string, max = 120): string | null {
  const name = value.trim();
  if (name.length === 0) {
    return `${label} is required.`;
  }
  if (name.length > max) {
    return `${label} must be at most ${String(max)} characters.`;
  }
  return null;
}

/** `@DecimalMin("0.00") @Digits(integer = 10, fraction = 2)`; blank means "not set". */
export function validateSalary(value: string): string | null {
  const text = value.trim();
  if (text.length === 0) {
    return null;
  }
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(text)) {
    return 'Salary must be a positive amount with at most 2 decimals.';
  }
  return null;
}

export function validateIsoDate(value: string, label: string): string | null {
  if (value.trim().length === 0) {
    return `${label} is required.`;
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return `${label} must be a date.`;
  }
  if (Number.isNaN(Date.parse(`${value}T00:00:00Z`))) {
    return `${label} must be a date.`;
  }
  return null;
}

/** `@Size(max = 500)` on `reason` and `decisionNote`. */
export function validateNote(value: string, label: string): string | null {
  if (value.length > 500) {
    return `${label} must be at most 500 characters.`;
  }
  return null;
}

export interface LeaveRequestFormValues {
  type: string;
  startDate: string;
  endDate: string;
  reason: string;
}

/**
 * The whole leave form. The backend also rejects ranges with zero working days, overlaps
 * and insufficient balance — those answers (400/409) are rendered as they arrive.
 */
export function validateLeaveRequest(values: LeaveRequestFormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (values.type.trim().length === 0) {
    errors.type = 'Pick a leave type.';
  }
  const startError = validateIsoDate(values.startDate, 'Start date');
  if (startError !== null) {
    errors.startDate = startError;
  }
  const endError = validateIsoDate(values.endDate, 'End date');
  if (endError !== null) {
    errors.endDate = endError;
  }
  if (startError === null && endError === null && inclusiveDays(values.startDate, values.endDate) === null) {
    errors.endDate = 'The end date cannot be before the start date.';
  }
  const reasonError = validateNote(values.reason, 'Reason');
  if (reasonError !== null) {
    errors.reason = reasonError;
  }
  return errors;
}

export function hasErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
