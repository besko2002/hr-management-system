import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/api';
import { errorMessage } from '../api/ApiError';
import type { PayrollRunDetailResponse, PayslipResponse } from '../api/types';
import { Banner } from '../components/Banner';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Card, EmptyState, Loading, PageHeader } from '../components/states';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/useToast';
import { saveBlob } from '../lib/download';
import { formatMoney, formatPeriod } from '../lib/format';
import { useResource } from '../lib/useResource';

type Pending = 'recalculate' | 'finalize' | 'delete' | null;

export function PayrollRunPage(): JSX.Element {
  const { runId = '' } = useParams<{ runId: string }>();
  const toast = useToast();
  const navigate = useNavigate();

  const detail = useResource<PayrollRunDetailResponse>(
    (signal) => api.getPayrollRun(runId, signal),
    `payroll-run-${runId}`,
  );

  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState<Pending>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const data = detail.data;
  const run = data?.run ?? null;
  const isDraft = run?.status === 'DRAFT';

  async function recalculate(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.recalculatePayrollRun(runId);
      detail.setData(updated);
      toast.success('Payroll run recalculated.');
    } catch (cause) {
      // 409 once the run is FINALIZED.
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
      setConfirming(null);
    }
  }

  async function finalize(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await api.finalizePayrollRun(runId);
      toast.success('Payroll run finalized.');
      detail.reload();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
      setConfirming(null);
    }
  }

  async function remove(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await api.deletePayrollRun(runId);
      toast.success('Draft payroll run deleted.');
      navigate('/payroll', { replace: true });
    } catch (cause) {
      setError(errorMessage(cause));
      setBusy(false);
      setConfirming(null);
    }
  }

  async function downloadXlsx(): Promise<void> {
    setError(null);
    try {
      const blob = await api.payrollRunXlsx(runId);
      saveBlob(blob, `payroll-${runId}.xlsx`);
      toast.success('Register downloaded.');
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  async function downloadPdf(payslip: PayslipResponse): Promise<void> {
    setDownloadingId(payslip.id);
    setError(null);
    try {
      const blob = await api.payslipPdf(payslip.id);
      saveBlob(
        blob,
        `payslip-${payslip.employeeNumber}-${String(payslip.year)}-${String(payslip.month).padStart(2, '0')}.pdf`,
      );
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <>
      <PageHeader
        title={run === null ? 'Payroll run' : formatPeriod(run.year, run.month)}
        subtitle={run === null ? undefined : `Status: ${run.status}`}
        actions={<Link to="/payroll">Back to payroll</Link>}
      />

      {detail.loading && <Loading label="Loading the run…" />}
      {detail.error !== null && <Banner>{detail.error}</Banner>}
      {error !== null && (
        <Banner
          onDismiss={() => {
            setError(null);
          }}
        >
          {error}
        </Banner>
      )}

      {data !== null && run !== null && (
        <>
          <Card
            title="Totals"
            actions={
              <div className="button-row">
                {isDraft && (
                  <button
                    type="button"
                    className="button button-small"
                    disabled={busy}
                    onClick={() => {
                      setConfirming('recalculate');
                    }}
                  >
                    Recalculate
                  </button>
                )}
                {isDraft && (
                  <button
                    type="button"
                    className="button button-primary button-small"
                    disabled={busy}
                    onClick={() => {
                      setConfirming('finalize');
                    }}
                  >
                    Finalize
                  </button>
                )}
                {isDraft && (
                  <button
                    type="button"
                    className="button button-danger button-small"
                    disabled={busy}
                    onClick={() => {
                      setConfirming('delete');
                    }}
                  >
                    Delete draft
                  </button>
                )}
                <button
                  type="button"
                  className="button button-small"
                  onClick={() => {
                    void downloadXlsx();
                  }}
                >
                  Download XLSX
                </button>
              </div>
            }
          >
            <p>
              <StatusChip status={run.status} /> · {run.payslipCount} payslip
              {run.payslipCount === 1 ? '' : 's'}
            </p>
            <dl className="stat-row spaced">
              <div className="stat">
                <dt>Employees</dt>
                <dd>{data.totals.employees}</dd>
              </div>
              <div className="stat">
                <dt>Gross earnings</dt>
                <dd className="numeric">{formatMoney(data.totals.grossEarnings)}</dd>
              </div>
              <div className="stat">
                <dt>Insurance</dt>
                <dd className="numeric">{formatMoney(data.totals.insurance)}</dd>
              </div>
              <div className="stat">
                <dt>Tax</dt>
                <dd className="numeric">{formatMoney(data.totals.tax)}</dd>
              </div>
              <div className="stat">
                <dt>Net pay</dt>
                <dd className="numeric">{formatMoney(data.totals.netPay)}</dd>
              </div>
            </dl>
          </Card>

          <Card title="Payslips">
            {data.payslips.length === 0 ? (
              <EmptyState title="This run has no payslip." />
            ) : (
              <table className="table">
                <caption className="sr-only">Payslips in this run</caption>
                <thead>
                  <tr>
                    <th scope="col">Employee</th>
                    <th scope="col">Department</th>
                    <th scope="col">Base</th>
                    <th scope="col">Gross</th>
                    <th scope="col">Insurance</th>
                    <th scope="col">Tax</th>
                    <th scope="col">Net</th>
                    <th scope="col">
                      <span className="sr-only">PDF</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.payslips.map((payslip) => (
                    <tr key={payslip.id}>
                      <th scope="row">
                        {payslip.employeeName}
                        <span className="muted"> {payslip.employeeNumber}</span>
                      </th>
                      <td>{payslip.departmentName ?? 'Unassigned'}</td>
                      <td className="numeric">{formatMoney(payslip.baseSalary)}</td>
                      <td className="numeric">{formatMoney(payslip.grossEarnings)}</td>
                      <td className="numeric">{formatMoney(payslip.insurance)}</td>
                      <td className="numeric">{formatMoney(payslip.tax)}</td>
                      <td className="numeric">{formatMoney(payslip.netPay)}</td>
                      <td>
                        <button
                          type="button"
                          className="button button-small"
                          disabled={downloadingId === payslip.id}
                          onClick={() => {
                            void downloadPdf(payslip);
                          }}
                          aria-label={`Download the payslip of ${payslip.employeeName} as PDF`}
                        >
                          {downloadingId === payslip.id ? 'Preparing…' : 'PDF'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </>
      )}

      {confirming === 'recalculate' && (
        <ConfirmDialog
          title="Recalculate this run?"
          message="Every payslip in the run is replaced with a freshly computed one. Only a DRAFT run can be recalculated."
          confirmLabel="Recalculate"
          tone="primary"
          busy={busy}
          onConfirm={() => {
            void recalculate();
          }}
          onCancel={() => {
            setConfirming(null);
          }}
        />
      )}

      {confirming === 'finalize' && (
        <ConfirmDialog
          title="Finalize this run?"
          message="Finalizing locks the run forever: it can no longer be recalculated or deleted, and the payslips become visible to the employees."
          confirmLabel="Finalize"
          tone="primary"
          busy={busy}
          onConfirm={() => {
            void finalize();
          }}
          onCancel={() => {
            setConfirming(null);
          }}
        />
      )}

      {confirming === 'delete' && (
        <ConfirmDialog
          title="Delete this draft run?"
          message="The draft and its payslips are removed. A finalized run cannot be deleted."
          confirmLabel="Delete draft"
          busy={busy}
          onConfirm={() => {
            void remove();
          }}
          onCancel={() => {
            setConfirming(null);
          }}
        />
      )}
    </>
  );
}
