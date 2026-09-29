import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
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
import { QaSources } from './QaSources.tsx'
import { QA_SUGGESTED_PROMPTS } from './qa-prompts.ts'
import { askCourseQuestion } from './rag-api.ts'
import { studyActionError } from './study-ui.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import type { RagAnswer } from './rag-api.ts'

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
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>([])
  const [pickingMaterials, setPickingMaterials] = useState(true)

  const ask = useMutation({
    mutationFn: ({ q, materialIds }: { q: string; materialIds: string[] }) =>
      askCourseQuestion(courseId, q, materialIds),
    onSuccess: (answer, variables) => {
      setError(null)
      setAskedQuestion(variables.q)
      setResult(answer)
    },
    onError: (err) => {
      setResult(null)
      setAskedQuestion(null)
      setError(studyActionError(err, 'Could not answer that question.'))
    },
  })

  function submitQuestion(raw: string) {
    const trimmed = raw.trim()
    if (!trimmed) {
      setError('Ask a question about this course.')
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
    setQuestion(prompt)
  }

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyMaterials =
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY') ??
    []
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const insufficient = result != null && result.citations.length === 0

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
          {pickingMaterials ? (
            <StudyMaterialPicker
              materials={readyMaterials}
              description="Select the published lecture PDFs that Q&A should search. Your choice stays active for this session."
              confirmLabel="Use selected materials"
              initialSelectedIds={selectedMaterialIds}
              onCancel={selectedMaterialIds.length > 0 ? () => setPickingMaterials(false) : undefined}
              onConfirm={(materialIds) => {
                setSelectedMaterialIds(materialIds)
                setPickingMaterials(false)
                setResult(null)
                setAskedQuestion(null)
                setError(null)
              }}
            />
          ) : (
          <Panel className="mt-8">
            <SectionLabel>Your question</SectionLabel>
            <p className="mt-2 text-sm text-muted">
              Searching {selectedMaterialIds.length} selected {selectedMaterialIds.length === 1 ? 'material' : 'materials'}.
            </p>
            <form className="mt-4 flex flex-col gap-4" onSubmit={onSubmit}>
              <label className="flex flex-col gap-1.5 text-sm font-medium text-ink-soft">
                Question
                <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  rows={4}
                  className="rounded-lg border border-line bg-white/80 px-3 py-2.5 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25"
                  placeholder="What is consensus used for in this lecture?"
                />
              </label>

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
                <Button type="submit" disabled={ask.isPending || !question.trim()}>
                  {ask.isPending ? 'Thinking…' : 'Ask'}
                </Button>
                {error && question.trim() ? (
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

          {!pickingMaterials && result && !ask.isPending ? (
            <section className="mt-10 animate-rise-delay">
              {askedQuestion ? (
                <div className="mb-6 rounded-2xl border border-line/80 bg-mist/60 px-5 py-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">You asked</p>
                  <p className="mt-2 font-display text-lg leading-snug text-ink">{askedQuestion}</p>
                </div>
              ) : null}

              <SectionLabel>{insufficient ? 'No grounded match' : 'Answer'}</SectionLabel>
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
              </Panel>

              <QaSources courseId={courseId} citations={result.citations} answer={result.answer} />
            </section>
          ) : null}
        </>
      ) : null}
    </Page>
  )
}
