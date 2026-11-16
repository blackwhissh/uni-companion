import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { CourseProgressPage } from './CourseProgressPage.tsx'
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
      <MemoryRouter initialEntries={['/courses/course-1/progress']}>
        <AuthProvider initialUser={student} initialReady>
          <Routes>
            <Route path="/courses/:courseId/progress" element={ui} />
            <Route path="/courses/:courseId/quiz" element={<OpenedQuiz />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('course progress page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('shows an empty state before any quiz is submitted', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/progress')) {
          return json({
            attemptCount: 0,
            latestScore: null,
            latestTotal: null,
            bestScore: null,
            bestTotal: null,
            attempts: [],
            weakTopics: [],
          })
        }
        return json(course)
      }),
    )

    renderAt(<CourseProgressPage />)

    expect(await screen.findByText('No quizzes yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Take a quiz' })).toHaveAttribute('href', '/courses/course-1/quiz')
  })

  it('lists a missed prompt and links Q&A to that question', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/progress')) {
          return json({
            attemptCount: 2,
            latestScore: 0,
            latestTotal: 1,
            bestScore: 1,
            bestTotal: 1,
            attempts: [
              { id: 'attempt-2', score: 0, total: 1, createdAt: '2026-03-02T11:00:00Z' },
              { id: 'attempt-1', score: 1, total: 1, createdAt: '2026-03-01T11:00:00Z' },
            ],
            weakTopics: [
              {
                prompt: 'What is Raft?',
                missCount: 2,
                lastMissedAt: '2026-03-02T11:00:00Z',
              },
            ],
          })
        }
        return json(course)
      }),
    )

    renderAt(<CourseProgressPage />)

    expect(await screen.findByText('What is Raft?')).toBeInTheDocument()
    expect(screen.getByText('Missed 2 times')).toBeInTheDocument()
    expect(screen.getAllByText('0 / 1').length).toBeGreaterThan(0)
    expect(screen.getAllByText('1 / 1').length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: 'Ask about this' })).toHaveAttribute(
      'href',
      '/courses/course-1/qa?question=What%20is%20Raft%3F',
    )
    expect(screen.getByRole('link', { name: 'Open quiz' })).toHaveAttribute('href', '/courses/course-1/quiz')
  })

  it('starts a practice quiz from the missed questions', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (init?.method === 'POST' && String(url).includes('/quizzes/practice')) {
        return json({ id: 'quiz-9' })
      }
      if (String(url).endsWith('/progress')) {
        return json({
          attemptCount: 1,
          latestScore: 0,
          latestTotal: 1,
          bestScore: 0,
          bestTotal: 1,
          attempts: [{ id: 'attempt-1', score: 0, total: 1, createdAt: '2026-03-02T11:00:00Z' }],
          weakTopics: [{ prompt: 'What is Raft?', missCount: 1, lastMissedAt: '2026-03-02T11:00:00Z' }],
        })
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt(<CourseProgressPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Practice these questions' }))
    expect(await screen.findByText('Opened quiz quiz-9')).toBeInTheDocument()
  })
})

function OpenedQuiz() {
  const [params] = useSearchParams()
  return <p>Opened quiz {params.get('quiz')}</p>
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
