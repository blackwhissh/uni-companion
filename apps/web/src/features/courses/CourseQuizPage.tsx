import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import { useAuth } from '../auth/use-auth.ts'
import {
  Alert,
  BackLink,
  Button,
  EmptyState,
  Page,
  PageHeader,
  Panel,
  SectionLabel,
  StatusPill,
} from '../../shared/ui/ui.tsx'
import { getCourse } from './course-api.ts'
import { listMaterials } from './material-api.ts'
import { clearQuizAnswers, loadQuizAnswers, saveQuizAnswers } from './quiz-answer-storage.ts'
import { loadMaterialSelection, saveMaterialSelection } from './material-selection-storage.ts'
import { clearStudyGeneration, loadStudyGeneration, saveStudyGeneration } from './study-generation-storage.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import { StudyVersionControls } from './StudyVersionControls.tsx'
import { generateQuiz, reportQuizVersion, selectQuizVersion, submitQuiz } from './study-api.ts'
import type { AnswerReview, Quiz, QuizAttempt } from './study-api.ts'
import { studyActionError, studyWorkingCopy } from './study-ui.ts'

export function CourseQuizPage() {
  const { courseId = '' } = useParams()
  const { user } = useAuth()
  const userId = user?.id ?? null
  const course = useQuery({ queryKey: ['courses', courseId], queryFn: () => getCourse(courseId), retry: false })
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })

  const [quiz, setQuiz] = useState<Quiz | null>(null)
  const pendingAtMount = useRef(courseId ? loadStudyGeneration('quiz', courseId) : null)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>(() =>
    pendingAtMount.current?.materialIds ?? (courseId ? loadMaterialSelection('quiz', courseId) : []),
  )
  const [answers, setAnswers] = useState<Record<string, number>>({})
  const [attempt, setAttempt] = useState<QuizAttempt | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(() =>
    pendingAtMount.current ? 'Still preparing your shared quiz…' : null,
  )
  const [picking, setPicking] = useState(() => !pendingAtMount.current)
  const [blockingForResume, setBlockingForResume] = useState(() => !!pendingAtMount.current)
  const [generateForce, setGenerateForce] = useState(() => pendingAtMount.current?.force === true)
  const resumedGeneration = useRef(false)

  useEffect(() => {
    if (!quiz || attempt) {
      return
    }
    saveQuizAnswers(quiz.id, answers, userId)
  }, [quiz, answers, attempt, userId])

  const generate = useMutation({
    mutationFn: ({ materialIds, force }: { materialIds: string[]; force: boolean }) => {
      setGenerateForce(force)
      setSelectedMaterialIds(materialIds)
      setPicking(false)
      saveStudyGeneration('quiz', courseId, {
        materialIds,
        force,
        startedAt: Date.now(),
      })
      return generateQuiz(courseId, materialIds, force)
    },
    onSuccess: (next, variables) => {
      clearStudyGeneration('quiz', courseId)
      setBlockingForResume(false)
      setError(null)
      setNotice(null)
      setPicking(false)
      setSelectedMaterialIds(variables.materialIds)
      setQuiz(next)
      setAttempt(null)
      if (variables.force || next.delivery === 'CREATED' || next.delivery === 'NEXT_SHARED' || next.delivery === 'CYCLED') {
        clearQuizAnswers(next.id, userId)
        setAnswers({})
      } else {
        setAnswers(loadQuizAnswers(next.id, userId))
      }
    },
    onError: (err) => {
      clearStudyGeneration('quiz', courseId)
      setBlockingForResume(false)
      setError(studyActionError(err, 'Could not generate a quiz.'))
    },
  })

  useEffect(() => {
    if (resumedGeneration.current || !courseId || course.data?.enrolled !== true) {
      return
    }
    const pending = pendingAtMount.current ?? loadStudyGeneration('quiz', courseId)
    if (!pending) {
      setBlockingForResume(false)
      return
    }
    resumedGeneration.current = true
    setBlockingForResume(true)
    setPicking(false)
    setNotice('Still preparing your shared quiz…')
    generate.mutate({ materialIds: pending.materialIds, force: pending.force })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, course.data?.enrolled])

  function startGeneration(materialIds: string[], force: boolean) {
    if (generate.isPending) {
      return
    }
    saveMaterialSelection('quiz', courseId, materialIds)
    generate.mutate({ materialIds, force })
  }
  const selectVersion = useMutation({
    mutationFn: (versionId: string) => selectQuizVersion(courseId, selectedMaterialIds, versionId),
    onSuccess: (next) => {
      setQuiz(next)
      // Answers are per quiz version id — restore any saved for this version only.
      setAnswers(loadQuizAnswers(next.id, userId))
      setAttempt(null)
      setError(null)
      setNotice(null)
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not open that quiz version.')
    },
  })

  const reportVersion = useMutation({
    mutationFn: () => {
      if (!quiz) {
        throw new Error('No quiz')
      }
      return reportQuizVersion(quiz.id)
    },
    onSuccess: (result) => {
      setError(null)
      if (result.retired) {
        if (quiz) {
          clearQuizAnswers(quiz.id, userId)
        }
        setQuiz(null)
        setPicking(true)
        setNotice('That version was retired after repeated quality reports. Choose materials to continue.')
      } else {
        setNotice('Thanks. Your quality report was recorded.')
      }
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not report this quiz.')
    },
  })

  const submit = useMutation({
    mutationFn: () => {
      if (!quiz) {
        throw new Error('No quiz')
      }
      return submitQuiz(
        quiz.id,
        quiz.questions.map((question) => ({
          questionId: question.id,
          selectedIndex: answers[question.id] ?? -1,
        })),
      )
    },
    onSuccess: (result) => {
      setError(null)
      setAttempt(result)
      if (quiz) {
        clearQuizAnswers(quiz.id, userId)
      }
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not submit the quiz.')
    },
  })

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyMaterials =
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY') ??
    []
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const allAnswered = quiz?.questions?.every((question) => answers[question.id] !== undefined) ?? false
  const unansweredCount =
    quiz?.questions?.filter((question) => answers[question.id] === undefined).length ?? 0
  const reviewsByQuestion = new Map((attempt?.reviews ?? []).map((review) => [review.questionId, review]))
  const showPicker = !blockingForResume && !generate.isPending && (picking || !quiz)
  const showWorking = generate.isPending || blockingForResume

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>{course.data?.title ?? 'Course'}</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Quiz'}
          title="Practice quiz"
          description="Choose materials and take a shared quiz. Existing versions are reused before a new one is created."
          actions={
            enrolled && readyCount > 0 && quiz && !picking ? (
              <Button type="button" variant="secondary" disabled={generate.isPending} onClick={() => setPicking(true)}>
                Change materials
              </Button>
            ) : null
          }
        />
      </div>

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Enroll to take quizzes"
          description="Practice quizzes are only available after you join this course."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title="No published materials yet"
          description="Once your professor publishes ready lecture PDFs, you can generate a practice quiz here."
        />
      ) : null}

      {enrolled && readyCount > 0 ? (
        <>
          {error ? (
            <div className="mt-6 flex flex-col gap-3">
              <Alert>{error}</Alert>
              {selectedMaterialIds.length > 0 ? (
                <div>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={generate.isPending}
                    onClick={() => startGeneration(selectedMaterialIds, generateForce)}
                  >
                    Try again
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
          {notice ? <Panel className="mt-6"><p className="text-sm text-ink-soft">{notice}</p></Panel> : null}

          {showWorking ? (
            <section className="mt-8 animate-pulse-soft" aria-live="polite">
              <SectionLabel>Working</SectionLabel>
              <Panel className="mt-4">
                <p className="text-muted">
                  {blockingForResume && !generate.isPending
                    ? 'Still preparing your shared quiz…'
                    : studyWorkingCopy('quiz', generateForce)}
                </p>
                <p className="mt-2 text-sm text-muted">You can refresh this page — preparation will continue.</p>
              </Panel>
            </section>
          ) : null}

          {showPicker ? (
            <StudyMaterialPicker
              materials={readyMaterials}
              description="Select the lecture PDFs to practice. You will resume your quiz or receive the first shared version for this exact selection."
              confirmLabel={quiz ? 'Open selected quiz' : 'Start quiz'}
              pending={generate.isPending}
              initialSelectedIds={selectedMaterialIds}
              onCancel={quiz ? () => setPicking(false) : undefined}
              onConfirm={(materialIds) => startGeneration(materialIds, false)}
            />
          ) : null}

          {quiz && quiz.questions.length === 0 && !generate.isPending && !picking ? (
            <EmptyState
              title="This quiz has no questions"
              description="Try generating again, or choose a different set of materials."
            />
          ) : null}

          {quiz && quiz.questions.length > 0 && !generate.isPending && !picking ? (
            <section className="mt-8 animate-rise-delay">
              <SectionLabel>
                Shared quiz {quiz.version} of {quiz.availableVersions} · {quiz.questions.length} questions
              </SectionLabel>
              <StudyVersionControls
                noun="quiz"
                delivery={quiz.delivery}
                versions={quiz.versions}
                busy={selectVersion.isPending || reportVersion.isPending}
                onSelect={(versionId) => selectVersion.mutate(versionId)}
                onReport={() => reportVersion.mutate()}
              />
              <ul className="mt-4 flex flex-col gap-6">
                {quiz.questions.map((question, questionIndex) => {
                  const review = reviewsByQuestion.get(question.id)
                  return (
                    <li key={question.id}>
                      <Panel>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="text-sm font-medium text-muted">Question {questionIndex + 1}</p>
                          {review ? (
                            <StatusPill tone={review.correct ? 'ok' : 'danger'}>
                              {review.correct ? 'Correct' : 'Incorrect'}
                            </StatusPill>
                          ) : null}
                        </div>
                        <p className="mt-2 text-base font-medium leading-relaxed text-ink">{question.prompt}</p>
                        <fieldset className="mt-4 flex flex-col gap-2" disabled={attempt !== null}>
                          <legend className="sr-only">Options for question {questionIndex + 1}</legend>
                          {question.options.map((option, optionIndex) => (
                            <label
                              key={`${question.id}-${optionIndex}`}
                              className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 text-sm transition ${optionClassName(optionIndex, answers[question.id], review)}`}
                            >
                              <input
                                type="radio"
                                className="mt-0.5"
                                name={question.id}
                                value={optionIndex}
                                checked={answers[question.id] === optionIndex}
                                onChange={() =>
                                  setAnswers((current) => ({ ...current, [question.id]: optionIndex }))
                                }
                              />
                              <span>{option}</span>
                            </label>
                          ))}
                        </fieldset>
                        {review ? <AnswerReviewBlock review={review} /> : null}
                      </Panel>
                    </li>
                  )
                })}
              </ul>

              {attempt ? (
                <Panel className="mt-8">
                  <p className="font-display text-xl font-semibold text-ink">
                    Score: {attempt.score} / {attempt.total}
                  </p>
                  <p className="mt-2 text-sm text-muted">
                    Review the correct answers and explanations above, or try this quiz again.
                  </p>
                </Panel>
              ) : null}
              <div className="mt-6 flex flex-col gap-2">
                <div className="flex flex-wrap gap-2">
                  {!attempt ? (
                    <Button
                      type="button"
                      disabled={!allAnswered || submit.isPending}
                      title={!allAnswered ? 'Answer all questions to submit' : undefined}
                      onClick={() => submit.mutate()}
                    >
                      {submit.isPending ? 'Submitting…' : 'Submit answers'}
                    </Button>
                  ) : (
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => {
                        setAttempt(null)
                        setAnswers({})
                        if (quiz) {
                          clearQuizAnswers(quiz.id, userId)
                        }
                      }}
                    >
                      Try this quiz again
                    </Button>
                  )}
                  {selectedMaterialIds.length > 0 ? (
                    <Button
                      type="button"
                      variant="ghost"
                      disabled={generate.isPending}
                      onClick={() => startGeneration(selectedMaterialIds, true)}
                    >
                      {nextQuizLabel(quiz)}
                    </Button>
                  ) : null}
                </div>
                {!attempt && !allAnswered ? (
                  <p className="text-sm text-muted" role="status">
                    Answer all questions to submit
                    {unansweredCount > 0 ? ` (${unansweredCount} remaining).` : '.'}
                  </p>
                ) : null}
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </Page>
  )
}

function nextQuizLabel(quiz: Quiz) {
  if (quiz.version < quiz.availableVersions) {
    return 'Next shared quiz'
  }
  if (quiz.availableVersions < quiz.maxVersions) {
    return 'Create another quiz'
  }
  return 'Review from quiz 1'
}

function AnswerReviewBlock({ review }: { review: AnswerReview }) {
  return (
    <div className="mt-4 rounded-lg border border-line bg-mist/60 px-3 py-3">
      <p className="text-sm font-medium text-ink">
        Correct answer: <span className="font-normal">{review.correctOption}</span>
      </p>
      {review.explanation ? (
        <p className="mt-2 text-sm leading-relaxed text-muted">
          <span className="font-medium text-ink-soft">Explanation: </span>
          {review.explanation}
        </p>
      ) : null}
    </div>
  )
}

function optionClassName(optionIndex: number, selectedIndex: number | undefined, review: AnswerReview | undefined) {
  if (!review) {
    return 'border-line bg-white/70 text-ink hover:border-accent/40'
  }
  if (optionIndex === review.correctIndex) {
    return 'border-ok/40 bg-emerald-50 text-ink'
  }
  if (optionIndex === selectedIndex && !review.correct) {
    return 'border-danger/40 bg-red-50 text-ink'
  }
  return 'border-line bg-white/50 text-muted'
}
