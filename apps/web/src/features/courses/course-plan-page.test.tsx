import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { CoursePlanPage } from './CoursePlanPage.tsx'
import type { Course } from './course-api.ts'

const course: Course = {
  id: 'course-1',
  title: 'Distributed Systems',
  code: 'CISS',
  term: '2026WS',
  visibility: 'PUBLISHED',
  enrolled: true,
  owned: false,
}

const student = {
  id: 'student-1',
  email: 'student@example.com',
  displayName: 'Student',
  interests: '',
  roles: ['STUDENT'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderAt(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/courses/course-1/plan']}>
        <AuthProvider initialUser={student} initialReady>
          <Routes>
            <Route path="/courses/:courseId/plan" element={ui} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('course plan page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('builds a plan that schedules a missed question before the exam', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      if (target.endsWith('/plan') && init?.method === 'PUT') {
        expect(JSON.parse(String(init.body))).toEqual({ examDate: '2026-11-06' })
        return json({
          examDate: '2026-11-06',
          daysUntilExam: 2,
          steps: [
            {
              date: '2026-11-04',
              kind: 'GAP',
              prompt: 'What is Raft?',
              missCount: 1,
            },
            {
              date: '2026-11-06',
              kind: 'REVIEW',
              prompt: null,
              missCount: 0,
            },
          ],
        })
      }
      if (target.endsWith('/plan')) {
        return json({ message: 'Not found' }, 404)
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt(<CoursePlanPage />)

    expect(await screen.findByRole('heading', { name: 'Study plan' })).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('Exam date'), { target: { value: '2026-11-06' } })
    fireEvent.click(screen.getByRole('button', { name: 'Build my plan' }))

    expect(await screen.findByText('2 days until the exam')).toBeInTheDocument()
    expect(screen.getByText(/What is Raft\?/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ask about this' })).toHaveAttribute(
      'href',
      '/courses/course-1/qa?question=What%20is%20Raft%3F',
    )
    expect(screen.getByText('Review the published lectures before the exam.')).toBeInTheDocument()
    expect(screen.queryByText(/student@example.com/)).not.toBeInTheDocument()
  })
})
