import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

describe('course Q&A page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('hydrates prior turns from Q&A history', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).includes('/history')) {
        return json({
          turns: [
            {
              id: 'turn-1',
              question: 'What is consensus?',
              answer: '## Direct answer\nConsensus keeps replicas consistent. [1]',
              citations: [
                {
                  materialId: 'material-1',
                  title: 'Lecture 1',
                  pageNumber: 1,
                  excerpt: 'Consensus algorithms keep replicas consistent.',
                },
              ],
              insufficient: false,
              latencyMs: 120,
              citedCount: 1,
              followUp: false,
              createdAt: '2026-10-16T10:00:00Z',
            },
          ],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('What is consensus?')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Direct answer' })).toBeInTheDocument()
  })

  it('asks a grounded question and shows the answer with citations', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/rag/query/stream') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          courseId: 'course-1',
          question: 'What is consensus?',
          materialIds: ['material-1'],
          priorQuestion: null,
          priorAnswer: null,
        })
        return sseDone({
          answer: `## Direct answer
Consensus algorithms keep replicas consistent. [1]

## Explanation
From your course materials, consensus keeps replicas consistent.

## Key terms
- **Consensus** â€” keeps replicas consistent [1]
`,
          citations: [
            {
              materialId: 'material-1',
              title: 'Lecture 1',
              sectionHint: 'Chapter 1 â€” Consensus',
              pageNumber: 1,
              excerpt: 'Consensus algorithms keep replicas consistent.',
            },
          ],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('Course Q&A')).toBeInTheDocument()
    expect(await screen.findByText('Choose materials')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Use selected materials' }))
    expect(screen.getByText('Searching 1 selected material.')).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('You asked')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Direct answer' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Explanation' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Key terms' })).toBeInTheDocument()
    expect(screen.getAllByText(/Consensus algorithms keep replicas consistent/).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Source 1:/ })[0]).toHaveAttribute('href', '#qa-source-1')
    expect(screen.getByText('Sources used')).toBeInTheDocument()
    expect(screen.getByText('Lecture 1')).toBeInTheDocument()
    expect(screen.getByText(/Chapter 1 .* Consensus/)).toBeInTheDocument()
    expect(screen.getByText('Page 1')).toBeInTheDocument()
    expect(screen.getByText(/Cited 2. in answer/)).toBeInTheDocument()
    expect(document.getElementById('qa-source-1')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ask a follow-up' })).toBeInTheDocument()
  })

  it('fills a ready-to-use prompt into the question box without asking yet', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    expect(await screen.findByText('Try a prompt')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Summarize chapter 1' }))

    expect(screen.getByDisplayValue('Summarize the first chapter in clear study notes.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/rag/query'))).toBe(false)
  })

  it('can clear and select all Q&A materials', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material, material2])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('0 of 2 selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Use selected materials' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Select all' }))
    expect(screen.getByText('2 of 2 selected')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }))
    expect(screen.getByText('0 of 2 selected')).toBeInTheDocument()
  })

  it('keeps the current Q&A material selection when changing materials', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material, material2])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    fireEvent.click(await screen.findByLabelText(/Lecture 1/))
    fireEvent.click(screen.getByRole('button', { name: 'Use selected materials' }))
    expect(screen.getByText('Searching 1 selected material.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Change materials' }))
    expect(await screen.findByText('1 of 2 selected')).toBeInTheDocument()
    expect(screen.getByLabelText(/Lecture 1/)).toBeChecked()
    expect(screen.getByLabelText(/Lecture 2/)).not.toBeChecked()
  })

  it('labels an answer without citations as no supporting passage found', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/rag/query/stream') && init?.method === 'POST') {
        return sseDone({
          answer: 'The published pages do not cover that question.',
          citations: [],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is the dining philosophers problem?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('No supporting passage found')).toBeInTheDocument()
    expect(screen.getByText(/No supporting passage was found/i)).toBeInTheDocument()
    expect(screen.getByText('The published pages do not cover that question.')).toBeInTheDocument()
    expect(screen.queryByText('Sources used')).not.toBeInTheDocument()
  })

  it('disables Ask after a paste is trimmed with a shorten warning', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/materials')) {
          return json([material])
        }
        return json(course)
      }),
    )

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    const field = await screen.findByLabelText('Question')
    fireEvent.change(field, { target: { value: 'x'.repeat(1990) } })
    fireEvent.paste(field, {
      clipboardData: {
        getData: () => 'y'.repeat(20),
      },
    })

    expect(await screen.findByText(/Paste was trimmed/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled()
  })

  it('shows enroll empty state when the student is not enrolled', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async () => json({ ...course, enrolled: false }))
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    expect(await screen.findByText('Enroll to ask questions')).toBeInTheDocument()
    expect(screen.queryByLabelText('Question')).not.toBeInTheDocument()
  })

  it('trims pasted questions that exceed the character limit', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))

    const field = await screen.findByLabelText('Question')
    fireEvent.paste(field, {
      clipboardData: {
        getData: () => 'x'.repeat(2100),
      },
    })

    expect(await screen.findByText(/Paste was trimmed to 2,000 characters/)).toBeInTheDocument()
    expect((field as HTMLTextAreaElement).value).toHaveLength(2000)
  })

  it('recovers when selected materials are no longer available', async () => {
    setAccessToken('student-token')
    let materialsCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/rag/query/stream') && init?.method === 'POST') {
        return sseError(400, 'Every selected material must be ready, published, and part of this course.')
      }
      if (String(url).endsWith('/materials')) {
        materialsCalls += 1
        if (materialsCalls === 1) {
          return json([material, material2])
        }
        return json([material2])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByLabelText(/Lecture 1/))
    fireEvent.click(screen.getByRole('button', { name: 'Use selected materials' }))
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText(/One selected material is no longer available/i)).toBeInTheDocument()
    expect(await screen.findByText('Choose materials')).toBeInTheDocument()
    expect(screen.getByLabelText(/Lecture 2/)).toBeInTheDocument()
    expect(screen.queryByLabelText(/Lecture 1/)).not.toBeInTheDocument()
  })

  it('retries a failed question from Try again', async () => {
    setAccessToken('student-token')
    let ragCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/rag/query/stream') && init?.method === 'POST') {
        ragCalls += 1
        if (ragCalls === 1) {
          return sseError(502, 'Could not draft an answer from the published materials. Try again shortly.')
        }
        return sseDone({
          answer: '## Direct answer\nConsensus keeps replicas consistent. [1]',
          citations: [
            {
              materialId: 'material-1',
              title: 'Lecture 1',
              pageNumber: 1,
              excerpt: 'Consensus algorithms keep replicas consistent.',
            },
          ],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(
      await screen.findByText('Could not draft an answer from the published materials. Try again shortly.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Direct answer' })).toBeInTheDocument()
    expect(ragCalls).toBe(2)
  })

  it('sends prior turn context when asking a follow-up', async () => {
    setAccessToken('student-token')
    let ragCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/rag/query/stream') && init?.method === 'POST') {
        ragCalls += 1
        const body = JSON.parse(String(init.body))
        if (ragCalls === 1) {
          return sseDone({
            answer: '## Direct answer\nConsensus keeps replicas consistent. [1]',
            citations: [
              {
                materialId: 'material-1',
                title: 'Lecture 1',
                pageNumber: 1,
                excerpt: 'Consensus algorithms keep replicas consistent.',
              },
            ],
          })
        }
        expect(body.priorQuestion).toBe('What is consensus?')
        expect(body.priorAnswer).toContain('Consensus keeps replicas consistent')
        expect(body.question).toBe('Why does that matter?')
        return sseDone({
          answer: '## Direct answer\nIt keeps replicas aligned. [1]',
          citations: [
            {
              materialId: 'material-1',
              title: 'Lecture 1',
              pageNumber: 1,
              excerpt: 'Consensus algorithms keep replicas consistent.',
            },
          ],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      const meta = ragMetaResponse(url)
      if (meta) {
        return meta
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByRole('button', { name: 'Ask a follow-up' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Ask a follow-up' }))
    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Why does that matter?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask follow-up' }))

    expect(await screen.findByText('Follow-up 1')).toBeInTheDocument()
    expect(screen.getByText('Why does that matter?')).toBeInTheDocument()
    expect(ragCalls).toBe(2)
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function ragMetaResponse(url: string): Response | null {
  const href = String(url)
  if (href.includes('/rag/courses/') && href.includes('/history')) {
    return json({ turns: [] })
  }
  if (href.includes('/rag/courses/') && href.includes('/stats')) {
    return json({ totalTurns: 0, avgLatencyMs: 0, citeRate: 0, insufficientRate: 0 })
  }
  return null
}

function sseDone(payload: unknown) {
  const body = [
    'event: status',
    'data: {"phase":"retrieving"}',
    '',
    'event: status',
    'data: {"phase":"generating"}',
    '',
    'event: done',
    `data: ${JSON.stringify(payload)}`,
    '',
  ].join('\n')
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

function sseError(status: number, message: string) {
  const body = ['event: error', `data: ${JSON.stringify({ status, message })}`, ''].join('\n')
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

