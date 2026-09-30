import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RequireAuth } from '../../app/guards.tsx'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from './AuthProvider.tsx'
import { LoginPage } from './LoginPage.tsx'
import { markLogoutNavigation, shouldSkipReturnPath } from './auth-session-sync.ts'
import type { User } from './types.ts'

const student: User = {
  id: '1',
  email: 'ada@uni.test',
  displayName: 'Ada',
  interests: null,
  roles: ['STUDENT'],
  createdAt: '2026-09-27T00:00:00Z',
}

describe('logout return path', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  it('does not send the next login back to the previous user page after logout', async () => {
    markLogoutNavigation()
    expect(shouldSkipReturnPath()).toBe(true)

    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify({ token: 'token-2', user: student }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    )

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter
          initialEntries={[
            {
              pathname: '/login',
              state: { from: { pathname: '/courses/old-course/quiz', search: '', hash: '' } },
            },
          ]}
        >
          <AuthProvider initialReady>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/courses" element={<h1>Your courses</h1>} />
              <Route path="/courses/old-course/quiz" element={<h1>Stale quiz</h1>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ada@uni.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'Your courses' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Stale quiz' })).not.toBeInTheDocument()
    expect(shouldSkipReturnPath()).toBe(false)
  })

  it('RequireAuth omits return state while a logout skip flag is set', async () => {
    markLogoutNavigation()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/courses/secret/quiz']}>
          <AuthProvider initialReady initialUser={null}>
            <Routes>
              <Route
                path="/courses/secret/quiz"
                element={
                  <RequireAuth>
                    <h1>Secret quiz</h1>
                  </RequireAuth>
                }
              />
              <Route path="/login" element={<LoginPage />} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
    })
    expect(screen.queryByRole('heading', { name: 'Secret quiz' })).not.toBeInTheDocument()
  })
})
