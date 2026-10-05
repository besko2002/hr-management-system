/** Status used when the request never reached the server. */
export const NETWORK_ERROR_STATUS = 0;

export const NETWORK_ERROR_MESSAGE =
  'Could not reach the server. Check your connection and try again.';

/**
 * Error thrown by every api call. `status` is the HTTP status (0 when the request failed
 * before reaching the server) and `fieldErrors` mirrors the backend `ApiError.fieldErrors`
 * map, which the API only sends for 400 validation failures.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  readonly body: unknown;

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> = {},
    body: unknown = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.body = body;
  }

  get isNetworkError(): boolean {
    return this.status === NETWORK_ERROR_STATUS;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readFieldErrors(value: unknown): Record<string, string> {
  if (!isRecord(value)) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const [key, message] of Object.entries(value)) {
    if (typeof message === 'string') {
      result[key] = message;
    }
  }
  return result;
}

function defaultMessage(status: number, statusText: string): string {
  if (statusText.trim().length > 0) {
    return `Request failed (${String(status)} ${statusText}).`;
  }
  return `Request failed (${String(status)}).`;
}

/** Builds an {@link ApiError} from a parsed (possibly malformed) backend error body. */
export function toApiError(status: number, statusText: string, body: unknown): ApiError {
  const record = isRecord(body) ? body : null;
  const rawMessage = record?.message;
  const message =
    typeof rawMessage === 'string' && rawMessage.trim().length > 0
      ? rawMessage
      : defaultMessage(status, statusText);
  return new ApiError(status, message, readFieldErrors(record?.fieldErrors), body);
}

/** The message to show for any thrown value, so no screen renders "[object Object]". */
export function errorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return 'Something went wrong. Please try again.';
}
