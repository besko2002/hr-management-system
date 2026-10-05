import { render, type RenderResult } from '@testing-library/react';
import { StrictMode, type ReactElement, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi, type Mock } from 'vitest';
import { App } from '../App';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/ToastProvider';

/** A canned HTTP reply for the fetch mock. */
export interface MockReply {
  status?: number;
  statusText?: string;
  /** Serialised as the JSON response body; omit for an empty body. */
  body?: unknown;
  /** Returned by `response.blob()`, for the download paths. */
  blob?: Blob;
}

export interface RecordedCall {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: unknown;
}

export type RouteHandler = MockReply | ((call: RecordedCall) => MockReply);

export interface FetchMock {
  spy: Mock<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>;
  calls: RecordedCall[];
  callsTo: (key: string) => RecordedCall[];
  /** Every recorded url whose path part matches, ignoring the query string. */
  urlsFor: (method: string, path: string) => string[];
}

const STATUS_TEXT: Record<number, string> = {
  200: 'OK',
  201: 'Created',
  204: 'No Content',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  500: 'Internal Server Error',
};

export function makeResponse(reply: MockReply): Response {
  const status = reply.status ?? 200;
  const text = reply.body === undefined ? '' : JSON.stringify(reply.body);
  const response = {
    ok: status >= 200 && status < 300,
    status,
    statusText: reply.statusText ?? STATUS_TEXT[status] ?? '',
    text: () => Promise.resolve(text),
    blob: () => Promise.resolve(reply.blob ?? new Blob([text])),
  };
  return response as unknown as Response;
}

function parseBody(body: BodyInit | null | undefined): unknown {
  if (typeof body !== 'string') {
    return null;
  }
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

/**
 * Replaces global fetch with a table-driven mock. Keys look like
 * `"GET /api/employees/me"`, optionally with a query string; a request with no handler
 * fails the test loudly instead of silently resolving.
 */
export function installFetchMock(table: Record<string, RouteHandler>): FetchMock {
  const calls: RecordedCall[] = [];

  const spy = vi.fn((input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url =
      typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const call: RecordedCall = { method, url, headers, body: parseBody(init?.body) };
    calls.push(call);

    const exact = table[`${method} ${url}`];
    // Fall back to a handler registered without the query string.
    const pathOnly = table[`${method} ${url.split('?')[0]}`];
    const handler = exact ?? pathOnly;
    if (handler === undefined) {
      return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
    }
    return Promise.resolve(makeResponse(typeof handler === 'function' ? handler(call) : handler));
  });

  vi.stubGlobal('fetch', spy);

  return {
    spy,
    calls,
    callsTo: (key: string) => calls.filter((call) => `${call.method} ${call.url}` === key),
    urlsFor: (method: string, path: string) =>
      calls
        .filter((call) => call.method === method.toUpperCase() && call.url.split('?')[0] === path)
        .map((call) => call.url),
  };
}

function Providers({ children }: { children: ReactNode }): JSX.Element {
  return (
    <AuthProvider>
      <ToastProvider>{children}</ToastProvider>
    </AuthProvider>
  );
}

/** Renders the whole routed app at `path`, wrapped in the real providers. */
export function renderApp(path: string): RenderResult {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Providers>
        <App />
      </Providers>
    </MemoryRouter>,
  );
}

/** Same as {@link renderApp}, but under StrictMode so effects run twice. */
export function renderAppStrict(path: string): RenderResult {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <Providers>
          <App />
        </Providers>
      </MemoryRouter>
    </StrictMode>,
  );
}

/** Renders one element inside a router and the providers. */
export function renderAt(path: string, element: ReactElement): RenderResult {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Providers>{element}</Providers>
    </MemoryRouter>,
  );
}
