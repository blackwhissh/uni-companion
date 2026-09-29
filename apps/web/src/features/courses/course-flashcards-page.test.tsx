import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { CourseFlashcardsPage } from './CourseFlashcardsPage.tsx'
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

function renderAt(path: string, ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/courses/:courseId/flashcards" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('course flashcards page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('generates a deck and flips a card', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (path.includes('/flashcards/generate') && init?.method === 'POST') {
        return json(
          {
            id: 'deck-1',
            courseId: 'course-1',
            version: 1,
            availableVersions: 1,
            maxVersions: 3,
            delivery: 'CREATED',
            versions: [{ id: 'deck-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
            cards: [{ id: 'card-1', front: 'What is consensus?', back: 'Keeping replicas consistent.' }],
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

    renderAt('/courses/course-1/flashcards', <CourseFlashcardsPage />)

    expect(await screen.findByText('Flashcards')).toBeInTheDocument()
    expect(await screen.findByText('Choose materials')).toBeInTheDocument()
    expect(screen.getByText('Lecture 1')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Start flashcards' }))

    expect(await screen.findByText('What is consensus?')).toBeInTheDocument()
    expect(screen.getByText('A new shared deck was created for this material selection.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Deck 1' })).toBeDisabled()
    const generateCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url).includes('/flashcards/generate') && init?.method === 'POST',
    )
    expect(JSON.parse(String(generateCall?.[1]?.body))).toEqual({ materialIds: ['material-1'] })
    fireEvent.click(screen.getByRole('button', { name: 'Show answer' }))
    expect(await screen.findByText('Keeping replicas consistent.')).toBeInTheDocument()
  })

  it('shows a working state while generating', async () => {
    setAccessToken('student-token')
    let resolveGenerate!: (value: Response) => void
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (path.includes('/flashcards/generate') && init?.method === 'POST') {
        return new Promise<Response>((resolve) => {
          resolveGenerate = resolve
        })
      }
      if (path.endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/flashcards', <CourseFlashcardsPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start flashcards' }))

    expect(await screen.findByText('Working')).toBeInTheDocument()
    expect(screen.getByText(/Preparing a shared deck/)).toBeInTheDocument()

    resolveGenerate(
      json(
        {
          id: 'deck-1',
          courseId: 'course-1',
          version: 1,
          availableVersions: 1,
          maxVersions: 3,
          delivery: 'CREATED',
          versions: [{ id: 'deck-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
          cards: [{ id: 'card-1', front: 'What is consensus?', back: 'Keeping replicas consistent.' }],
        },
        201,
      ),
    )
    expect(await screen.findByText('What is consensus?')).toBeInTheDocument()
  })

  it('lets the student retry after generation is still running', async () => {
    setAccessToken('student-token')
    let generateCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const path = String(url)
      if (path.includes('/flashcards/generate') && init?.method === 'POST') {
        generateCalls += 1
        if (generateCalls === 1) {
          return json({ code: 'UNAVAILABLE', message: 'Generation is taking longer than expected.' }, 503)
        }
        return json(
          {
            id: 'deck-1',
            courseId: 'course-1',
            version: 1,
            availableVersions: 1,
            maxVersions: 3,
            delivery: 'CREATED',
            versions: [{ id: 'deck-1', version: 1, createdAt: '2026-09-28T20:00:00Z', current: true }],
            cards: [{ id: 'card-1', front: 'What is consensus?', back: 'Keeping replicas consistent.' }],
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

    renderAt('/courses/course-1/flashcards', <CourseFlashcardsPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start flashcards' }))

    expect(await screen.findByText('Generation is still running. Wait a moment, then try again.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('What is consensus?')).toBeInTheDocument()
    expect(generateCalls).toBe(2)
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
