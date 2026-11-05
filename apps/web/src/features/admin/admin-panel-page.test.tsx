import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import type { User } from '../auth/types.ts'
import { AdminPanelPage } from './AdminPanelPage.tsx'

const admin: User = {
  id: 'admin-1',
  email: 'admin',
  displayName: 'Admin',
  interests: null,
  roles: ['ADMIN', 'COURSE_ADMIN'],
  createdAt: '2026-01-01T00:00:00Z',
}

const accounts = [
  {
    id: 'admin-1',
    email: 'admin',
    displayName: 'Admin',
    roles: ['ADMIN', 'COURSE_ADMIN'],
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'prof-1',
    email: 'professor',
    displayName: 'Professor',
    roles: ['COURSE_ADMIN'],
    createdAt: '2026-01-01T12:00:00Z',
  },
  {
    id: 'student-1',
    email: 'student',
    displayName: 'Student',
    roles: ['STUDENT'],
    createdAt: '2026-01-02T00:00:00Z',
  },
]

const courses = [
  {
    id: 'course-1',
    title: 'Distributed Systems',
    code: 'CISS',
    term: '2026WS',
    visibility: 'PUBLISHED',
    ownerId: 'admin-1',
    enrollmentCount: 4,
    publishedReadyMaterials: 2,
    failedMaterials: 1,
    otherMaterials: 0,
  },
]

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider initialUser={admin}>
        <MemoryRouter>
          <AdminPanelPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('admin panel', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('lists accounts and course health, then grants course admin', async () => {
    setAccessToken('admin-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/api/admin/accounts') && (!init?.method || init.method === 'GET')) {
        return json(accounts)
      }
      if (url.endsWith('/api/admin/courses')) {
        return json(courses)
      }
      if (url.endsWith('/course-admin') && init?.method === 'PUT') {
        return json({ ...accounts[2], roles: ['COURSE_ADMIN', 'STUDENT'] })
      }
      if (url.endsWith('/owner') && init?.method === 'PATCH') {
        return json({ ...courses[0], owned: false })
      }
      return json({ code: 'REQUEST_FAILED', message: 'Request failed' }, 500)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderPanel()

    expect(await screen.findByRole('heading', { name: 'Admin panel' })).toBeInTheDocument()
    expect(await screen.findByText('student')).toBeInTheDocument()
    expect(screen.getByText('4 students enrolled · 2 published lectures')).toBeInTheDocument()
    expect(screen.getByText('1 failed lecture')).toBeInTheDocument()
    expect(screen.getByText('CISS · 2026WS · Admin')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open materials' })).toHaveAttribute(
      'href',
      '/admin/courses/course-1/materials',
    )

    fireEvent.change(screen.getByLabelText('Owner for Distributed Systems'), {
      target: { value: 'prof-1' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Transfer owner' }))
    expect(await screen.findByText(/Transferred “Distributed Systems” to Professor/)).toBeInTheDocument()
    const ownerCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith('/owner'))
    expect(ownerCall?.[1]).toMatchObject({ method: 'PATCH', body: JSON.stringify({ ownerId: 'prof-1' }) })

    fireEvent.click(screen.getByRole('button', { name: 'Grant course admin' }))
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Grant course admin' }))

    expect(await screen.findByText(/Student is now a course admin/)).toBeInTheDocument()
    const grantCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith('/course-admin'))
    expect(grantCall?.[1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ courseAdmin: true }) })
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
