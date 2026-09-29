import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  Button,
  ButtonLink,
  EmptyState,
  Page,
  PageHeader,
  StatusPill,
} from '../../shared/ui/ui.tsx'
import { enrollInCourse, listCourses, unenrollFromCourse } from './course-api.ts'
import type { Course } from './course-api.ts'

export function CoursesPage() {
  const queryClient = useQueryClient()
  const courses = useQuery({ queryKey: ['courses'], queryFn: listCourses })
  const [error, setError] = useState<string | null>(null)
  const enroll = useMutation({
    mutationFn: enrollInCourse,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not enroll.')
    },
  })
  const unenroll = useMutation({
    mutationFn: unenrollFromCourse,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['courses'] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not unenroll.')
    },
  })

  const enrolled = courses.data?.filter((course) => course.enrolled) ?? []
  const available = courses.data?.filter((course) => !course.enrolled) ?? []

  return (
    <Page>
      <PageHeader
        eyebrow="Study workspace"
        title="Your courses"
        description="Enroll in published modules, then open a course to practice with materials and peers."
      />

      {courses.isLoading ? (
        <p className="mt-8 animate-pulse-soft text-muted">Loading courses…</p>
      ) : null}
      {error ? (
        <div className="mt-6">
          <Alert>{error}</Alert>
        </div>
      ) : null}

      {courses.data?.length === 0 ? (
        <EmptyState
          title="No courses yet."
          description="When a course admin publishes a module, it will show up here so you can enroll and study."
        />
      ) : null}

      {enrolled.length > 0 ? (
        <section className="mt-10 animate-rise-delay">
          <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted">Joined</h2>
          <ul className="mt-4 flex flex-col gap-3">
            {enrolled.map((course) => (
              <CourseRow
                key={course.id}
                course={course}
                enrolled
                pending={enroll.isPending || unenroll.isPending}
                onEnroll={() => enroll.mutate(course.id)}
                onUnenroll={() => unenroll.mutate(course.id)}
              />
            ))}
          </ul>
        </section>
      ) : null}

      {available.length > 0 ? (
        <section className="mt-10 animate-rise-delay-2">
          <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted">Available to join</h2>
          <ul className="mt-4 flex flex-col gap-3">
            {available.map((course) => (
              <CourseRow
                key={course.id}
                course={course}
                enrolled={false}
                pending={enroll.isPending || unenroll.isPending}
                onEnroll={() => enroll.mutate(course.id)}
                onUnenroll={() => unenroll.mutate(course.id)}
              />
            ))}
          </ul>
        </section>
      ) : null}
    </Page>
  )
}

function CourseRow({
  course,
  enrolled,
  pending,
  onEnroll,
  onUnenroll,
}: {
  course: Course
  enrolled: boolean
  pending: boolean
  onEnroll: () => void
  onUnenroll: () => void
}) {
  return (
    <li className="surface-panel group relative cursor-pointer rounded-2xl px-5 py-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35">
      <Link
        to={`/courses/${course.id}`}
        aria-label={`Open ${course.title}`}
        className="absolute inset-0 rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
      />
      <div className="pointer-events-none relative z-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-display text-lg font-semibold text-ink">{course.title}</p>
            {enrolled ? <StatusPill tone="ok">Enrolled</StatusPill> : null}
          </div>
          <p className="mt-1 text-sm text-muted">
            {course.code} · {course.term}
          </p>
        </div>
        <div className="pointer-events-auto flex flex-wrap items-center gap-2">
          <ButtonLink to={`/courses/${course.id}`} variant={enrolled ? 'primary' : 'secondary'}>
            Open
          </ButtonLink>
          {enrolled ? (
            <Button type="button" variant="warn" disabled={pending} onClick={onUnenroll}>
              Unenroll
            </Button>
          ) : (
            <Button type="button" variant="success" disabled={pending} onClick={onEnroll}>
              Enroll
            </Button>
          )}
        </div>
      </div>
    </li>
  )
}
