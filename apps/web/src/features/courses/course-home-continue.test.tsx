import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import type { User } from '../auth/types.ts'
import { CourseHomePage } from './CourseHomePage.tsx'
import type { Course } from './course-api.ts'
import type { Material } from './material-api.ts'
import { localIsoDate } from './study-continue.ts'

const course: Course = {
  id: 'course-1',
  title: 'Distributed Systems',
  code: 'CISS',
  term: '2026WS',
  visibility: 'PUBLISHED',
  enrolled: true,
  owned: false,
}

const material: Material = {
  id: 'material-1',
  courseId: 'course-1',
  title: 'Lecture 1',
  fileName: 'lecture.pdf',
  visibility: 'PUBLISHED',
  processingStatus: 'READY',
}

const student: User = {
  id: 'student-1',
  email: 'student@example.com',
  displayName: 'Student',
  interests: null,
  roles: ['STUDENT'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderAt(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider initialUser={student}>
        <MemoryRouter initialEntries={['/courses/course-1']}>
          <Routes>
            <Route path="/courses/:courseId" element={ui} />
            <Route path="/courses/:courseId/quiz" element={<OpenedQuiz />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('course home continue', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('shows the last chat, today’s gap, lecture marks, and starts practice', async () => {
    setAccessToken('student-token')
    const today = localIsoDate()
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (init?.method === 'POST' && path.includes('/quizzes/practice')) {
        return json({ id: 'quiz-9' })
      }
      if (path.endsWith('/materials')) {
        return json([material])
      }
      if (path.endsWith('/latest')) {
        return json({
          id: 'session-9',
          courseId: 'course-1',
          materialIds: ['material-1'],
          updatedAt: '2026-11-16T10:00:00Z',
          preview: 'What is consensus?',
        })
      }
      if (path.endsWith('/plan')) {
        return json({
          examDate: today,
          daysUntilExam: 0,
          steps: [{ date: today, kind: 'GAP', prompt: 'What is Raft?', missCount: 2 }],
        })
      }
      if (path.endsWith('/activity')) {
        return json([{ materialId: 'material-1', kinds: ['SUMMARY', 'CHAT'] }])
      }
      if (path.endsWith('/active-count')) {
        return json({ optedIn: false, activeCount: 0 })
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt(<CourseHomePage />)

    expect(await screen.findByRole('link', { name: 'Continue chat' })).toHaveAttribute(
      'href',
      '/courses/course-1/qa?session=session-9',
    )
    expect(screen.getByText('What is consensus?')).toBeInTheDocument()
    expect(screen.getByText('Today: What is Raft?')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ask about this' })).toHaveAttribute(
      'href',
      '/courses/course-1/qa?question=What%20is%20Raft%3F',
    )
    expect(screen.getByText('Summarized · Chat')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Practice these questions' }))
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
