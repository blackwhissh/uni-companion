import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  BackLink,
  ButtonLink,
  EmptyState,
  Page,
  PageHeader,
  SectionLabel,
} from '../../shared/ui/ui.tsx'
import { getCourse } from './course-api.ts'
import { getCourseProgress } from './study-api.ts'
import type { WeakTopic } from './study-api.ts'

export function CourseProgressPage() {
  const { courseId = '' } = useParams()
  const course = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => getCourse(courseId),
    retry: false,
    refetchOnMount: 'always',
  })
  const enrolled = course.data?.enrolled === true
  const progress = useQuery({
    queryKey: ['progress', courseId],
    queryFn: () => getCourseProgress(courseId),
    enabled: enrolled,
    retry: false,
  })
  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const courseLoading = course.isLoading && course.data == null
  const hasAttempts = (progress.data?.attemptCount ?? 0) > 0

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>
        {course.data?.title ?? (courseLoading ? 'Loading course…' : 'Course')}
      </BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Progress'}
          title="Study progress"
          description="Scores come from quizzes you submitted. A question stays on the gap list until your latest answer for that prompt is correct."
          actions={
            enrolled ? (
              <>
                <ButtonLink to={`/courses/${courseId}/plan`} variant="secondary">
                  Study plan
                </ButtonLink>
                <ButtonLink to={`/courses/${courseId}/quiz`} variant="secondary">
                  Open quiz
                </ButtonLink>
              </>
            ) : null
          }
        />
      </div>

      {courseLoading ? (
        <p className="mt-6 animate-pulse-soft text-muted" role="status">
          Loading progress…
        </p>
      ) : null}
      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}
      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Enroll to see progress"
          description="Quiz scores and missed questions are available after you join this course."
        />
      ) : null}
      {progress.isError ? (
        <div className="mt-6">
          <Alert>Could not load your quiz progress.</Alert>
        </div>
      ) : null}
      {enrolled && progress.isLoading ? (
        <p className="mt-6 animate-pulse-soft text-muted" role="status">
          Loading progress…
        </p>
      ) : null}
      {enrolled && progress.data && !hasAttempts ? (
        <EmptyState
          title="No quizzes submitted yet"
          description="Take a quiz on the published materials. Your score and any missed questions will show up here."
          action={
            <ButtonLink to={`/courses/${courseId}/quiz`} variant="secondary">
              Take a quiz
            </ButtonLink>
          }
        />
      ) : null}
      {enrolled && progress.data && hasAttempts ? (
        <>
          <section className="mt-8 grid gap-3 sm:grid-cols-3">
            <ScoreCard label="Latest" score={progress.data.latestScore} total={progress.data.latestTotal} />
            <ScoreCard label="Best" score={progress.data.bestScore} total={progress.data.bestTotal} />
            <article className="surface-panel rounded-2xl p-4">
              <p className="text-sm font-medium text-muted">Attempts</p>
              <p className="mt-2 font-display text-2xl font-semibold text-ink">{progress.data.attemptCount}</p>
            </article>
          </section>

          <section className="mt-10">
            <SectionLabel>Questions to revisit</SectionLabel>
            {progress.data.weakTopics.length === 0 ? (
              <p className="mt-3 text-sm leading-relaxed text-muted">
                No open gaps. Your latest answer was correct for every question you have seen.
              </p>
            ) : (
              <ul className="mt-4 flex flex-col gap-3">
                {progress.data.weakTopics.map((topic) => (
                  <li key={topic.prompt}>
                    <WeakTopicCard courseId={courseId} topic={topic} />
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="mt-10">
            <SectionLabel>Recent attempts</SectionLabel>
            <ul className="mt-4 flex flex-col gap-2">
              {progress.data.attempts.map((attempt) => (
                <li key={attempt.id} className="surface-panel flex items-center justify-between gap-3 rounded-2xl px-4 py-3">
                  <p className="font-medium text-ink">
                    {attempt.score} / {attempt.total}
                  </p>
                  <p className="text-sm text-muted">{formatWhen(attempt.createdAt)}</p>
                </li>
              ))}
            </ul>
          </section>
        </>
      ) : null}
    </Page>
  )
}

function ScoreCard({ label, score, total }: { label: string; score: number | null; total: number | null }) {
  return (
    <article className="surface-panel rounded-2xl p-4">
      <p className="text-sm font-medium text-muted">{label}</p>
      <p className="mt-2 font-display text-2xl font-semibold text-ink">
        {score ?? 0} / {total ?? 0}
      </p>
    </article>
  )
}

function WeakTopicCard({ courseId, topic }: { courseId: string; topic: WeakTopic }) {
  const times = topic.missCount === 1 ? '1 time' : `${topic.missCount} times`
  return (
    <article className="surface-panel rounded-2xl px-5 py-4">
      <p className="font-medium text-ink">{topic.prompt}</p>
      <p className="mt-1 text-sm text-muted">Missed {times}</p>
      <p className="mt-3">
        <Link
          to={`/courses/${courseId}/qa?question=${encodeURIComponent(topic.prompt)}`}
          className="text-sm font-medium text-ink underline decoration-accent/50 underline-offset-4"
        >
          Ask about this
        </Link>
      </p>
    </article>
  )
}

function formatWhen(iso: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Berlin',
  }).format(date)
}
