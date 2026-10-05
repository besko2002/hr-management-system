import { describe, expect, it, vi } from 'vitest';
import { api, requestBlob, setUnauthorizedHandler } from './api';
import { ApiError } from './ApiError';
import { writeToken } from './token';
import { installFetchMock } from '../test/helpers';

describe('api client', () => {
  it('attaches the bearer token to an authenticated call', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({ 'GET /api/employees/me': { body: { id: 'emp-1' } } });

    await api.me();

    const call = mock.callsTo('GET /api/employees/me')[0];
    expect(call.headers.Authorization).toBe('Bearer jwt-123');
    expect(call.headers.Accept).toBe('application/json');
  });

  it('sends no Authorization header on login', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({
      'POST /api/auth/login': { body: { accessToken: 'new', tokenType: 'Bearer' } },
    });

    await api.login({ email: 'admin@hr.local', password: 'Admin@12345' });

    const call = mock.callsTo('POST /api/auth/login')[0];
    expect(call.headers.Authorization).toBeUndefined();
    expect(call.body).toEqual({ email: 'admin@hr.local', password: 'Admin@12345' });
    expect(call.headers['Content-Type']).toBe('application/json');
  });

  it('turns an ApiError body into the thrown message', async () => {
    installFetchMock({
      'POST /api/attendance/check-in': {
        status: 409,
        body: {
          timestamp: '2025-03-04T09:00:00Z',
          status: 409,
          error: 'Conflict',
          message: 'You already have an open attendance session today; check out first',
          path: '/api/attendance/check-in',
        },
      },
    });

    await expect(api.checkIn()).rejects.toThrowError(
      'You already have an open attendance session today; check out first',
    );
  });

  it('parses the fieldErrors map of a 400', async () => {
    installFetchMock({
      'POST /api/employees': {
        status: 400,
        body: {
          status: 400,
          message: 'Validation failed',
          fieldErrors: { email: 'must be a well-formed email address', fullName: 'must not be blank' },
        },
      },
    });

    const error = await api
      .createEmployee({
        fullName: '',
        email: 'nope',
        role: 'EMPLOYEE',
        jobTitle: null,
        departmentId: null,
        managerId: null,
        hireDate: '2025-01-01',
        salary: null,
      })
      .catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.fieldErrors).toEqual({
      email: 'must be a well-formed email address',
      fullName: 'must not be blank',
    });
  });

  it('falls back to a readable message when the error body is not JSON', async () => {
    installFetchMock({ 'GET /api/employees/me': { status: 500, body: undefined } });

    const error = await api.me().catch((cause: unknown) => cause);

    expect((error as ApiError).message).toBe('Request failed (500 Internal Server Error).');
  });

  it('calls the unauthorized handler once on an authenticated 401', async () => {
    writeToken('expired');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    installFetchMock({
      'GET /api/employees/me': { status: 401, body: { status: 401, message: 'Token expired' } },
    });

    await expect(api.me()).rejects.toThrowError('Token expired');
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not call the unauthorized handler for a failed login', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    installFetchMock({
      'POST /api/auth/login': {
        status: 401,
        body: { status: 401, message: 'Invalid email or password' },
      },
    });

    await expect(api.login({ email: 'a@b.co', password: 'x' })).rejects.toThrowError(
      'Invalid email or password',
    );
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('reports a network failure as an ApiError with status 0', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    const error = await api.me().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isNetworkError).toBe(true);
  });

  it('resolves a 204 with no body', async () => {
    installFetchMock({ 'DELETE /api/departments/dep-1': { status: 204 } });

    await expect(api.deleteDepartment('dep-1')).resolves.toBeUndefined();
  });

  it('downloads a blob with the bearer token attached', async () => {
    writeToken('jwt-123');
    const pdf = new Blob(['%PDF-1.7'], { type: 'application/pdf' });
    const mock = installFetchMock({
      'GET /api/payroll/payslips/pay-1/pdf': { blob: pdf },
    });

    const blob = await api.payslipPdf('pay-1');

    expect(blob).toBe(pdf);
    const call = mock.callsTo('GET /api/payroll/payslips/pay-1/pdf')[0];
    expect(call.headers.Authorization).toBe('Bearer jwt-123');
  });

  it('turns a failed blob download into an ApiError and signals the 401', async () => {
    writeToken('expired');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    installFetchMock({
      'GET /api/payroll/runs/run-1/export.xlsx': {
        status: 401,
        body: { status: 401, message: 'Token expired' },
      },
    });

    await expect(requestBlob('/api/payroll/runs/run-1/export.xlsx')).rejects.toThrowError(
      'Token expired',
    );
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('builds the employee query string from the filters it was given', async () => {
    const mock = installFetchMock({ 'GET /api/employees': { body: { content: [] } } });

    await api.listEmployees({
      q: 'nadia',
      departmentId: 'dep-1',
      status: 'ACTIVE',
      page: 2,
      size: 20,
      sort: 'hireDate,desc',
    });

    expect(mock.calls[0].url).toBe(
      '/api/employees?q=nadia&departmentId=dep-1&status=ACTIVE&page=2&size=20&sort=hireDate%2Cdesc',
    );
  });

  it('omits empty filters from the query string', async () => {
    const mock = installFetchMock({ 'GET /api/employees': { body: { content: [] } } });

    await api.listEmployees({ page: 0, size: 20 });

    expect(mock.calls[0].url).toBe('/api/employees?page=0&size=20');
  });
});
