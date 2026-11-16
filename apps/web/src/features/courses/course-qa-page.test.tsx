import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { CourseQaPage } from './CourseQaPage.tsx'
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

const material2: Material = {
  ...material,
  id: 'material-2',
  title: 'Lecture 2',
  fileName: 'lecture-2.pdf',
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
            <Route path="/courses/:courseId/qa" element={ui} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function emptySession(overrides: Record<string, unknown> = {}) {
  return {
    id: 'session-1',
    courseId: 'course-1',
    materialIds: ['material-1'],
    language: 'EN',
    messages: [],
    createdAt: '2026-11-16T00:00:00Z',
    updatedAt: '2026-11-16T00:00:00Z',
    ...overrides,
  }
}

describe('course study chat page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('starts a chat, asks a question, and shows a grounded tutor reply', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          courseId: 'course-1',
          materialIds: ['material-1'],
        })
        return json(emptySession())
      }
      if (String(url).includes('/rag/chat/sessions/session-1/messages/stream') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({ question: 'What is consensus?' })
        const session = emptySession({
          messages: [
            {
              id: 'm1',
              role: 'USER',
              content: 'What is consensus?',
              citations: [],
              createdAt: '2026-11-16T00:00:01Z',
            },
            {
              id: 'm2',
              role: 'ASSISTANT',
              content: `## Direct answer
Consensus algorithms keep replicas consistent. [1]

## Explanation
From your course materials, consensus keeps replicas consistent.

## Key terms
- **Consensus** — keeps replicas consistent [1]
`,
              citations: [
                {
                  materialId: 'material-1',
                  title: 'Lecture 1',
                  sectionHint: 'Chapter 1 — Consensus',
                  pageNumber: 1,
                  excerpt: 'Consensus algorithms keep replicas consistent.',
                },
              ],
              createdAt: '2026-11-16T00:00:02Z',
            },
          ],
        })
        return sse([
          { event: 'token', data: { delta: '## Direct answer\n' } },
          { event: 'token', data: { delta: 'Consensus algorithms keep replicas consistent. [1]\n\n' } },
          {
            event: 'token',
            data: {
              delta: '## Explanation\nFrom your course materials, consensus keeps replicas consistent.\n\n## Key terms\n- **Consensus** — keeps replicas consistent [1]\n',
            },
          },
          { event: 'done', data: session },
        ])
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('Study chat')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    expect(await screen.findByText(/Studying from 1 lecture/)).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('Your message'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('What is consensus?')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'The result' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Walk through it' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Remember' })).toBeInTheDocument()
    expect(screen.getByText(/Consensus algorithms keep replicas consistent/)).toBeInTheDocument()
    expect(screen.getByText('Sources used')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show passage' })).toBeInTheDocument()
    expect(screen.getByText('Explain simpler')).toBeInTheDocument()
    expect(screen.getByText('One-sentence result')).toBeInTheDocument()
  })

  it('sends a German-language chat when Deutsch is selected and the chat is restarted', async () => {
    setAccessToken('student-token')
    let sessionPosts = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        sessionPosts += 1
        const body = JSON.parse(String(init.body)) as { language?: string }
        if (sessionPosts === 1) {
          expect(body.language).toBeUndefined()
          return json(emptySession({ language: 'EN' }))
        }
        expect(body.language).toBe('DE')
        return json(emptySession({ id: 'session-de', language: 'DE' }))
      }
      if (String(url).includes('/messages/stream') && init?.method === 'POST') {
        const session = emptySession({
          id: 'session-de',
          language: 'DE',
          messages: [
            {
              id: 'm1',
              role: 'USER',
              content: 'What is consensus?',
              citations: [],
              createdAt: '2026-11-16T00:00:01Z',
            },
            {
              id: 'm2',
              role: 'ASSISTANT',
              content: `## Direkte Antwort
Consensus algorithms keep replicas consistent. [1]

## Erklärung
Aus den veröffentlichten Materialien.

## Schlüsselbegriffe
- **Consensus** — keeps replicas consistent [1]
`,
              citations: [
                {
                  materialId: 'material-1',
                  title: 'Lecture 1',
                  pageNumber: 1,
                  excerpt: 'Consensus algorithms keep replicas consistent.',
                },
              ],
              createdAt: '2026-11-16T00:00:02Z',
            },
          ],
        })
        return sse([{ event: 'done', data: session }])
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    expect(await screen.findByText(/Studying from 1 lecture/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('radio', { name: 'Deutsch' }))
    fireEvent.click(await screen.findByRole('button', { name: 'New chat' }))
    await waitFor(() => expect(sessionPosts).toBe(2))
    fireEvent.change(await screen.findByLabelText('Your message'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByRole('heading', { name: 'Das Ergebnis' })).toBeInTheDocument()
  })

  it('fills a starter into the composer without sending yet when clicked after focus', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        return json(emptySession())
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    expect(await screen.findByText('Ask for a result')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Give me the result' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Walk through the steps' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Your message'), {
      target: { value: 'What are the most important key terms here, with short definitions I can memorize?' },
    })
    expect(
      screen.getByDisplayValue('What are the most important key terms here, with short definitions I can memorize?'),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/messages'))).toBe(false)
  })

  it('can clear and select all lectures', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material, material2])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('0 of 2 selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start studying' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Select all' }))
    expect(screen.getByText('2 of 2 selected')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }))
    expect(screen.getByText('0 of 2 selected')).toBeInTheDocument()
  })

  it('keeps the current lecture selection when changing lectures', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        return json(emptySession())
      }
      if (String(url).endsWith('/materials')) {
        return json([material, material2])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    fireEvent.click(await screen.findByLabelText(/Lecture 1/))
    fireEvent.click(screen.getByRole('button', { name: 'Start studying' }))
    expect(await screen.findByText(/Studying from 1 lecture/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Change lectures' }))
    expect(await screen.findByText('1 of 2 selected')).toBeInTheDocument()
    expect(screen.getByLabelText(/Lecture 1/)).toBeChecked()
    expect(screen.getByLabelText(/Lecture 2/)).not.toBeChecked()
  })

  it('labels an answer without citations as unverified', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        return json(emptySession())
      }
      if (String(url).includes('/messages/stream') && init?.method === 'POST') {
        const session = emptySession({
          messages: [
            {
              id: 'm1',
              role: 'USER',
              content: 'What is the dining philosophers problem?',
              citations: [],
              createdAt: '2026-11-16T00:00:01Z',
            },
            {
              id: 'm2',
              role: 'ASSISTANT',
              content: 'The published pages do not cover that question.',
              citations: [],
              createdAt: '2026-11-16T00:00:02Z',
            },
          ],
        })
        return sse([{ event: 'done', data: session }])
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    fireEvent.change(await screen.findByLabelText('Your message'), {
      target: { value: 'What is the dining philosophers problem?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/No supporting passage was found/i)).toBeInTheDocument()
    expect(screen.getByText('The published pages do not cover that question.')).toBeInTheDocument()
    expect(screen.queryByText('Sources used')).not.toBeInTheDocument()
  })

  it('disables Ask after a paste is trimmed with a shorten warning', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
          return json(emptySession())
        }
        if (String(url).endsWith('/materials')) {
          return json([material])
        }
        return json(course)
      }),
    )

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    const field = await screen.findByLabelText('Your message')
    fireEvent.change(field, { target: { value: 'x'.repeat(1990) } })
    fireEvent.paste(field, {
      clipboardData: {
        getData: () => 'y'.repeat(20),
      },
    })

    expect(await screen.findByText(/Paste was trimmed/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled()
  })

  it('retries a failed message from Try again', async () => {
    setAccessToken('student-token')
    let messageCalls = 0
    const successSession = emptySession({
      messages: [
        {
          id: 'm1',
          role: 'USER',
          content: 'What is consensus?',
          citations: [],
          createdAt: '2026-11-16T00:00:01Z',
        },
        {
          id: 'm2',
          role: 'ASSISTANT',
          content: '## Direct answer\nConsensus keeps replicas consistent. [1]',
          citations: [
            {
              materialId: 'material-1',
              title: 'Lecture 1',
              pageNumber: 1,
              excerpt: 'Consensus algorithms keep replicas consistent.',
            },
          ],
          createdAt: '2026-11-16T00:00:02Z',
        },
      ],
    })
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/chat/sessions') && init?.method === 'POST') {
        return json(emptySession())
      }
      if (String(url).includes('/messages/stream') && init?.method === 'POST') {
        messageCalls += 1
        if (messageCalls === 1) {
          return sse([
            {
              event: 'error',
              data: {
                message: 'Could not draft an answer from the published materials. Try again shortly.',
                status: 502,
              },
            },
          ])
        }
        return sse([{ event: 'done', data: successSession }])
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Start studying' }))
    fireEvent.change(await screen.findByLabelText('Your message'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/Could not draft an answer/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'The result' })).toBeInTheDocument()
    expect(messageCalls).toBe(2)
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function sse(events: Array<{ event: string; data: unknown }>) {
  const body = events.map((item) => `event: ${item.event}\ndata: ${JSON.stringify(item.data)}\n\n`).join('')
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}
