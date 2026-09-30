import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
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

function renderAt(path: string, ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/courses/:courseId/qa" element={ui} />
        </Routes>
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

  it('asks a grounded question and shows the answer with citations', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/query') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          courseId: 'course-1',
          question: 'What is consensus?',
          materialIds: ['material-1'],
        })
        return json({
          answer: `## Direct answer
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
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
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
    expect(screen.getByText('Chapter 1 — Consensus')).toBeInTheDocument()
    expect(screen.getByText('Page 1')).toBeInTheDocument()
    expect(screen.getByText(/Cited 2× in answer/)).toBeInTheDocument()
    expect(document.getElementById('qa-source-1')).toBeTruthy()
  })

  it('fills a ready-to-use prompt into the question box without asking yet', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/materials')) {
        return json([material])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    expect(await screen.findByText('Try a prompt')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Summarize chapter 1' }))

    expect(screen.getByDisplayValue('Summarize the first chapter in clear study notes.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/rag/query'))).toBe(false)
  })

    it('can clear and select all Q&A materials', async () => {
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
      if (String(url).endsWith('/rag/query') && init?.method === 'POST') {
        return json({
          answer: 'The published pages do not cover that question.',
          citations: [],
        })
      }
      if (String(url).endsWith('/materials')) {
        return json([material])
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

  it('retries a failed question from Try again', async () => {
    setAccessToken('student-token')
    let ragCalls = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith('/rag/query') && init?.method === 'POST') {
        ragCalls += 1
        if (ragCalls === 1) {
          return json({ code: 'BAD_GATEWAY', message: 'Could not draft an answer from the published materials. Try again shortly.' }, 502)
        }
        return json({
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
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/qa', <CourseQaPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Use selected materials' }))
    fireEvent.change(await screen.findByLabelText('Question'), {
      target: { value: 'What is consensus?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(await screen.findByText('Could not draft an answer from the published materials. Try again shortly.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Direct answer' })).toBeInTheDocument()
    expect(ragCalls).toBe(2)
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
