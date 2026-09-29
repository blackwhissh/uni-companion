import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { AdminMaterialsPage } from '../admin-courses/AdminMaterialsPage.tsx'
import { AuthProvider } from '../auth/AuthProvider.tsx'
import type { User } from '../auth/types.ts'
import { CourseHomePage } from './CourseHomePage.tsx'
import type { Course } from './course-api.ts'
import type { Material } from './material-api.ts'

const course: Course = {
  id: 'course-1',
  title: 'Distributed Systems',
  code: 'CISS',
  term: '2026WS',
  visibility: 'PUBLISHED',
  enrolled: false,
  owned: true,
}

const ready: Material = {
  id: 'material-1',
  courseId: 'course-1',
  title: 'Lecture 1',
  fileName: 'lecture.pdf',
  visibility: 'UNPUBLISHED',
  processingStatus: 'READY',
}

const professor: User = {
  id: 'admin-1',
  email: 'admin@uni-companion.test',
  displayName: 'Course Admin',
  interests: null,
  roles: ['COURSE_ADMIN'],
  createdAt: '2026-01-01T00:00:00Z',
}

function renderAt(path: string, ui: ReactNode, user: User | null = professor) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider initialUser={user}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/admin/courses/:courseId/materials" element={ui} />
            <Route path="/courses/:courseId" element={ui} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('material pages', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  function matchStatus(optedIn = false, activeCount = 0) {
    return json({ optedIn, activeCount })
  }

  it('explains that peer matching is not available yet', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/materials')) {
          return json([{ ...ready, visibility: 'PUBLISHED' }])
        }
        return json({ ...course, enrolled: true })
      }),
    )

    renderAt('/courses/course-1', <CourseHomePage />)

    expect(await screen.findByText(/Study-partner matching is not available yet/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Find study partners' })).not.toBeInTheDocument()
  })

  it('lets an admin upload a PDF and publish it', async () => {
    setAccessToken('admin-token')
    let materials: Material[] = []
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (method === 'POST') {
        const body = init?.body as FormData
        expect(body.get('title')).toBe('Lecture 1')
        expect(body.get('file')).toBeInstanceOf(File)
        materials = [ready]
        return json({ ...ready, processingStatus: 'UPLOADED' }, 201)
      }
      if (method === 'PATCH') {
        materials = [{ ...ready, visibility: 'PUBLISHED' }]
        return json(materials[0])
      }
      if (method === 'DELETE') {
        materials = []
        return new Response(null, { status: 204 })
      }
      if (String(url).endsWith('/materials')) {
        return json(materials)
      }
      return json(course)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderAt('/admin/courses/course-1/materials', <AdminMaterialsPage />)

    expect(await screen.findByText('No materials yet.')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Upload PDF' }))
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Lecture 1' } })
    const file = new File(['%PDF'], 'lecture.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('PDF'), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Save PDF' }))

    expect(await screen.findByText('lecture.pdf · Indexed · Unpublished')).toBeInTheDocument()
    window.confirm = vi.fn(() => true)
    fireEvent.click(screen.getByRole('button', { name: 'Publish' }))
    expect(await screen.findByText('lecture.pdf · Indexed · Published')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('No materials yet.')).toBeInTheDocument()
  })

  it('shows an enrolled student only the materials the API returns', async () => {
    setAccessToken('student-token')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (String(url).endsWith('/active-count')) {
          return matchStatus()
        }
        if (String(url).endsWith('/materials')) {
          return json([{ ...ready, visibility: 'PUBLISHED' }])
        }
        return json({ ...course, enrolled: true })
      }),
    )

    renderAt('/courses/course-1', <CourseHomePage />)

    expect(await screen.findByText('Lecture 1')).toBeInTheDocument()
    expect(screen.getByText('Enrolled')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Upload PDF' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Unpublished/)).not.toBeInTheDocument()
  })

  it('downloads the PDF when the student clicks Download', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/file')) {
        return {
          ok: true,
          status: 200,
          blob: async () => new Blob(['%PDF'], { type: 'application/pdf' }),
          json: async () => null,
        } as Response
      }
      if (String(url).endsWith('/active-count')) {
        return matchStatus()
      }
      if (String(url).endsWith('/materials')) {
        return json([{ ...ready, visibility: 'PUBLISHED' }])
      }
      return json({ ...course, enrolled: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:lecture')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderAt('/courses/course-1', <CourseHomePage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Download Lecture 1' }))

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8082/api/materials/material-1/file',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('opens a PDF preview when the student clicks the material', async () => {
    setAccessToken('student-token')
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).endsWith('/file')) {
        return {
          ok: true,
          status: 200,
          blob: async () => new Blob(['%PDF'], { type: 'application/pdf' }),
          json: async () => null,
        } as Response
      }
      if (String(url).endsWith('/active-count')) {
        return matchStatus()
      }
      if (String(url).endsWith('/materials')) {
        return json([{ ...ready, visibility: 'PUBLISHED' }])
      }
      return json({ ...course, enrolled: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:lecture-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    renderAt('/courses/course-1', <CourseHomePage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Preview Lecture 1' }))

    expect(await screen.findByRole('dialog', { name: 'Preview Lecture 1' })).toBeInTheDocument()
    expect(screen.getByTitle('Lecture 1')).toHaveAttribute('src', 'blob:lecture-preview')
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8082/api/materials/material-1/file',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
  })

  it('offers enroll when the student cannot list materials yet', async () => {
    setAccessToken('student-token')
    let enrolled = false
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
          enrolled = true
          return json({ courseId: 'course-1' }, 201)
        }
        if (String(url).endsWith('/active-count')) {
          return matchStatus()
        }
        if (String(url).endsWith('/materials')) {
          return json([{ ...ready, visibility: 'PUBLISHED' }])
        }
        return json({ ...course, enrolled, owned: false })
      }),
    )

    renderAt('/courses/course-1', <CourseHomePage />, {
      id: 'student-1',
      email: 'student@uni-companion.test',
      displayName: 'Student',
      interests: null,
      roles: ['STUDENT'],
      createdAt: '2026-01-01T00:00:00Z',
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Enroll' }))
    expect(await screen.findByText('Lecture 1')).toBeInTheDocument()
  })

  it('hides materials after the student unenrolls', async () => {
    setAccessToken('student-token')
    let enrolled = true
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          enrolled = false
          return new Response(null, { status: 204 })
        }
        if (String(url).endsWith('/active-count')) {
          return matchStatus()
        }
        if (String(url).endsWith('/materials')) {
          return enrolled ? json([{ ...ready, visibility: 'PUBLISHED' }]) : json([], 403)
        }
        return json({ ...course, enrolled, owned: false })
      }),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderAt('/courses/course-1', <CourseHomePage />, {
      id: 'student-1',
      email: 'student@uni-companion.test',
      displayName: 'Student',
      interests: null,
      roles: ['STUDENT'],
      createdAt: '2026-01-01T00:00:00Z',
    })

    expect(await screen.findByText('Lecture 1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Unenroll from course' }))
    expect(await screen.findByText('You are not enrolled yet')).toBeInTheDocument()
    expect(screen.queryByText('Lecture 1')).not.toBeInTheDocument()
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
