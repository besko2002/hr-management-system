interface PaginationProps {
  /** 0-based, exactly as the API reports it. */
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
  busy?: boolean;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
  busy = false,
}: PaginationProps): JSX.Element {
  const human = totalPages === 0 ? 1 : page + 1;
  const pages = Math.max(totalPages, 1);

  return (
    <nav className="pagination" aria-label="Pagination">
      <button
        type="button"
        className="button"
        disabled={busy || page <= 0}
        onClick={() => {
          onChange(page - 1);
        }}
      >
        Previous
      </button>
      <span className="pagination-status" aria-live="polite">
        Page {human} of {pages} · {totalElements} total
      </span>
      <button
        type="button"
        className="button"
        disabled={busy || page >= totalPages - 1}
        onClick={() => {
          onChange(page + 1);
        }}
      >
        Next
      </button>
    </nav>
  );
}
