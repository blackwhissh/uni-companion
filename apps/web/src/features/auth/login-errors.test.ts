import { describe, expect, it, vi, afterEach } from 'vitest'
import { identityFetch } from '../../shared/api/identity-client.ts'

describe('login errors', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('maps failed login to wrong credentials', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'Bad credentials' }), { status: 401 })),
    )
    await expect(identityFetch('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      message: 'Wrong username or password.',
    })
  })

  it('maps network failures to a reachable-server message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))
    await expect(identityFetch('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      message: 'Could not reach the server. Check your connection and try again.',
    })
  })
})
