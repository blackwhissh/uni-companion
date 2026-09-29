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

export async function tryRefreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${identityApiUrl}/api/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        })
        if (!response.ok) {
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
    const message = body?.message ?? 'Request failed'
    throw new ApiError(code, message, response.status, body?.details ?? null)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}
