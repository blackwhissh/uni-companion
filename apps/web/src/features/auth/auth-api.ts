import { getAccessToken, setAccessToken } from '../../shared/api/access-token.ts'
import { allowTokenRefresh, blockTokenRefresh, identityFetch, tryRefreshAccessToken } from '../../shared/api/identity-client.ts'
import type { AuthResponse, User } from './types.ts'

export function registerAccount(input: { email: string; password: string; displayName: string }) {
  allowTokenRefresh()
  return identityFetch<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function loginAccount(input: { email: string; password: string }) {
  allowTokenRefresh()
  return identityFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      email: input.email.trim(),
      password: input.password,
    }),
  })
}

export async function logoutAccount() {
  blockTokenRefresh()
  try {
    await identityFetch<void>('/api/auth/logout', { method: 'POST' }, false)
  } catch {
    // Still clear local session below — lab PCs must not keep the previous user.
  } finally {
    setAccessToken(null)
  }
}

export function updateProfile(input: { displayName: string; interests: string }) {
  return identityFetch<User>('/api/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function fetchMe() {
  return identityFetch<User>('/api/me')
}

export async function restoreSession(): Promise<AuthResponse | null> {
  allowTokenRefresh()
  const existing = getAccessToken()
  if (existing) {
    try {
      const user = await fetchMe()
      return { token: getAccessToken() ?? existing, user }
    } catch {
      // fall through to refresh cookie
    }
  }
  const refreshed = await tryRefreshAccessToken()
  if (!refreshed) {
    setAccessToken(null)
    return null
  }
  try {
    const user = await fetchMe()
    const token = getAccessToken()
    if (!token) {
      return null
    }
    return { token, user }
  } catch {
    setAccessToken(null)
    return null
  }
}
