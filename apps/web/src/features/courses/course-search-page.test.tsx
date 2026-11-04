import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import { CourseSearchPage } from './CourseSearchPage.tsx'
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

const sharding: Material = {
  id: 'material-shard',
  courseId: 'course-1',
  title: 'Sharding',
  fileName: 'sharding.pdf',
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
            <Route path="/courses/:courseId/search" element={ui} />
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

describe('course search page', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('shows a search hit and a lecture summary without another lecture', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      if (target.endsWith('/rag/search') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          courseId: 'course-1',
          query: 'partitions data',
        })
        return json({
          hits: [
            {
              materialId: 'material-shard',
              title: 'Sharding',
              pageNumber: 2,
              excerpt: 'Sharding partitions data across nodes.',
            },
          ],
        })
      }
      if (target.endsWith('/rag/summary') && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          courseId: 'course-1',
          materialId: 'material-shard',
        })
        return json({
          answer: 'Short summary of Sharding. Page 2: Sharding partitions data across nodes.',
          citations: [
            {
              materialId: 'material-shard',
              title: 'Sharding',
              pageNumber: 2,
              excerpt: 'Sharding partitions data across nodes.',
            },
          ],
        })
      }
      if (target.endsWith('/materials')) {
        return json([sharding])
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/courses/course-1/search', <CourseSearchPage />)

    expect(await screen.findByRole('heading', { name: 'Search and summaries' })).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('Search published lectures'), {
      target: { value: 'partitions data' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('Sharding partitions data across nodes.')).toBeInTheDocument()
    expect(screen.queryByText(/student@example.com/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByText(/Short summary of Sharding/)).toBeInTheDocument()
    expect(screen.queryByText(/keep replicas consistent/)).not.toBeInTheDocument()
  })
})
