import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AdminCoursesPage } from '../admin-courses/AdminCoursesPage.tsx'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import type { User } from '../auth/types.ts'
import { CoursesPage } from './CoursesPage.tsx'
import type { Course } from './course-api.ts'

const published: Course = {
  id: 'course-1',
  title: 'Distributed Systems',
  code: 'CISS',
  term: '2026WS',
  visibility: 'PUBLISHED',
  enrolled: false,
  owned: false,
}

const professor: User = {
  id: 'admin-1',
  email: 'admin@uni-companion.test',
  displayName: 'Course Admin',
  interests: null,
  roles: ['COURSE_ADMIN'],
  createdAt: '2026-01-01T00:00:00Z',
}

const studentUser: User = {
  id: 'student-1',
  email: 'student',
  displayName: 'Student',
  interests: null,
  roles: ['STUDENT'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderWithQuery(ui: ReactNode, user: User | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider initialUser={user}>
        <MemoryRouter>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('course pages', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('lets a student enroll in a published course and hides course creation', async () => {
    setAccessToken('student-token')
    let joined = false
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        joined = true
        return json({ courseId: 'course-1' }, 201)
      }
      return json([{ ...published, enrolled: joined }])
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithQuery(<CoursesPage />, studentUser)

    expect(await screen.findByText('Distributed Systems')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Distributed Systems' })).toHaveAttribute(
      'href',
      '/courses/course-1',
    )
    expect(screen.queryByRole('button', { name: 'Create course' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Enroll' }))

    expect(await screen.findByText('Enrolled')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enroll' })).not.toBeInTheDocument()
    expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('http://localhost:8082/api/courses')
  })

  it('shows Enrolled when the student already joined the course', async () => {
    setAccessToken('student-token')
    vi.stubGlobal('fetch', vi.fn(async () => json([{ ...published, enrolled: true }])))

    renderWithQuery(<CoursesPage />, studentUser)

    expect(await screen.findByText('Enrolled')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enroll' })).not.toBeInTheDocument()
  })

  it('lets a student unenroll and shows Enroll again', async () => {
    setAccessToken('student-token')
    let joined = true
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          joined = false
          return new Response(null, { status: 204 })
        }
        return json([{ ...published, enrolled: joined }])
      }),
    )

    renderWithQuery(<CoursesPage />, studentUser)

    fireEvent.click(await screen.findByRole('button', { name: 'Unenroll' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Unenroll' }))
    expect(await screen.findByRole('button', { name: 'Enroll' })).toBeInTheDocument()
    expect(screen.queryByText('Enrolled')).not.toBeInTheDocument()
  })

  it('lets an admin create a course and publish it', async () => {
    setAccessToken('admin-token')
    const created: Course = {
      ...published,
      id: 'course-2',
      visibility: 'UNPUBLISHED',
      code: 'CISS-NEW',
      owned: true,
    }
    let listed: Course[] = []
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          title: 'Distributed Systems',
          code: 'CISS-NEW',
          term: '2026WS',
        })
        listed = [created]
        return json(created, 201)
      }
      if (init?.method === 'PATCH') {
        const body = JSON.parse(String(init.body)) as { visibility: Course['visibility'] }
        listed = [{ ...created, visibility: body.visibility }]
        return json(listed[0])
      }
      if (init?.method === 'DELETE') {
        listed = []
        return new Response(null, { status: 204 })
      }
      return json(listed)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderWithQuery(<AdminCoursesPage />, professor)

    expect(await screen.findByText('No courses yet.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Create course' }))
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Distributed Systems' } })
    fireEvent.change(screen.getByLabelText('Code'), { target: { value: 'CISS-NEW' } })
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2026' } })
    fireEvent.change(screen.getByLabelText('Semester'), { target: { value: 'WS' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save course' }))

    expect(await screen.findByText('CISS-NEW · 2026WS · Unpublished')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open materials for Distributed Systems' })).toHaveAttribute(
      'href',
      '/admin/courses/course-2/materials',
    )
    fireEvent.click(screen.getByRole('button', { name: 'Publish' }))
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publish' }))
    expect(await screen.findByText('CISS-NEW · 2026WS · Published')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Unpublish' }))
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Unpublish' }))
    expect(await screen.findByText('CISS-NEW · 2026WS · Unpublished')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Delete permanently' }))
    expect(await screen.findByText('No courses yet.')).toBeInTheDocument()
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
