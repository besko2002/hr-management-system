/** Formatting helpers. Every one of them tolerates null so no screen prints "null". */

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
] as const;

/** `2025-03-04` → `4 Mar 2025`. Returns an em dash for null. */
export function formatDate(value: string | null | undefined): string {
  if (value === null || value === undefined || value.length < 10) {
    return '—';
  }
  const [year, month, day] = value.slice(0, 10).split('-');
  const index = Number(month) - 1;
  const name = MONTH_NAMES[index];
  if (name === undefined) {
    return value;
  }
  return `${String(Number(day))} ${name.slice(0, 3)} ${year}`;
}

/** An ISO instant or local date-time → `4 Mar 2025, 09:05`. */
export function formatDateTime(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  const parsed = new Date(value.endsWith('Z') || value.includes('+') ? value : `${value}Z`);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  const time = parsed.toISOString().slice(11, 16);
  return `${formatDate(parsed.toISOString().slice(0, 10))}, ${time}`;
}

/** Just the clock part of a `LocalDateTime` such as `2025-03-04T09:05:00`. */
export function formatTime(value: string | null | undefined): string {
  if (value === null || value === undefined || value.length < 16) {
    return '—';
  }
  return value.slice(11, 16);
}

/**
 * Money, always with two decimals and thousands separators. Never called with a missing
 * salary: the salary-visibility rule is decided before formatting, not here.
 */
export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** 95 → `1h 35m`, 0 → `0m`. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) {
    return '—';
  }
  const whole = Math.trunc(Math.abs(minutes));
  const hours = Math.trunc(whole / 60);
  const rest = whole % 60;
  const sign = minutes < 0 ? '-' : '';
  if (hours === 0) {
    return `${sign}${String(rest)}m`;
  }
  return `${sign}${String(hours)}h ${String(rest)}m`;
}

export function monthName(month: number): string {
  return MONTH_NAMES[month - 1] ?? String(month);
}

/** `2025-03` → `March 2025`. */
export function formatPeriod(year: number, month: number): string {
  return `${monthName(month)} ${String(year)}`;
}

/** `ON_LEAVE` → `On leave`. */
export function humanizeEnum(value: string): string {
  const words = value.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export { MONTH_NAMES };
