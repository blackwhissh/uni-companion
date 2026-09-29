import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RequireRole } from '../../app/guards.tsx'
import { CoursesPage } from '../courses/CoursesPage.tsx'
import { AuthProvider } from './AuthProvider.tsx'
import { LoginPage } from './LoginPage.tsx'
import { RegisterPage } from './RegisterPage.tsx'
import type { User } from './types.ts'

const student: User = {
  id: '1',
  email: 'ada@uni.test',
  displayName: 'Ada',
  interests: null,
  roles: ['STUDENT'],
  createdAt: '2026-09-27T00:00:00Z',
}

function renderAuth(path: string, page: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <Routes>
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/courses" element={<CoursesPage />} />
            <Route path="/admin/courses" element={<h1>Course console</h1>} />
            <Route path={path} element={page} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

describe('auth flow', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('registers a student and opens their courses', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/api/courses')) {
        return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      expect(JSON.parse(String(init?.body))).toEqual({
        email: 'ada@uni.test',
        password: 'password1',
        displayName: 'Ada Lovelace',
      })
      return new Response(JSON.stringify({ token: 'token-1', user: student }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAuth('/register', <RegisterPage />)
    fill('Display name', 'Ada Lovelace')
    fill('Email', 'ada@uni.test')
    fill('Password', 'password1')
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Your courses' })).toBeInTheDocument()
  })

  it('sends a course admin to the course console after login', async () => {
    const admin = { ...student, roles: ['COURSE_ADMIN'], displayName: 'Course Admin' }
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify({ token: 'token-1', user: admin }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    )

    renderAuth('/login', <LoginPage />)
    fill('Email', 'admin@uni-companion.local')
    fill('Password', 'admin-pass-1')
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'Course console' })).toBeInTheDocument()
  })

  it('keeps a student out of the admin console', () => {
    render(
      <MemoryRouter initialEntries={['/admin/courses']}>
        <AuthProvider initialUser={student}>
          <Routes>
            <Route
              path="/admin/courses"
              element={
                <RequireRole role="COURSE_ADMIN">
                  <h1>Course console</h1>
                </RequireRole>
              }
            />
            <Route path="/courses" element={<h1>Your courses</h1>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Your courses' })).toBeInTheDocument()
  })
})
