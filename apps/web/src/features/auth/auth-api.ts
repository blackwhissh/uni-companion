import { identityFetch } from '../../shared/api/identity-client.ts'
import type { AuthResponse, User } from './types.ts'

export function registerAccount(input: { email: string; password: string; displayName: string }) {
  return identityFetch<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function loginAccount(input: { email: string; password: string }) {
  return identityFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  })
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
