import { learningApiUrl } from './config.ts'
import { getAccessToken } from './access-token.ts'
import { ApiError, tryRefreshAccessToken } from './identity-client.ts'

export async function learningFetch<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${learningApiUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })
  if (response.status === 401 && allowRefresh) {
    const refreshed = await tryRefreshAccessToken()
    if (refreshed) {
      return learningFetch<T>(path, init, false)
    }
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.code ?? 'REQUEST_FAILED', body?.message ?? 'Request failed', response.status, body?.details ?? null)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export async function learningUpload<T>(path: string, body: FormData): Promise<T> {
  const headers = new Headers()
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${learningApiUrl}${path}`, {
    method: 'POST',
    body,
    headers,
    credentials: 'include',
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(
      payload?.code ?? 'REQUEST_FAILED',
      payload?.message ?? 'Request failed',
      response.status,
      payload?.details ?? null,
    )
  }
  return response.json() as Promise<T>
}
