import { useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/ApiError';
import type {
  AttendanceSummaryRow,
  HeadcountRow,
  LeaveSummaryRow,
  PayrollSummaryRow,
} from '../api/types';
import { Banner } from '../components/Banner';
import { BarChart, type BarDatum } from '../components/BarChart';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { currentMonth, currentYear } from '../lib/dates';
import { saveBlob } from '../lib/download';
import { formatMinutes, formatMoney, humanizeEnum, monthName } from '../lib/format';
import { useResource } from '../lib/useResource';

type ReportKey = 'headcount' | 'leave' | 'payroll' | 'attendance';

const TABS: { key: ReportKey; label: string }[] = [
  { key: 'headcount', label: 'Headcount' },
  { key: 'leave', label: 'Leave summary' },
  { key: 'payroll', label: 'Payroll summary' },
  { key: 'attendance', label: 'Attendance summary' },
];

function HeadcountReport(): JSX.Element {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const report = useResource<HeadcountRow[]>(
    (signal) => api.headcountReport(signal),
    'report-headcount',
  );

  async function download(): Promise<void> {
    setError(null);
    try {
      saveBlob(await api.headcountXlsx(), 'headcount.xlsx');
      toast.success('Headcount exported.');
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  const data: BarDatum[] = (report.data ?? []).map((row) => ({
    label: `${row.department} · ${humanizeEnum(row.status)}`,
    value: row.count,
  }));

  return (
    <Card
      title="Headcount by department and status"
      actions={
        <button
          type="button"
          className="button button-small"
          onClick={() => {
            void download();
          }}
        >
          Download XLSX
        </button>
      }
    >
      {report.loading && <Loading label="Loading the headcount…" />}
      {report.error !== null && <Banner>{report.error}</Banner>}
      {error !== null && <Banner>{error}</Banner>}
      {report.data !== null &&
        (report.data.length === 0 ? (
          <EmptyState title="No employee to count." />
        ) : (
          <BarChart title="Employees per department and status" data={data} unit="Employees" />
        ))}
    </Card>
  );
}

function LeaveReport({ year }: { year: number }): JSX.Element {
  const report = useResource<LeaveSummaryRow[]>(
    (signal) => api.leaveSummaryReport(year, signal),
    `report-leave-${String(year)}`,
  );

  const data: BarDatum[] = (report.data ?? []).map((row) => ({
    label: `${row.department} · ${row.leaveType}`,
    value: row.usedDays,
  }));

  return (
    <Card title={`Leave used in ${String(year)}`}>
      {report.loading && <Loading label="Loading the leave summary…" />}
      {report.error !== null && <Banner>{report.error}</Banner>}
      {report.data !== null &&
        (report.data.length === 0 ? (
          <EmptyState title="No leave recorded for this year." />
        ) : (
          <>
            <BarChart title="Used leave days per department and type" data={data} unit="Used days" />
            <table className="table spaced">
              <caption>Leave summary {year}</caption>
              <thead>
                <tr>
                  <th scope="col">Department</th>
                  <th scope="col">Type</th>
                  <th scope="col">Used</th>
                  <th scope="col">Pending</th>
                  <th scope="col">Remaining</th>
                </tr>
              </thead>
              <tbody>
                {report.data.map((row) => (
                  <tr key={`${row.department}-${row.leaveType}`}>
                    <th scope="row">{row.department}</th>
                    <td>{row.leaveType}</td>
                    <td>{row.usedDays}</td>
                    <td>{row.pendingDays}</td>
                    <td>{row.remainingDays}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ))}
    </Card>
  );
}

function PayrollReport({ year }: { year: number }): JSX.Element {
  const report = useResource<PayrollSummaryRow[]>(
    (signal) => api.payrollSummaryReport(year, signal),
    `report-payroll-${String(year)}`,
  );

  const data: BarDatum[] = (report.data ?? []).map((row) => ({
    label: monthName(row.month),
    value: row.netPay,
    display: formatMoney(row.netPay),
  }));

  return (
    <Card title={`Payroll in ${String(year)} (finalized runs only)`}>
      {report.loading && <Loading label="Loading the payroll summary…" />}
      {report.error !== null && <Banner>{report.error}</Banner>}
      {report.data !== null &&
        (report.data.length === 0 ? (
          <EmptyState title="No finalized payroll run in this year." />
        ) : (
          <>
            <BarChart title="Net pay per month" data={data} unit="Net pay" />
            <table className="table spaced">
              <caption>Monthly payroll totals {year}</caption>
              <thead>
                <tr>
                  <th scope="col">Month</th>
                  <th scope="col">Payslips</th>
                  <th scope="col">Gross</th>
                  <th scope="col">Insurance</th>
                  <th scope="col">Tax</th>
                  <th scope="col">Net</th>
                </tr>
              </thead>
              <tbody>
                {report.data.map((row) => (
                  <tr key={row.month}>
                    <th scope="row">{monthName(row.month)}</th>
                    <td>{row.payslipCount}</td>
                    <td className="numeric">{formatMoney(row.grossEarnings)}</td>
                    <td className="numeric">{formatMoney(row.insurance)}</td>
                    <td className="numeric">{formatMoney(row.tax)}</td>
                    <td className="numeric">{formatMoney(row.netPay)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ))}
    </Card>
  );
}

function AttendanceReport({ year, month }: { year: number; month: number }): JSX.Element {
  const report = useResource<AttendanceSummaryRow[]>(
    (signal) => api.attendanceSummaryReport(year, month, signal),
    `report-attendance-${String(year)}-${String(month)}`,
  );

  const data: BarDatum[] = (report.data ?? []).map((row) => ({
    label: row.department,
    value: row.absentDays,
  }));

  return (
    <Card title={`Attendance in ${monthName(month)} ${String(year)}`}>
      {report.loading && <Loading label="Loading the attendance summary…" />}
      {report.error !== null && <Banner>{report.error}</Banner>}
      {report.data !== null &&
        (report.data.length === 0 ? (
          <EmptyState title="No attendance recorded for this month." />
        ) : (
          <>
            <BarChart title="Absent days per department" data={data} unit="Absent days" />
            <table className="table spaced">
              <caption>
                Attendance summary {monthName(month)} {year}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Department</th>
                  <th scope="col">Late days</th>
                  <th scope="col">Absent days</th>
                  <th scope="col">Overtime</th>
                </tr>
              </thead>
              <tbody>
                {report.data.map((row) => (
                  <tr key={row.department}>
                    <th scope="row">{row.department}</th>
                    <td>{row.lateDays}</td>
                    <td>{row.absentDays}</td>
                    <td>{formatMinutes(row.overtimeMinutes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ))}
    </Card>
  );
}

export function ReportsPage(): JSX.Element {
  const [tab, setTab] = useState<ReportKey>('headcount');
  const [year, setYear] = useState(currentYear());
  const [month, setMonth] = useState(currentMonth());

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Headcount, leave, payroll and attendance, straight from the reporting endpoints."
      />

      <div className="tabs" role="tablist" aria-label="Reports">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={tab === item.key}
            className={tab === item.key ? 'tab tab-active' : 'tab'}
            onClick={() => {
              setTab(item.key);
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab !== 'headcount' && (
        <Card title="Period">
          <div className="filter-row">
            <label className="inline-field" htmlFor="report-year">
              <span>Year</span>
              <input
                id="report-year"
                type="number"
                min={2000}
                max={2100}
                value={year}
                onChange={(event) => {
                  const next = Number(event.target.value);
                  if (Number.isInteger(next)) {
                    setYear(next);
                  }
                }}
              />
            </label>
            {tab === 'attendance' && (
              <label className="inline-field" htmlFor="report-month">
                <span>Month</span>
                <input
                  id="report-month"
                  type="number"
                  min={1}
                  max={12}
                  value={month}
                  onChange={(event) => {
                    const next = Number(event.target.value);
                    if (Number.isInteger(next) && next >= 1 && next <= 12) {
                      setMonth(next);
                    }
                  }}
                />
              </label>
            )}
          </div>
        </Card>
      )}

      {tab === 'headcount' && <HeadcountReport />}
      {tab === 'leave' && <LeaveReport year={year} />}
      {tab === 'payroll' && <PayrollReport year={year} />}
      {tab === 'attendance' && <AttendanceReport year={year} month={month} />}
    </>
  );
}
