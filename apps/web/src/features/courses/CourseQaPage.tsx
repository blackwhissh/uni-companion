import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams, useSearchParams } from 'react-router'
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
import { GroundedNote } from '../../shared/ui/GroundedNote.tsx'
import { StudyMarkdown } from '../../shared/ui/StudyMarkdown.tsx'
import { getCourse } from './course-api.ts'
import { listMaterials } from './material-api.ts'
import { loadMaterialSelection, saveMaterialSelection } from './material-selection-storage.ts'
import { QaSources } from './QaSources.tsx'
import { QA_FOLLOW_UP_PROMPTS, QA_SUGGESTED_PROMPTS } from './qa-prompts.ts'
import { ExplainLanguageField } from './ExplainLanguageField.tsx'
import type { ExplainLanguage } from './ExplainLanguageField.tsx'
import {
  sendStudyChatMessage,
  startStudyChat,
} from './rag-api.ts'
import type { StudyChatMessage, StudyChatSession } from './rag-api.ts'
import { useAuth } from '../auth/use-auth.ts'
import { studyActionError } from './study-ui.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'

const QUESTION_MAX = 2000
const MATERIALS_GONE =
  'Every selected material must be ready, published, and part of this course.'

export function CourseQaPage() {
  const { courseId = '' } = useParams()
  const [searchParams] = useSearchParams()
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
  const [draft, setDraft] = useState(() => (searchParams.get('question') ?? '').slice(0, QUESTION_MAX))
  const [language, setLanguage] = useState<ExplainLanguage>('')
  const [session, setSession] = useState<StudyChatSession | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pasteWarning, setPasteWarning] = useState<string | null>(null)
  const [materialNotice, setMaterialNotice] = useState<string | null>(null)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>(() =>
    courseId ? loadMaterialSelection('qa', courseId) : [],
  )
  const [pickingMaterials, setPickingMaterials] = useState(true)
  const selectedRef = useRef(selectedMaterialIds)
  selectedRef.current = selectedMaterialIds
  const threadEndRef = useRef<HTMLDivElement | null>(null)
  const pendingStarter = useRef<string | null>(searchParams.get('question'))

  useEffect(() => {
    if (!courseId) {
      return
    }
    const saved = loadMaterialSelection('qa', courseId)
    if (saved.length > 0) {
      setSelectedMaterialIds(saved)
    }
  }, [courseId])

  useEffect(() => {
    const node = threadEndRef.current
    if (node && typeof node.scrollIntoView === 'function') {
      node.scrollIntoView({ behavior: 'smooth', block: 'end' })
    }
  }, [session?.messages.length, session?.id])

  const ask = useMutation({
    mutationFn: ({ sessionId, question }: { sessionId: string; question: string }) =>
      sendStudyChatMessage(sessionId, question),
    onSuccess: (next) => {
      setError(null)
      setMaterialNotice(null)
      setSession(next)
      setDraft('')
      setPasteWarning(null)
    },
    onError: async (err) => {
      const message = studyActionError(err, 'Could not answer that.')
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
        setSession(null)
        setMaterialNotice(
          dropped.length === 1
            ? 'One selected lecture is no longer available. Pick lectures again to keep chatting.'
            : 'Some selected lectures are no longer available. Pick lectures again to keep chatting.',
        )
        setPickingMaterials(true)
        setError(null)
        return
      }
      setError(readableAskError(err, message))
    },
  })

  const start = useMutation({
    mutationFn: (materialIds: string[]) =>
      startStudyChat(courseId, materialIds, language === '' ? undefined : language),
    onSuccess: (next) => {
      setError(null)
      setMaterialNotice(null)
      setSession(next)
      const starter = pendingStarter.current?.trim()
      if (starter) {
        pendingStarter.current = null
        setDraft('')
        ask.mutate({ sessionId: next.id, question: starter })
      }
    },
    onError: (err) => {
      setError(studyActionError(err, 'Could not start a study chat.'))
      setPickingMaterials(true)
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
          ? 'One selected lecture was unpublished or is no longer ready. It was removed from your selection.'
          : `${dropped} selected lectures were unpublished or are no longer ready. They were removed from your selection.`,
      )
      if (kept.length === 0) {
        setPickingMaterials(true)
        setSession(null)
      }
      return kept
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readyIdsKey, materials.data])

  function beginChat(materialIds: string[]) {
    setSelectedMaterialIds(materialIds)
    saveMaterialSelection('qa', courseId, materialIds)
    setPickingMaterials(false)
    setSession(null)
    setError(null)
    setMaterialNotice(null)
    start.mutate(materialIds)
  }

  function submitQuestion(raw: string) {
    const trimmed = raw.trim()
    if (!trimmed) {
      setError('Ask something about these lectures.')
      return
    }
    if (trimmed.length > QUESTION_MAX) {
      setError(`Keep your message to ${QUESTION_MAX.toLocaleString()} characters or fewer.`)
      return
    }
    if (!session) {
      setError('Pick lectures first so we know what to study from.')
      return
    }
    ask.mutate({ sessionId: session.id, question: trimmed })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    submitQuestion(draft)
  }

  function usePrompt(prompt: string, sendImmediately: boolean) {
    setError(null)
    setPasteWarning(null)
    if (sendImmediately && session) {
      submitQuestion(prompt)
      return
    }
    setDraft(prompt.slice(0, QUESTION_MAX))
  }

  function startFreshChat() {
    if (selectedMaterialIds.length === 0) {
      setPickingMaterials(true)
      return
    }
    setDraft('')
    setError(null)
    beginChat(selectedMaterialIds)
  }

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const overLimit = draft.length > QUESTION_MAX
  const busy = start.isPending || ask.isPending
  const canSend =
    authReady &&
    !busy &&
    !!session &&
    draft.trim().length > 0 &&
    !overLimit &&
    !pasteWarning
  const hasMessages = (session?.messages.length ?? 0) > 0

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
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Study chat'}
          title="Study chat"
          description="Talk through topics the way you would with a tutor — ask follow-ups, request analogies, or get quizzed. Answers stay grounded in the lectures you pick."
          actions={
            enrolled && readyCount > 0 && !pickingMaterials ? (
              <div className="flex flex-wrap gap-2">
                {session ? (
                  <Button type="button" variant="ghost" disabled={busy} onClick={startFreshChat}>
                    New chat
                  </Button>
                ) : null}
                <Button type="button" variant="secondary" disabled={busy} onClick={() => setPickingMaterials(true)}>
                  Change lectures
                </Button>
              </div>
            ) : null
          }
        />
      </div>

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Join to study in chat"
          description="Join the course first, then you can chat about the lectures like a tutor session."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title="No lectures to study yet"
          description="Once your professor publishes the PDFs, come back and chat about the topics."
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
              description="Choose which lectures this chat can use. You can change them anytime — that starts a fresh chat."
              confirmLabel="Start studying"
              pending={start.isPending}
              initialSelectedIds={selectedMaterialIds}
              onCancel={session || selectedMaterialIds.length > 0 ? () => setPickingMaterials(false) : undefined}
              onConfirm={beginChat}
            />
          ) : (
            <div className="mt-8 flex flex-col gap-4">
              <p className="text-sm text-muted">
                Studying from {selectedMaterialIds.length}{' '}
                {selectedMaterialIds.length === 1 ? 'lecture' : 'lectures'}. Ask anything — then keep going with
                follow-ups.
              </p>

              <Panel className="flex min-h-[28rem] flex-col overflow-hidden !p-0">
                <div className="flex-1 space-y-4 overflow-y-auto px-5 py-5" aria-live="polite">
                  {!hasMessages && !busy ? (
                    <div className="flex h-full flex-col justify-center gap-4">
                      <div>
                        <SectionLabel>Start anywhere</SectionLabel>
                        <p className="mt-2 text-sm leading-relaxed text-muted">
                          Pick a starter, or type your own question about a topic, definition, or process.
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-2" role="group" aria-label="Suggested starters">
                        {QA_SUGGESTED_PROMPTS.map((prompt) => (
                          <button
                            key={prompt.id}
                            type="button"
                            disabled={busy || !session}
                            onClick={() => usePrompt(prompt.question, true)}
                            className="rounded-lg border border-line bg-mist/70 px-3 py-1.5 text-left text-sm text-ink-soft transition hover:border-accent/40 hover:bg-white hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {prompt.label}
                          </button>
                        ))}
                      </div>
                      <GroundedNote />
                    </div>
                  ) : null}

                  {(session?.messages ?? []).map((message) => (
                    <ChatBubble key={message.id} courseId={courseId} message={message} />
                  ))}

                  {busy ? (
                    <div className="animate-pulse-soft rounded-2xl border border-line/70 bg-mist/50 px-4 py-3 text-sm text-muted">
                      Looking through the lectures…
                    </div>
                  ) : null}
                  <div ref={threadEndRef} />
                </div>

                {hasMessages && !busy ? (
                  <div className="border-t border-line/70 px-5 py-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">Keep going</p>
                    <div className="mt-2 flex flex-wrap gap-2" role="group" aria-label="Follow-up prompts">
                      {QA_FOLLOW_UP_PROMPTS.map((prompt) => (
                        <button
                          key={prompt.id}
                          type="button"
                          disabled={!session}
                          onClick={() => usePrompt(prompt.question, true)}
                          className="rounded-lg border border-line bg-white px-3 py-1.5 text-left text-sm text-ink-soft transition hover:border-accent/40 hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {prompt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                <form className="border-t border-line/70 px-5 py-4" onSubmit={onSubmit}>
                  <ExplainLanguageField name="qa-language" value={language} onChange={setLanguage} matchQuestion />
                  <label htmlFor="qa-draft" className="mt-3 block text-sm font-medium text-ink-soft">
                    Your message
                  </label>
                  <textarea
                    id="qa-draft"
                    value={draft}
                    rows={3}
                    onChange={(event) => {
                      setPasteWarning(null)
                      setDraft(event.target.value.slice(0, QUESTION_MAX))
                    }}
                    onPaste={(event) => {
                      const pasted = event.clipboardData.getData('text')
                      if (!pasted) {
                        return
                      }
                      const startPos = event.currentTarget.selectionStart ?? draft.length
                      const end = event.currentTarget.selectionEnd ?? startPos
                      const merged = draft.slice(0, startPos) + pasted + draft.slice(end)
                      if (merged.length > QUESTION_MAX) {
                        event.preventDefault()
                        setDraft(merged.slice(0, QUESTION_MAX))
                        setPasteWarning(
                          `Paste was trimmed to ${QUESTION_MAX.toLocaleString()} characters. Shorten your message before sending.`,
                        )
                      } else {
                        setPasteWarning(null)
                      }
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && !event.shiftKey) {
                        event.preventDefault()
                        if (canSend) {
                          submitQuestion(draft)
                        }
                      }
                    }}
                    className="mt-1.5 w-full rounded-lg border border-line bg-white/80 px-3 py-2.5 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25"
                    placeholder="Ask about a topic, or say “explain that simpler”…"
                  />
                  <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
                    <span className={`text-xs font-normal ${overLimit || pasteWarning ? 'text-danger' : 'text-muted'}`}>
                      {draft.length.toLocaleString()} / {QUESTION_MAX.toLocaleString()} characters
                      {pasteWarning
                        ? ` — ${pasteWarning}`
                        : overLimit
                          ? ' — shorten your message to send'
                          : ' · Enter to send · Shift+Enter for a new line'}
                    </span>
                  </div>
                  {error ? (
                    <div className="mt-3">
                      <Alert>{error}</Alert>
                    </div>
                  ) : null}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button type="submit" disabled={!canSend}>
                      {ask.isPending ? 'Thinking…' : hasMessages ? 'Send' : 'Ask'}
                    </Button>
                    {error && draft.trim() && !overLimit && session ? (
                      <Button
                        type="button"
                        variant="secondary"
                        disabled={busy}
                        onClick={() => submitQuestion(draft)}
                      >
                        Try again
                      </Button>
                    ) : null}
                  </div>
                </form>
              </Panel>
            </div>
          )}
        </>
      ) : null}
    </Page>
  )
}

