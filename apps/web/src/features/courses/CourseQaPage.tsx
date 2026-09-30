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
import { askCourseQuestion } from './rag-api.ts'
import { studyActionError } from './study-ui.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import type { RagAnswer } from './rag-api.ts'

const QUESTION_MAX = 2000
const MATERIALS_GONE =
  'Every selected material must be ready, published, and part of this course.'

export function CourseQaPage() {
  const { courseId = '' } = useParams()
  const course = useQuery({ queryKey: ['courses', courseId], queryFn: () => getCourse(courseId), retry: false })
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })
  const [question, setQuestion] = useState('')
  const [askedQuestion, setAskedQuestion] = useState<string | null>(null)
  const [result, setResult] = useState<RagAnswer | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [materialNotice, setMaterialNotice] = useState<string | null>(null)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>(() =>
    courseId ? loadMaterialSelection('qa', courseId) : [],
  )
  const [pickingMaterials, setPickingMaterials] = useState(true)
  const selectedRef = useRef(selectedMaterialIds)
  selectedRef.current = selectedMaterialIds

  useEffect(() => {
    if (!courseId) {
      return
    }
    const saved = loadMaterialSelection('qa', courseId)
    if (saved.length > 0) {
      setSelectedMaterialIds(saved)
    }
  }, [courseId])

  const ask = useMutation({
    mutationFn: ({ q, materialIds }: { q: string; materialIds: string[] }) =>
      askCourseQuestion(courseId, q, materialIds),
    onSuccess: (answer, variables) => {
      setError(null)
      setMaterialNotice(null)
      setAskedQuestion(variables.q)
      setResult(answer)
    },
    onError: async (err) => {
      // Keep the previous answer visible; only update the error banner.
      const message = studyActionError(err, 'Could not answer that question.')
      if (message.includes(MATERIALS_GONE) || (err instanceof ApiError && err.message.includes(MATERIALS_GONE))) {
        const refreshed = await materials.refetch()
        const readyIds = new Set(
          (refreshed.data ?? [])
            .filter((m) => m.visibility === 'PUBLISHED' && m.processingStatus === 'READY')
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
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY') ??
    []
  const readyIdsKey = readyMaterials.map((m) => m.id).sort().join(',')

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

  function submitQuestion(raw: string) {
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
    ask.mutate({ q: trimmed, materialIds: selectedMaterialIds })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    submitQuestion(question)
  }

  function useSuggestedPrompt(prompt: string) {
    setError(null)
    setQuestion(prompt.slice(0, QUESTION_MAX))
  }

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const insufficient = result != null && result.citations.length === 0
  const overLimit = question.length > QUESTION_MAX
  const canAsk = !ask.isPending && question.trim().length > 0 && !overLimit

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>{course.data?.title ?? 'Course'}</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Q&A'}
          title="Course Q&A"
          description="Ask your own question, or start from a ready prompt. Answers stay grounded in published lecture PDFs."
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

      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Enroll to ask questions"
          description="Q&A is only available after you join this course."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title="No published materials yet"
          description="Once your professor publishes ready lecture PDFs, you can ask grounded questions here."
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
              description="Select the published lecture PDFs that Q&A should search. Your choice stays active for this session."
              confirmLabel="Use selected materials"
              initialSelectedIds={selectedMaterialIds}
              onCancel={selectedMaterialIds.length > 0 ? () => setPickingMaterials(false) : undefined}
              onConfirm={(materialIds) => {
                const recovering = materialNotice != null
                setSelectedMaterialIds(materialIds)
                saveMaterialSelection('qa', courseId, materialIds)
                setPickingMaterials(false)
                if (!recovering) {
                  setResult(null)
                  setAskedQuestion(null)
                }
                setError(null)
                setMaterialNotice(null)
              }}
            />
          ) : (
          <Panel className="mt-8">
            <SectionLabel>Your question</SectionLabel>
            <p className="mt-2 text-sm text-muted">
              Searching {selectedMaterialIds.length} selected {selectedMaterialIds.length === 1 ? 'material' : 'materials'}.
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
                  onChange={(event) => setQuestion(event.target.value)}
                  rows={4}
                  maxLength={QUESTION_MAX}
                  className="rounded-lg border border-line bg-white/80 px-3 py-2.5 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25"
                  placeholder="What are the main ideas covered in these materials?"
                />
                <span className={`text-xs font-normal ${overLimit ? 'text-danger' : 'text-muted'}`}>
                  {question.length.toLocaleString()} / {QUESTION_MAX.toLocaleString()} characters
                  {overLimit ? ' — shorten your question to ask' : ''}
                </span>
              </div>

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

              {error ? <Alert>{error}</Alert> : null}
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={!canAsk}>
                  {ask.isPending ? 'Thinking…' : 'Ask'}
                </Button>
                {error && question.trim() && !overLimit ? (
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={ask.isPending}
                    onClick={() => submitQuestion(question)}
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
                <p className="text-muted">Retrieving published pages and drafting a study-style answer…</p>
              </Panel>
            </section>
          ) : null}

          {result ? (
            <section className={`mt-10 ${ask.isPending ? 'opacity-60' : 'animate-rise-delay'}`}>
              {askedQuestion ? (
                <div className="mb-6 rounded-2xl border border-line/80 bg-mist/60 px-5 py-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">You asked</p>
                  <p className="mt-2 font-display text-lg leading-snug text-ink">{askedQuestion}</p>
                </div>
              ) : null}

              <SectionLabel>{insufficient ? 'No supporting passage found' : 'Answer'}</SectionLabel>
              <Panel className={`mt-4 ${insufficient ? 'border-warn/30 bg-amber-50/50' : ''}`}>
                <StudyMarkdown
                  markdown={result.answer}
                  citations={result.citations.map((citation, index) => ({
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

              <QaSources courseId={courseId} citations={result.citations} answer={result.answer} />
            </section>
          ) : null}
        </>
      ) : null}
    </Page>
  )
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
