import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  BackLink,
  Button,
  EmptyState,
  Page,
  PageHeader,
  Panel,
  SectionLabel,
} from '../../shared/ui/ui.tsx'
import { StudyMarkdown } from '../../shared/ui/StudyMarkdown.tsx'
import { getCourse } from './course-api.ts'
import { listMaterials } from './material-api.ts'
import { loadMaterialSelection, saveMaterialSelection } from './material-selection-storage.ts'
import { QaSources } from './QaSources.tsx'
import { QA_SUGGESTED_PROMPTS } from './qa-prompts.ts'
import { askCourseQuestionStream, getCourseQaStats, listCourseQaHistory } from './rag-api.ts'
import { useAuth } from '../auth/use-auth.ts'
import { studyActionError } from './study-ui.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import type { RagAnswer } from './rag-api.ts'

const QUESTION_MAX = 2000

type QaTurn = {
  id?: string
  question: string
  answer: RagAnswer
}

export function CourseQaPage() {
  const { courseId = '' } = useParams()
  const { ready: authReady } = useAuth()
  const course = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => getCourse(courseId),
    retry: false,
    refetchOnMount: 'always',
  })
  const courseLoading = course.isLoading || (course.isFetching && !course.data)
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })
  const history = useQuery({
    queryKey: ['rag-history', courseId],
    queryFn: () => listCourseQaHistory(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })
  const stats = useQuery({
    queryKey: ['rag-stats', courseId],
    queryFn: () => getCourseQaStats(courseId),
    enabled: course.data?.owned === true && course.data?.enrolled === true,
    retry: false,
  })
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<QaTurn[]>([])
  const [streamPreview, setStreamPreview] = useState<string | null>(null)
  const [streamPhase, setStreamPhase] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pasteWarning, setPasteWarning] = useState<string | null>(null)
  const [materialNotice, setMaterialNotice] = useState<string | null>(null)
  const [followUpMode, setFollowUpMode] = useState(false)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>(() =>
    courseId ? loadMaterialSelection('qa', courseId) : [],
  )
  const [pickingMaterials, setPickingMaterials] = useState(true)
  const selectedRef = useRef(selectedMaterialIds)
  selectedRef.current = selectedMaterialIds
  const historyHydratedFor = useRef<string | null>(null)
  const owned = course.data?.owned === true

  useEffect(() => {
    if (!courseId) {
      return
    }
    const saved = loadMaterialSelection('qa', courseId)
    if (saved.length > 0) {
      setSelectedMaterialIds(saved)
    }
    historyHydratedFor.current = null
    setTurns([])
  }, [courseId])

  useEffect(() => {
    if (!courseId || !history.data || historyHydratedFor.current === courseId) {
      return
    }
    historyHydratedFor.current = courseId
    setTurns(
      (history.data.turns ?? []).map((turn) => ({
        id: turn.id,
        question: turn.question,
        answer: { answer: turn.answer, citations: turn.citations },
      })),
    )
  }, [courseId, history.data])

  const ask = useMutation({
    mutationFn: async ({
      q,
      materialIds,
      prior,
    }: {
      q: string
      materialIds: string[]
      prior: { question: string; answer: string } | null
    }) => {
      setStreamPreview('')
      setStreamPhase('retrieving')
      return askCourseQuestionStream(
        {
          courseId,
          question: q,
          materialIds,
          priorQuestion: prior?.question,
          priorAnswer: prior?.answer,
        },
        {
          onStatus: (phase) => setStreamPhase(phase),
          onDelta: (delta) => setStreamPreview((prev) => (prev ?? '') + delta),
        },
      )
    },
    onSuccess: (answer, variables) => {
      setError(null)
      setMaterialNotice(null)
      setStreamPhase(null)
      setStreamPreview(null)
      setTurns((prev) => [...prev, { question: variables.q, answer }])
      setQuestion('')
      setFollowUpMode(false)
      if (owned) {
        void stats.refetch()
      }
    },
    onError: async (err) => {
      setStreamPhase(null)
      setStreamPreview(null)
      const message = studyActionError(err, 'Could not answer that question.')
      if (isMaterialsGone(message) || (err instanceof ApiError && isMaterialsGone(err.message))) {
        const refreshed = await materials.refetch()
        const readyIds = new Set(
          (refreshed.data ?? [])
            .filter((m) => isSelectableMaterial(m.visibility, m.processingStatus, owned))
            .map((m) => m.id),
        )
        const dropped = selectedRef.current.filter((id) => !readyIds.has(id))
        const kept = selectedRef.current.filter((id) => readyIds.has(id))
        setSelectedMaterialIds(kept)
        if (dropped.length > 0) {
          setMaterialNotice(
            dropped.length === 1
              ? 'One selected material is no longer available (unpublished or not ready). Choose materials again.'
              : `${dropped.length} selected materials are no longer available (unpublished or not ready). Choose materials again.`,
          )
        } else {
          setMaterialNotice('Selected materials are no longer available. Choose materials again.')
        }
        setPickingMaterials(true)
        setError(null)
        return
      }
      setError(readableAskError(err, message))
    },
  })

  const readyMaterials =
    materials.data?.filter((material) =>
      isSelectableMaterial(material.visibility, material.processingStatus, owned),
    ) ?? []
  const readyIdsKey = readyMaterials.map((m) => m.id).sort().join(',')
  const latestTurn = turns.length > 0 ? turns[turns.length - 1] : null

  useEffect(() => {
    function refetchMaterials() {
      if (course.data?.enrolled === true) {
        void materials.refetch()
      }
    }
    function onVisibility() {
      if (document.visibilityState === 'visible') {
        refetchMaterials()
      }
    }
    window.addEventListener('focus', refetchMaterials)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('focus', refetchMaterials)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [course.data?.enrolled, materials])

  useEffect(() => {
    if (!materials.data) {
      return
    }
    const readyIds = new Set(readyMaterials.map((m) => m.id))
    setSelectedMaterialIds((prev) => {
      if (prev.length === 0) {
        return prev
      }
      const kept = prev.filter((id) => readyIds.has(id))
      const dropped = prev.length - kept.length
      if (dropped === 0) {
        return prev
      }
      setMaterialNotice(
        dropped === 1
          ? 'One selected material was unpublished or is no longer ready. It was removed from your selection.'
          : `${dropped} selected materials were unpublished or are no longer ready. They were removed from your selection.`,
      )
      if (kept.length === 0) {
        setPickingMaterials(true)
      }
      return kept
    })
    // readyMaterials identity changes every render; key on ids.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readyIdsKey, materials.data])

  function submitQuestion(raw: string, asFollowUp: boolean) {
    const trimmed = raw.trim()
    if (!trimmed) {
      setError('Ask a question about this course.')
      return
    }
    if (trimmed.length > QUESTION_MAX) {
      setError(`Keep your question to ${QUESTION_MAX.toLocaleString()} characters or fewer.`)
      return
    }
    setQuestion(trimmed)
    if (selectedMaterialIds.length === 0) {
      setError('Select at least one material first.')
      return
    }
    const prior =
      asFollowUp && latestTurn
        ? { question: latestTurn.question, answer: latestTurn.answer.answer }
        : null
    ask.mutate({ q: trimmed, materialIds: selectedMaterialIds, prior })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    submitQuestion(question, followUpMode)
  }

  function useSuggestedPrompt(prompt: string) {
    setError(null)
    setPasteWarning(null)
    setFollowUpMode(false)
    setQuestion(prompt.slice(0, QUESTION_MAX))
  }

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const overLimit = question.length > QUESTION_MAX
  const canAsk =
    authReady &&
    !ask.isPending &&
    question.trim().length > 0 &&
    !overLimit &&
    question.length <= QUESTION_MAX &&
    !pasteWarning &&
    selectedMaterialIds.length > 0

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>
        {course.data?.title ?? (courseLoading ? 'Loading course…' : 'Course')}
      </BackLink>
      {courseLoading ? (
        <p className="mt-6 animate-pulse-soft text-muted" role="status">
          Loading course…
        </p>
      ) : null}
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Q&A'}
          title="Course Q&A"
          description={
            owned
              ? 'Ask grounded questions over ready lecture PDFs. As owner you can also preview unpublished materials.'
              : 'Ask your own question, or start from a ready prompt. Answers stay grounded in published lecture PDFs.'
          }
          actions={
            enrolled && readyCount > 0 && !pickingMaterials ? (
              <Button type="button" variant="secondary" disabled={ask.isPending} onClick={() => setPickingMaterials(true)}>
                Change materials
              </Button>
            ) : null
          }
        />
      </div>

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {owned && enrolled && stats.data && stats.data.totalTurns > 0 ? (
        <p className="mt-4 text-sm text-muted" role="status">
          Course Q&A (all students): {stats.data.totalTurns} turns · avg {stats.data.avgLatencyMs} ms ·{' '}
          {Math.round(stats.data.citeRate * 100)}% with sources ·{' '}
          {Math.round(stats.data.insufficientRate * 100)}% insufficient
        </p>
      ) : null}

      {course.isSuccess && course.data && !enrolled && !unavailable ? (
        <EmptyState
          animate={false}
          title="Enroll to ask questions"
          description="Q&A is only available after you join this course."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title={owned ? 'No ready materials yet' : 'No published materials yet'}
          description={
            owned
              ? 'Upload and finish indexing lecture PDFs, then ask grounded questions here (published or draft).'
              : 'Once your professor publishes ready lecture PDFs, you can ask grounded questions here.'
          }
        />
      ) : null}

      {enrolled && readyCount > 0 ? (
        <>
          {materialNotice ? (
            <div className="mt-6">
              <Alert>{materialNotice}</Alert>
            </div>
          ) : null}

          {pickingMaterials ? (
            <StudyMaterialPicker
              materials={readyMaterials}
              description={
                owned
                  ? 'Select ready lecture PDFs that Q&A should search (including drafts). Your choice is remembered on this device.'
                  : 'Select the published lecture PDFs that Q&A should search. Your choice is remembered on this device.'
              }
              confirmLabel="Use selected materials"
              initialSelectedIds={selectedMaterialIds}
              onCancel={selectedMaterialIds.length > 0 ? () => setPickingMaterials(false) : undefined}
              onConfirm={(materialIds) => {
                const recovering = materialNotice != null
                setSelectedMaterialIds(materialIds)
                saveMaterialSelection('qa', courseId, materialIds)
                setPickingMaterials(false)
                if (!recovering) {
                  setTurns([])
                  setFollowUpMode(false)
                }
                setError(null)
                setMaterialNotice(null)
              }}
            />
          ) : (
            <Panel className="mt-8">
              <SectionLabel>{followUpMode ? 'Follow-up question' : 'Your question'}</SectionLabel>
              <p className="mt-2 text-sm text-muted">
                Searching {selectedMaterialIds.length} selected{' '}
                {selectedMaterialIds.length === 1 ? 'material' : 'materials'}.
                {followUpMode ? ' Prior answer is sent for continuity; retrieval still runs fresh.' : ''}
              </p>
              {selectedMaterialIds.length === 0 ? (
                <p className="mt-2 text-sm text-muted" role="status">
                  Pick at least one material to ask a question.
                </p>
              ) : null}
              <form className="mt-4 flex flex-col gap-4" onSubmit={onSubmit}>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="qa-question" className="text-sm font-medium text-ink-soft">
                    Question
                  </label>
                  <textarea
                    id="qa-question"
                    value={question}
                    onChange={(event) => {
                      setPasteWarning(null)
                      setQuestion(event.target.value.slice(0, QUESTION_MAX))
                    }}
                    onPaste={(event) => {
                      const pasted = event.clipboardData.getData('text')
                      if (!pasted) {
                        return
                      }
                      const start = event.currentTarget.selectionStart ?? question.length
                      const end = event.currentTarget.selectionEnd ?? start
                      const merged = question.slice(0, start) + pasted + question.slice(end)
                      if (merged.length > QUESTION_MAX) {
                        event.preventDefault()
                        setQuestion(merged.slice(0, QUESTION_MAX))
                        setPasteWarning(
                          `Paste was trimmed to ${QUESTION_MAX.toLocaleString()} characters. Shorten your question before asking.`,
                        )
                      } else {
                        setPasteWarning(null)
                      }
                    }}
                    rows={4}
                    className="rounded-lg border border-line bg-white/80 px-3 py-2.5 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25"
                    placeholder={
                      followUpMode
                        ? 'Ask a follow-up about the previous answer…'
                        : 'What are the main ideas covered in these materials?'
                    }
                  />
                  <span className={`text-xs font-normal ${overLimit || pasteWarning ? 'text-danger' : 'text-muted'}`}>
                    {question.length.toLocaleString()} / {QUESTION_MAX.toLocaleString()} characters
                    {pasteWarning
                      ? ` — ${pasteWarning}`
                      : overLimit
                        ? ' — shorten your question to ask'
                        : ''}
                  </span>
                </div>

                {!followUpMode ? (
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">Try a prompt</p>
                    <div className="mt-2 flex flex-wrap gap-2" role="group" aria-label="Suggested prompts">
                      {QA_SUGGESTED_PROMPTS.map((prompt) => (
                        <button
                          key={prompt.id}
                          type="button"
                          disabled={ask.isPending}
                          onClick={() => useSuggestedPrompt(prompt.question)}
                          className="rounded-lg border border-line bg-mist/70 px-3 py-1.5 text-left text-sm text-ink-soft transition hover:border-accent/40 hover:bg-white hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {prompt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                {error ? <Alert>{error}</Alert> : null}
                <div className="flex flex-wrap gap-2">
                  <Button type="submit" disabled={!canAsk}>
                    {ask.isPending ? 'Thinking…' : followUpMode ? 'Ask follow-up' : 'Ask'}
                  </Button>
                  {followUpMode ? (
                    <Button
                      type="button"
                      variant="secondary"
                      disabled={ask.isPending}
                      onClick={() => {
                        setFollowUpMode(false)
                        setQuestion('')
                      }}
                    >
                      Cancel follow-up
                    </Button>
                  ) : null}
                  {error && question.trim() && !overLimit ? (
                    <Button
                      type="button"
                      variant="secondary"
                      disabled={ask.isPending}
                      onClick={() => submitQuestion(question, followUpMode)}
                    >
                      Try again
                    </Button>
                  ) : null}
                </div>
              </form>
            </Panel>
          )}

          {!pickingMaterials && ask.isPending ? (
            <section className="mt-10 animate-pulse-soft" aria-live="polite">
              <SectionLabel>Working</SectionLabel>
              <Panel className="mt-4">
                <p className="text-muted">
                  {streamPhase === 'generating'
                    ? 'Drafting a study-style answer…'
                    : 'Retrieving lecture pages…'}
                </p>
                {streamPreview ? (
                  <div className="mt-4 opacity-80">
                    <StudyMarkdown markdown={streamPreview} citations={[]} />
                  </div>
                ) : null}
              </Panel>
            </section>
          ) : null}

          {turns.map((turn, turnIndex) => {
            const insufficient = turn.answer.citations.length === 0
            const isLatest = turnIndex === turns.length - 1
            return (
              <section
                key={turn.id ?? `${turn.question}-${turnIndex}`}
                className={`mt-10 ${ask.isPending && isLatest ? 'opacity-60' : 'animate-rise-delay'}`}
              >
                <div className="mb-6 rounded-2xl border border-line/80 bg-mist/60 px-5 py-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">
                    {turnIndex === 0 ? 'You asked' : `Follow-up ${turnIndex}`}
                  </p>
                  <p className="mt-2 font-display text-lg leading-snug text-ink">{turn.question}</p>
                </div>

                <SectionLabel>{insufficient ? 'No supporting passage found' : 'Answer'}</SectionLabel>
                <Panel className={`mt-4 ${insufficient ? 'border-warn/30 bg-amber-50/50' : ''}`}>
                  <StudyMarkdown
                    markdown={turn.answer.answer}
                    citations={turn.answer.citations.map((citation, index) => ({
                      index: index + 1,
                      title: citation.title,
                      pageNumber: citation.pageNumber,
                      sectionHint: citation.sectionHint,
                    }))}
                  />
                  {insufficient ? (
                    <p className="mt-4 border-t border-warn/20 pt-3 text-sm leading-relaxed text-ink-soft" role="status">
                      No supporting passage was found in the selected materials for this answer. Treat it as
                      unverified until you open the lecture PDF yourself.
                    </p>
                  ) : null}
                </Panel>

                <QaSources courseId={courseId} citations={turn.answer.citations} answer={turn.answer.answer} />

                {isLatest && !pickingMaterials && !ask.isPending ? (
                  <div className="mt-6">
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => {
                        setFollowUpMode(true)
                        setError(null)
                        setQuestion('')
                      }}
                    >
                      Ask a follow-up
                    </Button>
                  </div>
                ) : null}
              </section>
            )
          })}
        </>
      ) : null}
    </Page>
  )
}

function isSelectableMaterial(
  visibility: string,
  processingStatus: string,
  owned: boolean,
): boolean {
  if (processingStatus !== 'READY') {
    return false
  }
  return owned || visibility === 'PUBLISHED'
}

function isMaterialsGone(message: string): boolean {
  return /must be ready/i.test(message) && /part of this course/i.test(message)
}

function readableAskError(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) {
    return fallback
  }
  const questionDetail = err.details?.question
  if (questionDetail) {
    if (/size|2000|characters/i.test(questionDetail)) {
      return `Keep your question to ${QUESTION_MAX.toLocaleString()} characters or fewer.`
    }
    return questionDetail
  }
  if (/validation failed/i.test(err.message)) {
    return `Keep your question to ${QUESTION_MAX.toLocaleString()} characters or fewer.`
  }
  return fallback
}
