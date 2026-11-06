import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import { GroundedNote } from '../../shared/ui/GroundedNote.tsx'
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
import { clearStudyGeneration, loadStudyGeneration, saveStudyGeneration } from './study-generation-storage.ts'
import { loadMaterialSelection, saveMaterialSelection } from './material-selection-storage.ts'
import { StudyMaterialPicker } from './StudyMaterialPicker.tsx'
import { StudyVersionControls } from './StudyVersionControls.tsx'
import { generateFlashcards, reportFlashcardVersion, selectFlashcardVersion } from './study-api.ts'
import type { FlashcardDeck } from './study-api.ts'
import { studyActionError, studyWorkingCopy } from './study-ui.ts'

export function CourseFlashcardsPage() {
  const { courseId = '' } = useParams()
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

  const [deck, setDeck] = useState<FlashcardDeck | null>(null)
  const pendingAtMount = useRef(courseId ? loadStudyGeneration('flashcards', courseId) : null)
  const [selectedMaterialIds, setSelectedMaterialIds] = useState<string[]>(() =>
    pendingAtMount.current?.materialIds ?? (courseId ? loadMaterialSelection('flashcards', courseId) : []),
  )
  const [index, setIndex] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(() =>
    pendingAtMount.current ? 'Still putting your deck together…' : null,
  )
  const [picking, setPicking] = useState(() => !pendingAtMount.current)
  const [blockingForResume, setBlockingForResume] = useState(() => !!pendingAtMount.current)
  const [generateForce, setGenerateForce] = useState(() => pendingAtMount.current?.force === true)
  const resumedGeneration = useRef(false)

  const generate = useMutation({
    mutationFn: ({ materialIds, force }: { materialIds: string[]; force: boolean }) => {
      setGenerateForce(force)
      setSelectedMaterialIds(materialIds)
      setNotice(null)
      setPicking(false)
      saveStudyGeneration('flashcards', courseId, {
        materialIds,
        force,
        startedAt: Date.now(),
      })
      return generateFlashcards(courseId, materialIds, force)
    },
    onSuccess: (next: FlashcardDeck, variables) => {
      clearStudyGeneration('flashcards', courseId)
      setBlockingForResume(false)
      setError(null)
      setNotice(next.cards.length > 0 ? `This deck has ${cardCountLabel(next.cards.length)}.` : null)
      setPicking(false)
      setSelectedMaterialIds(variables.materialIds)
      setDeck(next)
      setIndex(0)
      setRevealed(false)
    },
    onError: (err) => {
      clearStudyGeneration('flashcards', courseId)
      setBlockingForResume(false)
      setError(studyActionError(err, 'Could not generate flashcards.'))
    },
  })

  useEffect(() => {
    if (resumedGeneration.current || !courseId || course.data?.enrolled !== true) {
      return
    }
    const pending = pendingAtMount.current ?? loadStudyGeneration('flashcards', courseId)
    if (!pending) {
      setBlockingForResume(false)
      return
    }
    resumedGeneration.current = true
    setBlockingForResume(true)
    setPicking(false)
    setNotice('Still putting your deck together…')
    generate.mutate({ materialIds: pending.materialIds, force: pending.force })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, course.data?.enrolled])

  const selectVersion = useMutation({
    mutationFn: (versionId: string) => selectFlashcardVersion(courseId, selectedMaterialIds, versionId),
    onSuccess: (next) => {
      setDeck(next)
      setIndex(0)
      setRevealed(false)
      setError(null)
      setNotice(`Opened deck ${next.version} · ${cardCountLabel(next.cards.length)}.`)
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
  const showPicker = !blockingForResume && !generate.isPending && (picking || !deck)
  const showWorking = generate.isPending || blockingForResume
  const sourceTitles = readyMaterials
    .filter((material) => selectedMaterialIds.includes(material.id))
    .map((material) => material.title)
  const generating = generate.isPending || blockingForResume

  function startGeneration(materialIds: string[], force: boolean) {
    if (generating) {
      return
    }
    saveMaterialSelection('flashcards', courseId, materialIds)
    generate.mutate({ materialIds, force })
  }

  function go(next: number) {
    if (next < 0 || next >= cards.length) {
      return
    }
    setIndex(next)
    setRevealed(false)
  }

  useEffect(() => {
    if (!card || picking || generating) {
      return
    }
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'BUTTON' ||
          target.isContentEditable ||
          target.closest('button'))
      ) {
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
  }, [card, index, cards.length, picking, generating])

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
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Flashcards'}
          title="Flashcards"
          description="Pick the lectures you want, then flip through a deck. If someone already made one for that exact set, you’ll get it instead of waiting on a new one."
          actions={
            enrolled && readyCount > 0 && deck && !picking ? (
              <Button type="button" variant="secondary" disabled={generating} onClick={() => setPicking(true)}>
                Change materials
              </Button>
            ) : null
          }
        />
      </div>

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Join to study flashcards"
          description="Join the course first, then flip through cards from the lectures."
        />
      ) : null}

      {enrolled && readyCount === 0 ? (
        <EmptyState
          title="No lectures for cards yet"
          description="Once your professor publishes the PDFs, you can build a deck from them here."
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
                    disabled={generating}
                    onClick={() => startGeneration(selectedMaterialIds, generateForce)}
                  >
                    Try again
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
          {notice ? (
            <Panel className="mt-6">
              <p className="text-sm text-ink-soft">{notice}</p>
            </Panel>
          ) : null}

          {showWorking ? (
            <section className="mt-8 animate-pulse-soft" aria-live="polite">
              <SectionLabel>Working</SectionLabel>
              <Panel className="mt-4">
                <p className="text-muted">
                  {blockingForResume && !generate.isPending
                    ? 'Still putting your deck together…'
                    : studyWorkingCopy('deck', generateForce)}
                </p>
                <p className="mt-2 text-sm text-muted">Feel free to refresh — we’ll keep working in the background.</p>
              </Panel>
            </section>
          ) : null}

          {showPicker ? (
            <StudyMaterialPicker
              materials={readyMaterials}
              description="Choose the lectures to study. If a deck already exists for that exact set, you’ll pick up there; otherwise we’ll make the first one."
              confirmLabel={deck ? 'Open this deck' : 'Start flashcards'}
              pending={generating}
              initialSelectedIds={selectedMaterialIds}
              onCancel={deck ? () => setPicking(false) : undefined}
              onConfirm={(materialIds) => startGeneration(materialIds, false)}
            />
          ) : null}

          {deck && cards.length === 0 && !generating && !picking ? (
            <EmptyState
              title="This deck came back empty"
              description="Try again, or pick a different set of lectures."
            />
          ) : null}

          {deck && card && !generating && !picking ? (
            <section className="mt-8 animate-rise-delay">
              <div className="flex flex-wrap items-end justify-between gap-3">
                <SectionLabel>
                  Deck {deck.version} of {deck.availableVersions} · {cardCountLabel(cards.length)} · Card{' '}
                  {index + 1} of {cards.length}
                </SectionLabel>
                <p className="text-xs text-muted">Space / Enter flip · ← → navigate · Enter on Next advances</p>
              </div>
              {sourceTitles.length > 0 ? (
                <p className="mt-2 text-sm text-muted">Sources: {sourceTitles.join(' · ')}</p>
              ) : null}
              <GroundedNote className="mt-2" />
              <StudyVersionControls
                noun="deck"
                delivery={deck.delivery}
                versions={deck.versions}
                busy={selectVersion.isPending || reportVersion.isPending || generating}
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
                <Button type="button" variant="ghost" disabled={index >= cards.length - 1} onClick={() => go(index + 1)}>
                  Next
                </Button>
                {selectedMaterialIds.length > 0 ? (
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={generating}
                    onClick={() => startGeneration(selectedMaterialIds, true)}
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
    return 'Next deck'
  }
  if (deck.availableVersions < deck.maxVersions) {
    return 'Create another deck'
  }
  return 'Review from deck 1'
}

function cardCountLabel(count: number) {
  return count === 1 ? '1 card' : `${count} cards`
}
