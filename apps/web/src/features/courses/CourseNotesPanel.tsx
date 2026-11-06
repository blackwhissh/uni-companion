import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, ConfirmDialog, Panel, SectionLabel, StatusPill } from '../../shared/ui/ui.tsx'
import { createCourseNote, deleteCourseNote, listCourseNotes, updateCourseNote } from './notes-api.ts'
import type { StudentNote } from './notes-api.ts'

export function CourseNotesPanel({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const notes = useQuery({
    queryKey: ['course-notes', courseId],
    queryFn: () => listCourseNotes(courseId),
  })
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<StudentNote | null>(null)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<StudentNote | null>(null)

  function openNew() {
    setEditing(null)
    setTitle('')
    setBody('')
    setError(null)
    setNotice(null)
    setFormOpen(true)
  }

  function openEdit(note: StudentNote) {
    setEditing(note)
    setTitle(note.title)
    setBody(note.body)
    setError(null)
    setNotice(null)
    setFormOpen(true)
  }

  function closeForm() {
    setEditing(null)
    setTitle('')
    setBody('')
    setError(null)
    setFormOpen(false)
  }

  const save = useMutation({
    mutationFn: () =>
      editing
        ? updateCourseNote(courseId, editing.id, { title, body })
        : createCourseNote(courseId, { title, body }),
    onSuccess: async () => {
      const wasEditing = editing !== null
      closeForm()
      setNotice(wasEditing ? 'Got it — your note is updated.' : 'Saved. Just for you.')
      await queryClient.invalidateQueries({ queryKey: ['course-notes', courseId] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not save that note. Try again.')
    },
  })

  const remove = useMutation({
    mutationFn: (noteId: string) => deleteCourseNote(courseId, noteId),
    onSuccess: async () => {
      setError(null)
      setNotice('That note is gone.')
      await queryClient.invalidateQueries({ queryKey: ['course-notes', courseId] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not remove that note. Try again.')
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!title.trim() || !body.trim()) {
      setError('Give your note a short title and a few lines of text.')
      return
    }
    save.mutate()
  }

  return (
    <Panel className="mt-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel>Your notes</SectionLabel>
          <StatusPill tone="neutral">Just for you</StatusPill>
        </div>
        {!formOpen ? (
          <Button type="button" variant="secondary" onClick={openNew}>
            Jot something down
          </Button>
        ) : null}
      </div>
      <p className="mt-2 text-sm leading-relaxed text-muted">
        Scribble reminders, exam cues, or a paraphrase in your own words. Classmates and course admins never see these —
        and they never feed the lecture Q&A.
      </p>

      {notice ? (
        <p className="mt-3 text-sm font-medium text-ok" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <div className="mt-3">
          <Alert>{error}</Alert>
        </div>
      ) : null}

      {formOpen ? (
        <form className="mt-4 flex flex-col gap-3 rounded-xl border border-line/80 bg-mist/40 p-4" onSubmit={onSubmit}>
          <label htmlFor="private-note-title" className="text-sm font-medium text-ink-soft">
            What’s this about?
          </label>
          <input
            id="private-note-title"
            type="text"
            maxLength={160}
            value={title}
            placeholder="e.g. Raft election timeout"
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-lg border border-ink/20 bg-white px-3 py-2 text-sm text-ink placeholder:text-muted"
          />
          <label htmlFor="private-note-body" className="text-sm font-medium text-ink-soft">
            Your words
          </label>
          <textarea
            id="private-note-body"
            rows={4}
            maxLength={8000}
            value={body}
            placeholder="Write it the way you’d explain it to yourself later…"
            onChange={(event) => setBody(event.target.value)}
            className="w-full rounded-lg border border-ink/20 bg-white px-3 py-2 text-sm text-ink placeholder:text-muted"
          />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Keep this note'}
            </Button>
            <Button type="button" variant="ghost" disabled={save.isPending} onClick={closeForm}>
              Never mind
            </Button>
          </div>
        </form>
      ) : null}

      {notes.isLoading ? (
        <p className="mt-4 text-sm text-muted" role="status">
          Loading your notes…
        </p>
      ) : null}

      {!notes.isLoading && (notes.data?.length ?? 0) === 0 && !formOpen ? (
        <p className="mt-4 text-sm text-muted">Nothing here yet. Add a thought when something clicks.</p>
      ) : null}

      {(notes.data?.length ?? 0) > 0 ? (
        <ul className="mt-4 flex flex-col gap-3">
          {(notes.data ?? []).map((note) => (
            <li key={note.id} className="rounded-xl border border-line/80 px-4 py-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <p className="font-medium text-ink">{note.title}</p>
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="ghost" onClick={() => openEdit(note)}>
                    Edit
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={remove.isPending}
                    onClick={() => setDeleteTarget(note)}
                  >
                    Remove
                  </Button>
                </div>
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-ink-soft">{note.body}</p>
            </li>
          ))}
        </ul>
      ) : null}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Remove this note?"
        body={
          deleteTarget
            ? `“${deleteTarget.title}” will disappear from your list. You can’t bring it back.`
            : ''
        }
        confirmLabel="Remove note"
        danger
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget) {
            remove.mutate(deleteTarget.id)
          }
          setDeleteTarget(null)
        }}
      />
    </Panel>
  )
}
