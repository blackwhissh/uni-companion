import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { CourseNotesPanel } from './CourseNotesPanel.tsx'
import type { StudentNote } from './notes-api.ts'

function renderNotes() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CourseNotesPanel courseId="course-1" />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CourseNotesPanel', () => {
  afterEach(() => {
    cleanup()
    setAccessToken(null)
    vi.unstubAllGlobals()
  })

  it('creates, updates, and deletes a private course note', async () => {
    setAccessToken('student-token')
    let notes: StudentNote[] = []
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (url.endsWith('/api/notes/courses/course-1') && method === 'GET') {
        return json(notes)
      }
      if (url.endsWith('/api/notes/courses/course-1') && method === 'POST') {
        const body = JSON.parse(String(init?.body)) as { title: string; body: string }
        const created: StudentNote = {
          id: 'note-1',
          courseId: 'course-1',
          title: body.title,
          body: body.body,
          createdAt: '2026-11-06T00:00:00Z',
          updatedAt: '2026-11-06T00:00:00Z',
        }
        notes = [created]
        return json(created, 201)
      }
      if (url.endsWith('/api/notes/courses/course-1/note-1') && method === 'PUT') {
        const body = JSON.parse(String(init?.body)) as { title: string; body: string }
        notes = [{ ...notes[0], title: body.title, body: body.body }]
        return json(notes[0])
      }
      if (url.endsWith('/api/notes/courses/course-1/note-1') && method === 'DELETE') {
        notes = []
        return new Response(null, { status: 204 })
      }
      return json({}, 500)
    })
    vi.stubGlobal('fetch', fetchMock)

    renderNotes()

    expect(await screen.findByText('Nothing here yet. Add a thought when something clicks.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Jot something down' }))
    fireEvent.change(screen.getByLabelText('What’s this about?'), { target: { value: 'Raft election timeout' } })
    fireEvent.change(screen.getByLabelText('Your words'), {
      target: { value: 'Randomized 150–300 ms avoids split votes.' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Keep this note' }))

    expect(await screen.findByText('Saved. Just for you.')).toBeInTheDocument()
    expect(screen.getByText('Raft election timeout')).toBeInTheDocument()
    expect(screen.getByText('Randomized 150–300 ms avoids split votes.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('What’s this about?'), { target: { value: 'Raft election timer' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('Raft election timer')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Remove note' }))
    expect(await screen.findByText('Nothing here yet. Add a thought when something clicks.')).toBeInTheDocument()
  })
})

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
