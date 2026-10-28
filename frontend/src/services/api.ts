/**
 * The single place the frontend talks to the API.
 *
 * Two details here are load-bearing rather than incidental:
 *
 * 1. The session cookie is HttpOnly, so JavaScript cannot read it and cannot
 *    mint a CSRF token from it. Spring Security's `CookieCsrfTokenRepository`
 *    works the other way round: it exposes a readable `XSRF-TOKEN` cookie and
 *    expects it echoed in the `X-XSRF-TOKEN` header. `resolveCsrfToken` does
 *    exactly that, and every mutating call goes through here so the header can
 *    never be forgotten at a call site.
 *
 * 2. `/api` is same-origin (the Vite dev proxy, or same-origin production
 *    serving), so requests are sent with credentials and no CORS is involved.
 */

import type { ApiError } from '@/types/api'

/** The readable CSRF cookie Spring Security writes. */
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export class ApiRequestError extends Error {
  readonly status: number
  readonly violations: ApiError['violations']

  constructor(status: number, message: string, violations: ApiError['violations'] = []) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.violations = violations
  }

  /**
   * A per-field message for the first violation on `field`, if the server
   * reported one. Used to annotate the matching input.
   */
  violationFor(field: string): string | undefined {
    return this.violations.find((violation) => violation.field === field)?.message
  }
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length))
    }
  }
  return null
}

function resolveCsrfToken(): string | null {
  return readCookie(CSRF_COOKIE)
}

async function readError(response: Response): Promise<ApiRequestError> {
  // The backend answers with the shared ApiError shape, but a proxy or a
  // network-level failure may not, so the body is treated as untrusted.
  let status = response.status
  let message = `Request failed with status ${status}.`
  let violations: ApiError['violations'] = []

  try {
    const body = (await response.json()) as Partial<ApiError>
    if (typeof body.status === 'number') {
      status = body.status
    }
    if (body.message) {
      message = body.message
    }
    if (Array.isArray(body.violations)) {
      violations = body.violations
    }
  } catch {
    // A non-JSON body (an HTML error page, for instance) leaves the defaults.
  }

  return new ApiRequestError(status, message, violations)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (!(init.body instanceof FormData)) {
    headers.set('Accept', 'application/json')
  }
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const token = resolveCsrfToken()
    if (token) {
      headers.set(CSRF_HEADER, token)
    }
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await readError(response)
  }
  // 204 carries no body, which is the correct answer for sign-out.
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),

  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),

  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),

  postEmpty: (path: string) => request<void>(path, { method: 'POST' }),

  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),

  delete: (path: string) => request<void>(path, { method: 'DELETE' }),

  deleteForResult: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
