import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { CourseQuizPage } from './CourseQuizPage.tsx'
import type { Course } from './course-api.ts'
import type { Material } from './material-api.ts'

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

const student = {
  id: 'student-1',
  email: 'student@example.com',
  displayName: 'Student',
  interests: '',
  roles: ['STUDENT'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderAt(path: string, ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider initialUser={student} initialReady>
          <Routes>
            <Route path="/courses/:courseId/quiz" element={ui} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('course quiz page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  it('generates a quiz, answers, and shows the score', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (path.includes('/quizzes/generate') && init?.method === 'POST') {
        return json(
          {
            id: 'quiz-1',
            courseId: 'course-1',
            version: 1,
            availableVersions: 1,
            maxVersions: 3,
            delivery: 'CREATED',
            versions: [{ id: 'quiz-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
            questions: [
              {
                id: 'q-1',
                prompt: 'Which statement is supported?',
                options: ['Replicas stay consistent.', 'Ignore lecture PDFs.', 'Skip enrollment.', 'Use drafts.'],
              },
            ],
          },
          201,
        )
      }
      if (path.includes('/quizzes/quiz-1/submit') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          answers: [{ questionId: 'q-1', selectedIndex: 0 }],
        })
        return json({
          id: 'attempt-1',
          score: 1,
          total: 1,
          reviews: [
            {
              questionId: 'q-1',
              selectedIndex: 0,
              correctIndex: 0,
              correct: true,
              correctOption: 'Replicas stay consistent.',
              explanation: 'From Lecture 1 (page 1): Consensus algorithms keep replicas consistent.',
            },
          ],
        })
      }
      if (path.endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/quiz', <CourseQuizPage />)

    expect(await screen.findByText('Practice quiz')).toBeInTheDocument()
    expect(await screen.findByText('Choose materials')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Start quiz' }))

    expect(await screen.findByText('Which statement is supported?')).toBeInTheDocument()
    expect(screen.getByText('A new shared quiz was created for this material selection.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quiz 1' })).toBeDisabled()
    const generateCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url).includes('/quizzes/generate') && init?.method === 'POST',
    )
    expect(JSON.parse(String(generateCall?.[1]?.body))).toEqual({ materialIds: ['material-1'] })
    fireEvent.click(screen.getByRole('radio', { name: 'Replicas stay consistent.' }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }))

    expect(await screen.findByText('Score: 1 / 1')).toBeInTheDocument()
    expect(screen.getByText(/Correct answer:/)).toBeInTheDocument()
    expect(screen.getByText(/Explanation:/)).toBeInTheDocument()
    expect(
      screen.getByText('From Lecture 1 (page 1): Consensus algorithms keep replicas consistent.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try this quiz again' }))
    expect(screen.queryByText('Score: 1 / 1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit answers' })).toBeDisabled()
  })

  it('lets the student retry after generation is still running', async () => {
    setAccessToken('student-token')
    let generateCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (path.includes('/quizzes/generate') && init?.method === 'POST') {
        generateCalls += 1
        if (generateCalls === 1) {
          return json({ code: 'UNAVAILABLE', message: 'Generation is taking longer than expected.' }, 503)
        }
        return json(
          {
            id: 'quiz-1',
            courseId: 'course-1',
            version: 1,
            availableVersions: 1,
            maxVersions: 3,
            delivery: 'CREATED',
            versions: [{ id: 'quiz-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
            questions: [
              {
                id: 'q-1',
                prompt: 'Which statement is supported?',
                options: ['Replicas stay consistent.', 'Ignore lecture PDFs.', 'Skip enrollment.', 'Use drafts.'],
              },
            ],
          },
          201,
        )
      }
      if (path.endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/quiz', <CourseQuizPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start quiz' }))

    expect(await screen.findByText('Generation is still running. Wait a moment, then try again.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Which statement is supported?')).toBeInTheDocument()
    expect(generateCalls).toBe(2)
  })

  it('resumes an in-flight quiz generation after refresh instead of showing the picker', async () => {
    setAccessToken('student-token')
    sessionStorage.setItem(
      'uc-study-gen:quiz:course-1',
      JSON.stringify({ materialIds: ['material-1'], force: false, startedAt: Date.now() }),
    )
    let resolveGenerate: ((value: Response) => void) | undefined
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = String(url)
        if (path.includes('/quizzes/generate') && init?.method === 'POST') {
          return new Promise<Response>((resolve) => {
            resolveGenerate = resolve
          })
        }
        if (path.endsWith('/materials')) {
          return json([material])
        }
        return json(course)
      }),
    )

    renderAt('/courses/course-1/quiz', <CourseQuizPage />)

    expect(await screen.findByText('Still preparing your shared quiz…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start quiz' })).not.toBeInTheDocument()

    expect(resolveGenerate).toBeTypeOf('function')
    resolveGenerate!(
      json(
        {
          id: 'quiz-1',
          courseId: 'course-1',
          version: 1,
          availableVersions: 1,
          maxVersions: 3,
          delivery: 'CREATED',
          versions: [{ id: 'quiz-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
          questions: [
            {
              id: 'q-1',
              prompt: 'Resumed question?',
              options: ['Yes', 'No', 'Maybe', 'Later'],
            },
          ],
        },
        201,
      ),
    )

    expect(await screen.findByText('Resumed question?')).toBeInTheDocument()
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
