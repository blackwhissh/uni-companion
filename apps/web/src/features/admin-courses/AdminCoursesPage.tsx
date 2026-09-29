import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { Field, SelectField } from '../auth/AuthForm.tsx'
import { useAuth } from '../auth/use-auth.ts'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  Button,
  EmptyState,
  Page,
  PageHeader,
  Panel,
  SectionLabel,
  StatusPill,
} from '../../shared/ui/ui.tsx'
import { createCourse, deleteCourse, listCourses, publishCourse, unpublishCourse } from '../courses/course-api.ts'

const TERM_YEARS = ['2025', '2026', '2027', '2028'] as const
const TERM_SEASONS = [
  { value: 'WS', label: 'Winter semester (WS)' },
  { value: 'SS', label: 'Summer semester (SS)' },
] as const

export function AdminCoursesPage() {
  const { user } = useAuth()
  const platformAdmin = user?.roles.includes('ADMIN') === true
  const queryClient = useQueryClient()
  const courses = useQuery({ queryKey: ['courses'], queryFn: listCourses })
  const [title, setTitle] = useState('')
  const [code, setCode] = useState('')
  const [termYear, setTermYear] = useState('')
  const [termSeason, setTermSeason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const term = termYear && termSeason ? `${termYear}${termSeason}` : ''

  function resetForm() {
    setTitle('')
    setCode('')
    setTermYear('')
    setTermSeason('')
    setError(null)
  }

  const create = useMutation({
    mutationFn: () => createCourse({ title, code, term }),
    onSuccess: async () => {
      resetForm()
      setFormOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not create the course.')
    },
  })

  const publish = useMutation({
    mutationFn: publishCourse,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not publish the course.')
    },
  })

  const unpublish = useMutation({
    mutationFn: unpublishCourse,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not unpublish the course.')
    },
  })

  const remove = useMutation({
    mutationFn: deleteCourse,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not delete the course.')
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!term) {
      setError('Choose a year and semester.')
      return
    }
    create.mutate()
  }

  function closeForm() {
    resetForm()
    setFormOpen(false)
  }

  const visibilityPending = publish.isPending || unpublish.isPending

  return (
    <Page>
      <PageHeader
        eyebrow="Admin"
        title="Course console"
        description="Create modules, publish them for enrollment, then upload lecture PDFs students can study from. Professors manage their own courses; platform admins can unpublish or delete any course."
        actions={
          formOpen ? null : (
            <Button type="button" onClick={() => setFormOpen(true)}>
              Create course
            </Button>
          )
        }
      />

      {formOpen ? (
        <Panel className="mt-8 animate-rise">
          <div className="flex items-center justify-between gap-3">
            <SectionLabel>New course</SectionLabel>
            <Button type="button" variant="ghost" onClick={closeForm}>
              Cancel
            </Button>
          </div>
          <form className="mt-4 grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="sm:col-span-2">
              <Field label="Title" type="text" value={title} onChange={setTitle} autoComplete="off" />
            </div>
            <Field label="Code" type="text" value={code} onChange={setCode} autoComplete="off" />
            <SelectField
              label="Year"
              value={termYear}
              onChange={setTermYear}
              placeholder="Select year"
              options={TERM_YEARS.map((year) => ({ value: year, label: year }))}
            />
            <SelectField
              label="Semester"
              value={termSeason}
              onChange={setTermSeason}
              placeholder="Select semester"
              options={TERM_SEASONS.map((season) => ({ value: season.value, label: season.label }))}
              hint={term ? `Term code: ${term}` : 'Term is saved as year + WS/SS (e.g. 2026WS).'}
            />
            {error ? (
              <div className="sm:col-span-2">
                <Alert>{error}</Alert>
              </div>
            ) : null}
            <div className="sm:col-span-2 flex flex-wrap gap-2">
              <Button type="submit" disabled={create.isPending}>
                Save course
              </Button>
              <Button type="button" variant="secondary" onClick={closeForm}>
                Cancel
              </Button>
            </div>
          </form>
        </Panel>
      ) : null}

      {courses.data?.length === 0 ? (
        <EmptyState
          title="No courses yet."
          description="Use Create course to add your first module. Students only see it after you publish."
        />
      ) : null}

      {courses.data && courses.data.length > 0 ? (
        <section className="mt-10 animate-rise-delay">
          <SectionLabel>Your courses</SectionLabel>
          <ul className="mt-4 flex flex-col gap-3">
            {courses.data.map((course) => {
              const published = course.visibility === 'PUBLISHED'
              const materialsTo = `/admin/courses/${course.id}/materials`
              const canManage = course.owned || platformAdmin
              return (
                <li
                  key={course.id}
                  className="surface-panel group relative cursor-pointer rounded-2xl px-5 py-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35"
                >
                  <Link
                    to={materialsTo}
                    aria-label={`Open materials for ${course.title}`}
                    className="absolute inset-0 rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
                  />
                  <div className="pointer-events-none relative z-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-display text-lg font-semibold text-ink">{course.title}</p>
                        <StatusPill tone={published ? 'ok' : 'warn'}>
                          {published ? 'Published' : 'Unpublished'}
                        </StatusPill>
                      </div>
                      <p className="mt-1 text-sm text-muted">
                        {course.code} · {course.term} · {published ? 'Published' : 'Unpublished'}
                      </p>
                    </div>
                    <div className="pointer-events-auto flex flex-wrap gap-2">
                      <Link
                        to={materialsTo}
                        className="inline-flex cursor-pointer items-center justify-center rounded-lg border border-ink/20 bg-white/70 px-3.5 py-2 text-sm font-medium text-ink transition hover:border-ink/40 hover:bg-white"
                      >
                        Materials
                      </Link>
                      {canManage && !published ? (
                        <Button
                          type="button"
                          variant="success"
                          disabled={visibilityPending}
                          onClick={() => publish.mutate(course.id)}
                        >
                          Publish
                        </Button>
                      ) : null}
                      {canManage && published ? (
                        <Button
                          type="button"
                          variant="warn"
                          disabled={visibilityPending}
                          onClick={() => unpublish.mutate(course.id)}
                        >
                          Unpublish
                        </Button>
                      ) : null}
                      {canManage ? (
                        <Button
                          type="button"
                          variant="danger"
                          disabled={remove.isPending}
                          onClick={() => remove.mutate(course.id)}
                        >
                          Delete
                        </Button>
                      ) : null}
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}
    </Page>
  )
}
