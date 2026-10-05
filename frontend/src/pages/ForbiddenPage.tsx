import { Link } from 'react-router-dom';
import { FORBIDDEN_BODY, FORBIDDEN_TITLE } from '../lib/messages';
import { PageHeader } from '../components/states';

export function ForbiddenPage(): JSX.Element {
  return (
    <>
      <PageHeader title={FORBIDDEN_TITLE} />
      <section className="card">
        <div className="card-body">
          <p>{FORBIDDEN_BODY}</p>
          <p className="spaced">
            <Link to="/">Back to the dashboard</Link>
          </p>
        </div>
      </section>
    </>
  );
}

export function NotFoundPage(): JSX.Element {
  return (
    <>
      <PageHeader title="Page not found" />
      <section className="card">
        <div className="card-body">
          <p>That page does not exist in this application.</p>
          <p className="spaced">
            <Link to="/">Back to the dashboard</Link>
          </p>
        </div>
      </section>
    </>
  );
}