function ChatBubble({ courseId, message }: { courseId: string; message: StudyChatMessage }) {
  const isUser = message.role === 'USER'
  if (isUser) {
    return (
      <div className="ml-auto max-w-[85%] rounded-2xl bg-ink px-4 py-3 text-white shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-white/70">You</p>
        <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{message.content}</p>
      </div>
    )
  }

  const insufficient = message.citations.length === 0
  return (
    <div className="max-w-[95%] space-y-3">
      <div className={`rounded-2xl border px-4 py-3 ${insufficient ? 'border-warn/30 bg-amber-50/50' : 'border-line/80 bg-white'}`}>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">Tutor</p>
        <div className="mt-2">
          <StudyMarkdown
            markdown={message.content}
            citations={message.citations.map((citation, index) => ({
              index: index + 1,
              title: citation.title,
              pageNumber: citation.pageNumber,
              sectionHint: citation.sectionHint,
            }))}
          />
        </div>
        {insufficient ? (
          <p className="mt-3 border-t border-warn/20 pt-3 text-sm leading-relaxed text-ink-soft" role="status">
            No supporting passage was found in the selected lectures for this answer. Treat it as unverified until you
            open the PDF yourself.
          </p>
        ) : null}
      </div>
      <QaSources courseId={courseId} citations={message.citations} answer={message.content} />
    </div>
  )
}

function readableAskError(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) {
    return fallback
  }
  const questionDetail = err.details?.question
  if (questionDetail) {
    if (/size|2000|characters/i.test(questionDetail)) {
      return `Keep your message to ${QUESTION_MAX.toLocaleString()} characters or fewer.`
    }
    return questionDetail
  }
  if (/validation failed/i.test(err.message)) {
    return `Keep your message to ${QUESTION_MAX.toLocaleString()} characters or fewer.`
  }
  return fallback
}
