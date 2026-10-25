/**
 * Shared HTTP client for the backend API. Every feature's api.ts goes through `request`, so
 * CSRF tokens, error messages and session expiry are handled in one place.
 */

export const NETWORK_ERROR_MESSAGE = "Can't reach the server. Is the backend running?"

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']
// Gateway errors come from a proxy in front of the backend (Vite in development).
const UNREACHABLE_STATUSES = [502, 503, 504]

/** An HTTP error response; `message` is ready to show to the user. */
export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

type UnauthorizedListener = () => void
const unauthorizedListeners = new Set<UnauthorizedListener>()

/** Called whenever the backend answers 401, e.g. when the session has expired. */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  // FormData and URLSearchParams bodies set their own Content-Type.
  if (typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!SAFE_METHODS.includes(method)) {
    headers.set(CSRF_HEADER, await csrfToken())
  }

  const res = await send(path, { ...init, method, headers })
  if (res.status === 401) unauthorizedListeners.forEach((listener) => listener())
  if (!res.ok) throw new ApiError(res.status, await errorMessage(res))
  return res.status === 204 ? (undefined as T) : res.json()
}

async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`/api${path}`, { credentials: 'same-origin', ...init })
  } catch {
    // fetch only rejects when no response arrived at all (server down, network error).
    throw new ApiError(0, NETWORK_ERROR_MESSAGE)
  }
}

/** The CSRF token from its cookie, asking the backend to issue one first if needed. */
async function csrfToken(): Promise<string> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await send('/auth/csrf', { method: 'GET' })
    token = readCookie(CSRF_COOKIE)
  }
  return token ?? ''
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length)
}

/** Prefers the backend's problem-detail message; falls back to the HTTP status. */
async function errorMessage(res: Response): Promise<string> {
  if (UNREACHABLE_STATUSES.includes(res.status)) return NETWORK_ERROR_MESSAGE
  try {
    const body = await res.json()
    if (typeof body?.detail === 'string' && body.detail) return body.detail
  } catch {
    // Not JSON; use the status line below.
  }
  return `${res.status} ${res.statusText}`.trim()
}
