import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
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
import { getCourse } from './course-api.ts'
import { listMaterials } from './material-api.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import { StudyVersionControls } from './StudyVersionControls.tsx'
import { generateFlashcards, reportFlashcardVersion, selectFlashcardVersion } from './study-api.ts'
import type { FlashcardDeck } from './study-api.ts'
import { studyActionError, studyWorkingCopy } from './study-ui.ts'

export function CourseFlashcardsPage() {
  const { courseId = '' } = useParams()
  const course = useQuery({ queryKey: ['courses', courseId], queryFn: () => getCourse(courseId), retry: false })
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })

  const [deck, setDeck] = useState<FlashcardDeck | null>(null)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>([])
  const [index, setIndex] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [picking, setPicking] = useState(true)
  const [generateForce, setGenerateForce] = useState(false)

  const generate = useMutation({
    mutationFn: ({ materialIds, force }: { materialIds: string[]; force: boolean }) => {
      setGenerateForce(force)
      setSelectedMaterialIds(materialIds)
      return generateFlashcards(courseId, materialIds, force)
    },
    onSuccess: (next: FlashcardDeck, variables) => {
      setError(null)
      setNotice(null)
      setPicking(false)
      setSelectedMaterialIds(variables.materialIds)
      setDeck(next)
      setIndex(0)
      setRevealed(false)
    },
    onError: (err) => {
      setError(studyActionError(err, 'Could not generate flashcards.'))
    },
  })

  const selectVersion = useMutation({
    mutationFn: (versionId: string) => selectFlashcardVersion(courseId, selectedMaterialIds, versionId),
    onSuccess: (next) => {
      setDeck(next)
      setIndex(0)
      setRevealed(false)
      setError(null)
      setNotice(null)
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not open that deck version.')
    },
  })

  const reportVersion = useMutation({
    mutationFn: () => {
      if (!deck) {
        throw new Error('No deck')
      }
      return reportFlashcardVersion(deck.id)
    },
    onSuccess: (result) => {
      setError(null)
      if (result.retired) {
        setDeck(null)
        setPicking(true)
        setNotice('That version was retired after repeated quality reports. Choose materials to continue.')
      } else {
        setNotice('Thanks. Your quality report was recorded.')
      }
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not report this deck.')
    },
  })

  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyMaterials =
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY') ??
    []
  const readyCount = readyMaterials.length
  const enrolled = course.data?.enrolled === true
  const cards = deck?.cards ?? []
  const card = cards[index]
  const showPicker = picking || (!deck && !generate.isPending)

  function go(next: number) {
    if (next < 0 || next >= cards.length) {
      return
    }
    setIndex(next)
    setRevealed(false)
  }

  useEffect(() => {
    if (!card || picking || generate.isPending) {
      return
    }
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return
      }
      if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault()
        setRevealed((value) => !value)
      } else if (event.key === 'ArrowRight') {
        event.preventDefault()
        go(index + 1)
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault()
        go(index - 1)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [card, index, cards.length, picking, generate.isPending])

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>{course.data?.title ?? 'Course'}</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Flashcards'}
          title="Flashcards"
          description="Choose materials and study a shared deck. Existing versions are reused before a new one is created."
          actions={
            enrolled && readyCount > 0 && deck && !picking ? (
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
          title="Enroll to study flashcards"
          description="Flashcards are only available after you join this course."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title="No published materials yet"
          description="Once your professor publishes ready lecture PDFs, you can generate flashcards here."
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
                    onClick={() => generate.mutate({ materialIds: selectedMaterialIds, force: generateForce })}
                  >
                    Try again
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
          {notice ? <Panel className="mt-6"><p className="text-sm text-ink-soft">{notice}</p></Panel> : null}

          {generate.isPending ? (
            <section className="mt-8 animate-pulse-soft" aria-live="polite">
              <SectionLabel>Working</SectionLabel>
              <Panel className="mt-4">
                <p className="text-muted">{studyWorkingCopy('deck', generateForce)}</p>
              </Panel>
            </section>
          ) : null}

          {showPicker && !generate.isPending ? (
            <StudyMaterialPicker
              materials={readyMaterials}
              description="Select the lecture PDFs to study. You will resume your deck or receive the first shared version for this exact selection."
              confirmLabel={deck ? 'Open selected deck' : 'Start flashcards'}
              pending={generate.isPending}
              initialSelectedIds={selectedMaterialIds}
              onCancel={deck ? () => setPicking(false) : undefined}
              onConfirm={(materialIds) => generate.mutate({ materialIds, force: false })}
            />
          ) : null}

          {deck && cards.length === 0 && !generate.isPending && !picking ? (
            <EmptyState
              title="This deck has no cards"
              description="Try generating again, or choose a different set of materials."
            />
          ) : null}

          {deck && card && !generate.isPending && !picking ? (
            <section className="mt-8 animate-rise-delay">
              <div className="flex flex-wrap items-end justify-between gap-3">
                <SectionLabel>
                  Shared deck {deck.version} of {deck.availableVersions} · Card {index + 1} of {cards.length}
                </SectionLabel>
                <p className="text-xs text-muted">Space / Enter flip · ← → navigate</p>
              </div>
              <StudyVersionControls
                noun="deck"
                delivery={deck.delivery}
                versions={deck.versions}
                busy={selectVersion.isPending || reportVersion.isPending}
                onSelect={(versionId) => selectVersion.mutate(versionId)}
                onReport={() => reportVersion.mutate()}
              />

              <button
                type="button"
                onClick={() => setRevealed((value) => !value)}
                className={`mt-4 w-full rounded-2xl border px-6 py-8 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${
                  revealed
                    ? 'border-accent/35 bg-accent/5'
                    : 'border-line bg-white/80 hover:border-accent/30'
                }`}
              >
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">
                  {revealed ? 'Answer' : 'Prompt'}
                </p>
                <p
                  className={`mt-4 whitespace-pre-wrap leading-relaxed text-ink ${
                    revealed ? 'text-base' : 'font-display text-xl tracking-tight'
                  }`}
                >
                  {revealed ? card.back : card.front}
                </p>
                <p className="mt-6 text-sm text-muted">
                  {revealed ? 'Click to hide answer' : 'Click to reveal answer'}
                </p>
              </button>

              <div className="mt-4 flex flex-wrap gap-2">
                <Button type="button" variant="secondary" onClick={() => setRevealed((value) => !value)}>
                  {revealed ? 'Show prompt' : 'Show answer'}
                </Button>
                <Button type="button" variant="ghost" disabled={index === 0} onClick={() => go(index - 1)}>
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={index >= cards.length - 1}
                  onClick={() => go(index + 1)}
                >
                  Next
                </Button>
                {selectedMaterialIds.length > 0 ? (
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={generate.isPending}
                    onClick={() => generate.mutate({ materialIds: selectedMaterialIds, force: true })}
                  >
                    {nextDeckLabel(deck)}
                  </Button>
                ) : null}
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </Page>
  )
}

function nextDeckLabel(deck: FlashcardDeck) {
  if (deck.version < deck.availableVersions) {
    return 'Next shared deck'
  }
  if (deck.availableVersions < deck.maxVersions) {
    return 'Create another deck'
  }
  return 'Review from deck 1'
}
