import { useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/ApiError';
import type { PayslipResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { useToast } from '../components/useToast';
import { saveBlob } from '../lib/download';
import { formatMoney, formatPeriod } from '../lib/format';
import { useResource } from '../lib/useResource';

/** Only FINALIZED runs produce a payslip the employee can see — the API enforces it. */
export function PayslipsPage(): JSX.Element {
  const toast = useToast();
  const payslips = useResource<PayslipResponse[]>((signal) => api.myPayslips(signal), 'my-payslips');
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function download(payslip: PayslipResponse): Promise<void> {
    setBusyId(payslip.id);
    setError(null);
    try {
      const blob = await api.payslipPdf(payslip.id);
      saveBlob(
        blob,
        `payslip-${payslip.employeeNumber}-${String(payslip.year)}-${String(payslip.month).padStart(2, '0')}.pdf`,
      );
      toast.success('Payslip downloaded.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeader title="My payslips" subtitle="Finalized payroll runs only." />

      <Card>
        {payslips.loading && <Loading label="Loading payslips…" />}
        {payslips.error !== null && <Banner>{payslips.error}</Banner>}
        {error !== null && (
          <Banner
            onDismiss={() => {
              setError(null);
            }}
          >
            {error}
          </Banner>
        )}

        {payslips.data !== null && payslips.data.length === 0 && (
          <EmptyState
            title="No payslips yet."
            hint="A payslip appears here once HR finalizes the payroll run for that month."
          />
        )}

        {payslips.data !== null && payslips.data.length > 0 && (
          <table className="table">
            <caption className="sr-only">My finalized payslips</caption>
            <thead>
              <tr>
                <th scope="col">Period</th>
                <th scope="col">Gross</th>
                <th scope="col">Insurance</th>
                <th scope="col">Tax</th>
                <th scope="col">Net pay</th>
                <th scope="col">
                  <span className="sr-only">Download</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {payslips.data.map((payslip) => (
                <tr key={payslip.id}>
                  <th scope="row">{formatPeriod(payslip.year, payslip.month)}</th>
                  <td className="numeric">{formatMoney(payslip.grossEarnings)}</td>
                  <td className="numeric">{formatMoney(payslip.insurance)}</td>
                  <td className="numeric">{formatMoney(payslip.tax)}</td>
                  <td className="numeric">{formatMoney(payslip.netPay)}</td>
                  <td>
                    <button
                      type="button"
                      className="button button-small"
                      disabled={busyId === payslip.id}
                      onClick={() => {
                        void download(payslip);
                      }}
                      aria-label={`Download the ${formatPeriod(payslip.year, payslip.month)} payslip as PDF`}
                    >
                      {busyId === payslip.id ? 'Preparing…' : 'PDF'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}
