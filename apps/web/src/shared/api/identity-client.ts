import { identityApiUrl } from './config.ts'
import { getAccessToken } from './access-token.ts'

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status = 0) {
    super(message)
    this.code = code
    this.status = status
  }
}

export async function identityFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${identityApiUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const code = body?.code ?? 'REQUEST_FAILED'
    const message = body?.message ?? 'Request failed'
    throw new ApiError(code, message, response.status)
  }
  return response.json() as Promise<T>
}
