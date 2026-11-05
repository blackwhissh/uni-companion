import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import type { User } from '../auth/types.ts'
import { ProfilePage } from './ProfilePage.tsx'

const student: User = {
  id: 'student-1',
  email: 'student@uni.test',
  displayName: 'Ada Lovelace',
  interests: 'Distributed systems',
  roles: ['STUDENT'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderProfile() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider initialUser={student}>
        <MemoryRouter initialEntries={['/profile']}>
          <Routes>
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/login" element={<h1>Login screen</h1>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('profile page privacy controls', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('exports personal data as JSON and deletes the account on confirmation', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/api/me/data') && (!init?.method || init.method === 'GET')) {
        return json({
          userId: 'student-1',
          exportedAt: '2026-11-05T00:00:00Z',
          enrollments: [{ courseId: 'c1', title: 'Distributed Systems', code: 'CISS', term: '2026WS' }],
          studyPlans: [],
          quizAttempts: [],
          matchProfiles: [],
          contacts: [],
        })
      }
      if (url.endsWith('/api/me/data') && init?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (url.endsWith('/api/me') && init?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (url.endsWith('/api/me')) {
        return json(student)
      }
      return json({}, 500)
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:personal-data')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderProfile()

    expect(await screen.findByDisplayValue('Ada Lovelace')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Download my data' }))
    expect(await screen.findByText('Personal data export downloaded.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Delete account' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Delete account' }))

    expect(await screen.findByRole('heading', { name: 'Login screen' })).toBeInTheDocument()
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
