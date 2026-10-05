import { humanizeEnum } from '../lib/format';

const TONES: Record<string, string> = {
  PENDING: 'warn',
  APPROVED: 'ok',
  REJECTED: 'bad',
  CANCELLED: 'muted',
  ACTIVE: 'ok',
  TERMINATED: 'bad',
  DRAFT: 'warn',
  FINALIZED: 'ok',
  PRESENT: 'ok',
  LATE: 'warn',
  ABSENT: 'bad',
  ON_LEAVE: 'info',
  HOLIDAY: 'info',
  WEEKEND: 'muted',
  MISSING_CHECKOUT: 'warn',
};

/** A status badge whose meaning is also in its text, never colour alone. */
export function StatusChip({ status }: { status: string }): JSX.Element {
  const tone = TONES[status] ?? 'muted';
  return <span className={`chip chip-${tone}`}>{humanizeEnum(status)}</span>;
}
