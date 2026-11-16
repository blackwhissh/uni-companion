import { identityApiUrl } from './config.ts'
import { clearAccessToken, getAccessToken, setAccessToken } from './access-token.ts'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: Record<string, string> | null

  constructor(code: string, message: string, status = 0, details: Record<string, string> | null = null) {
    super(message)
    this.code = code
    this.status = status
    this.details = details
  }
}

type TokenResponse = { token: string }

let refreshInFlight: Promise<boolean> | null = null
/** Set after logout so a racing 401 cannot silently restore the previous session. */
let refreshBlocked = false

export function blockTokenRefresh() {
  refreshBlocked = true
  refreshInFlight = null
}

export function allowTokenRefresh() {
  refreshBlocked = false
}

export async function tryRefreshAccessToken(): Promise<boolean> {
  if (refreshBlocked) {
    clearAccessToken()
    return false
  }
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        if (refreshBlocked) {
          clearAccessToken()
          return false
        }
        const response = await fetch(`${identityApiUrl}/api/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        })
        if (!response.ok) {
          clearAccessToken()
          return false
        }
        if (refreshBlocked) {
          clearAccessToken()
          return false
        }
        const body = (await response.json()) as TokenResponse
        setAccessToken(body.token)
        return true
      } catch {
        clearAccessToken()
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

export async function identityFetch<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${identityApiUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })
  if (response.status === 401 && allowRefresh && !path.startsWith('/api/auth/')) {
    const refreshed = await tryRefreshAccessToken()
    if (refreshed) {
      return identityFetch<T>(path, init, false)
    }
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const code = body?.code ?? 'REQUEST_FAILED'
    const message =
      (typeof body?.message === 'string' && body.message.trim()) ||
      fallbackIdentityError(response.status, path, code)
    throw new ApiError(code, message, response.status, body?.details ?? null)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

function fallbackIdentityError(status: number, path: string, code: string): string {
  if (code === 'INVALID_CREDENTIALS' || (status === 401 && path.startsWith('/api/auth/login'))) {
    return 'Invalid email or password.'
  }
  if (status === 0) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  if (status === 401) {
    return 'Your session expired. Please log in again.'
  }
  if (status === 403) {
    return 'You do not have permission to do that.'
  }
  if (status === 404) {
    return 'The requested resource was not found.'
  }
  if (status >= 500) {
    return 'The server had a problem. Please try again in a moment.'
  }
  if (status === 400) {
    return 'Please check your input and try again.'
  }
  return `Request failed (${status}).`
}
