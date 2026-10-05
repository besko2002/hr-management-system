import { ApprovalsPage } from './ApprovalsPage';

/**
 * HR/ADMIN view of the same pending-requests endpoint, which returns every pending
 * request in the company for those roles rather than just one manager's reports.
 */
export function LeaveOverviewPage(): JSX.Element {
  return (
    <ApprovalsPage
      title="Leave overview"
      subtitle="Every pending leave request in the company. HR can decide any of them."
    />
  );
}
