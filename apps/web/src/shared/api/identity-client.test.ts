import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from './access-token.ts'
import { identityFetch } from './identity-client.ts'

describe('identity client', () => {
  afterEach(() => {
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('sends the bearer token when a session exists', async () => {
    setAccessToken('token-1')
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
      return new Response(JSON.stringify({ ok: true }), { status: 200 })
    })
    vi.stubGlobal('fetch', fetchMock)

    await identityFetch('/api/me')

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(String(fetchMock.mock.calls[0][0])).toBe('http://localhost:8081/api/me')
    expect(headers.get('Authorization')).toBe('Bearer token-1')
  })
})
