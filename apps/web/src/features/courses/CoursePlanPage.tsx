import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  BackLink,
  Button,
  EmptyState,
  Page,
  PageHeader,
  SectionLabel,
} from '../../shared/ui/ui.tsx'
import { getCourse } from './course-api.ts'
import { getStudyPlan, saveStudyPlan } from './study-api.ts'
import type { PlanStep, StudyPlan } from './study-api.ts'
import { studyActionError } from './study-ui.ts'

export function CoursePlanPage() {
  const { courseId = '' } = useParams()
  const course = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => getCourse(courseId),
    retry: false,
    refetchOnMount: 'always',
  })
  const enrolled = course.data?.enrolled === true
  const plan = useQuery({
    queryKey: ['plan', courseId],
    queryFn: () => getStudyPlan(courseId),
    enabled: enrolled,
    retry: false,
  })
  const [examDate, setExamDate] = useState('')
  const [saved, setSaved] = useState<StudyPlan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: (date: string) => saveStudyPlan(courseId, date),
    onSuccess: (next) => {
      setSaved(next)
      setExamDate(next.examDate)
      setError(null)
    },
    onError: (err) => setError(studyActionError(err, 'Could not save that exam date.')),
  })
  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const missing = plan.error instanceof ApiError && plan.error.status === 404
  const courseLoading = course.isLoading && course.data == null
  const shown = saved ?? plan.data ?? null

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!examDate) {
      return
    }
    save.mutate(examDate)
  }

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>
        {course.data?.title ?? (courseLoading ? 'Loading course…' : 'Course')}
      </BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Plan'}
          title="Study plan"
          description="Pick your exam date. We’ll line up the quiz questions you still miss on the days before — and drop them off the list once you get them right."
        />
      </div>

      {courseLoading ? (
        <p className="mt-6 animate-pulse-soft text-muted" role="status">
          Loading plan…
        </p>
      ) : null}
      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}
      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Join to build a plan"
          description="A study plan uses the quiz questions you still miss — join the course first."
        />
      ) : null}
      {plan.isError && !missing ? (
        <div className="mt-6">
          <Alert>Couldn’t load your study plan. Try again in a moment.</Alert>
        </div>
      ) : null}

      {enrolled && (missing || plan.data || saved) ? (
        <form className="mt-8 flex flex-col gap-3" onSubmit={onSubmit}>
          <label htmlFor="exam-date" className="text-sm font-medium text-ink-soft">
            Exam date
          </label>
          <input
            id="exam-date"
            type="date"
            value={examDate || shown?.examDate || ''}
            onChange={(event) => setExamDate(event.target.value)}
            className="w-full max-w-xs rounded-lg border border-ink/20 bg-white px-3 py-2 text-sm text-ink"
          />
          {error ? <Alert>{error}</Alert> : null}
          <div>
            <Button type="submit" disabled={save.isPending || !(examDate || shown?.examDate)}>
              {save.isPending ? 'Building…' : 'Build my plan'}
            </Button>
          </div>
        </form>
      ) : null}

      {enrolled && shown ? (
        <section className="mt-10">
          <SectionLabel>{planHeading(shown.daysUntilExam)}</SectionLabel>
          <ol className="mt-4 flex flex-col gap-3">
            {shown.steps.map((step, index) => (
              <li key={`${step.date}-${step.kind}-${step.prompt ?? index}`}>
                <PlanStepCard courseId={courseId} step={step} />
              </li>
            ))}
          </ol>
        </section>
      ) : null}
    </Page>
  )
}

function planHeading(daysUntilExam: number) {
  if (daysUntilExam < 0) {
    return 'Exam date has passed'
  }
  if (daysUntilExam === 0) {
    return 'Exam is today'
  }
  if (daysUntilExam === 1) {
    return '1 day until the exam'
  }
  return `${daysUntilExam} days until the exam`
}

function PlanStepCard({ courseId, step }: { courseId: string; step: PlanStep }) {
  return (
    <article className="surface-panel rounded-2xl px-5 py-4">
      <p className="text-sm font-medium text-muted">{formatDay(step.date)}</p>
      <p className="mt-1 font-medium text-ink">{stepTitle(step)}</p>
      {step.kind === 'GAP' && step.prompt ? (
        <p className="mt-3">
          <Link
            to={`/courses/${courseId}/qa?question=${encodeURIComponent(step.prompt)}`}
            className="text-sm font-medium text-ink underline decoration-accent/50 underline-offset-4"
          >
            Ask about this
          </Link>
        </p>
      ) : null}
      {step.kind === 'QUIZ' ? (
        <p className="mt-3">
          <Link
            to={`/courses/${courseId}/quiz`}
            className="text-sm font-medium text-ink underline decoration-accent/50 underline-offset-4"
          >
            Take a quiz
          </Link>
        </p>
      ) : null}
    </article>
  )
}

function stepTitle(step: PlanStep) {
  if (step.kind === 'QUIZ') {
    return 'Take a quiz on the published materials so the plan can use what you miss.'
  }
  if (step.kind === 'REVIEW') {
    return 'Review the published lectures before the exam.'
  }
  if (step.kind === 'EXAM_PASSED') {
    return 'This exam date has passed. Choose a new date.'
  }
  const times = step.missCount === 1 ? '1 time' : `${step.missCount} times`
  return `${step.prompt} Missed ${times}.`
}

function formatDay(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  if (!year || !month || !day) {
    return iso
  }
  return new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, day)))
}
