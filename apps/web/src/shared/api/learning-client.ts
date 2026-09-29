import { getAccessToken } from './access-token.ts'
import { learningApiUrl } from './config.ts'
import { ApiError } from './identity-client.ts'

export async function learningFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${learningApiUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.code ?? 'REQUEST_FAILED', body?.message ?? 'Request failed', response.status)
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
  const response = await fetch(`${learningApiUrl}${path}`, { method: 'POST', body, headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(payload?.code ?? 'REQUEST_FAILED', payload?.message ?? 'Request failed', response.status)
  }
  return response.json() as Promise<T>
}
