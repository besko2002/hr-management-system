/** Calendar maths on plain `YYYY-MM-DD` strings, so no time zone ever shifts a day. */

export interface MonthRange {
  from: string;
  to: string;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function toIsoDate(date: Date): string {
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function today(): string {
  return toIsoDate(new Date());
}

export function currentYear(): number {
  return new Date().getFullYear();
}

export function currentMonth(): number {
  return new Date().getMonth() + 1;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** The inclusive first and last day of a month: 92 days at most, so the API accepts it. */
export function monthRange(year: number, month: number): MonthRange {
  return {
    from: `${String(year)}-${pad(month)}-01`,
    to: `${String(year)}-${pad(month)}-${pad(daysInMonth(year, month))}`,
  };
}

/** `2025-03` ← (2025, 3). */
export function monthInputValue(year: number, month: number): string {
  return `${String(year)}-${pad(month)}`;
}

/** Parses a `<input type="month">` value; null when it is not a valid month. */
export function parseMonthInput(value: string): { year: number; month: number } | null {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (match === null) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (month < 1 || month > 12) {
    return null;
  }
  return { year, month };
}

/** Inclusive day count, or null when either side is not a date. */
export function inclusiveDays(from: string, to: string): number | null {
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return null;
  }
  return Math.round((end - start) / 86_400_000) + 1;
}

/** Every `YYYY-MM-DD` between the two bounds, inclusive. */
export function eachDay(from: string, to: string): string[] {
  const days: string[] = [];
  const cursor = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  while (cursor.getTime() <= end.getTime()) {
    days.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return days;
}
