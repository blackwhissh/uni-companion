import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { SectionLabel } from '../../shared/ui/ui.tsx'
import { openMaterialAtPage } from './material-api.ts'
import type { RagCitation } from './rag-api.ts'

export function QaSources({
  courseId,
  citations,
  answer,
}: {
  courseId: string
  citations: RagCitation[]
  answer: string
}) {
  const [active, setActive] = useState<number | null>(null)
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const [openingId, setOpeningId] = useState<string | null>(null)
  const [openError, setOpenError] = useState<string | null>(null)
  const mentionCounts = useMemo(() => countMentions(answer, citations.length), [answer, citations.length])
  const focusTerms = useMemo(() => significantTerms(answer), [answer])

  useEffect(() => {
    function onHash() {
      const match = /^#qa-source-(\d+)$/.exec(window.location.hash)
      if (!match) {
        return
      }
      const n = Number(match[1])
      if (n < 1 || n > citations.length) {
        return
      }
      setActive(n)
      const el = document.getElementById(`qa-source-${n}`)
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      window.setTimeout(() => setActive((current) => (current === n ? null : current)), 2200)
    }
    onHash()
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [citations.length])

  if (citations.length === 0) {
    return null
  }

  return (
    <div className="rounded-xl border border-line/80 bg-mist/40 px-3 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <SectionLabel>Sources used</SectionLabel>
        <Link to={`/courses/${courseId}`} className="text-xs font-medium text-accent-deep hover:underline">
          Open course materials
        </Link>
      </div>

      {openError ? <p className="mt-3 text-sm text-danger">{openError}</p> : null}

      <ol className="mt-3 flex flex-col gap-2">
        {citations.map((citation, index) => {
          const n = index + 1
          const isActive = active === n
          const isOpen = expanded[n] === true || isActive
          const long = citation.excerpt.length > 220
          const shown = !isOpen
            ? null
            : !long || expanded[n] === true
              ? citation.excerpt
              : `${citation.excerpt.slice(0, 200).trim()}…`
          const mentions = mentionCounts[n] ?? 0
          const opening = openingId === citation.materialId

          return (
            <li key={`${citation.materialId}-${citation.pageNumber}-${n}`} id={`qa-source-${n}`}>
              <article
                className={`scroll-mt-24 overflow-hidden rounded-xl border transition ${
                  isActive
                    ? 'border-accent bg-accent/5 shadow-[0_0_0_3px_rgb(47_158_138_/_0.18)]'
                    : 'border-line bg-white/80 hover:border-accent/35'
                }`}
              >
                <div className="flex items-stretch gap-0">
                  <div className="w-1 shrink-0 bg-accent/70" aria-hidden />
                  <div className="min-w-0 flex-1 px-3 py-2.5">
                    <div className="flex flex-wrap items-start gap-3">
                      <span className="inline-flex h-8 min-w-8 shrink-0 items-center justify-center rounded-lg bg-accent/15 text-sm font-semibold text-accent-deep">
                        {n}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-medium text-ink">{citation.title}</h3>
                          <span className="rounded-md border border-line bg-mist/80 px-2 py-0.5 text-xs font-medium text-ink-soft">
                            Page {citation.pageNumber}
                          </span>
                          {mentions > 0 ? (
                            <span className="text-xs text-muted">
                              Cited {mentions}× in answer
                            </span>
                          ) : null}
                        </div>
                        {citation.sectionHint ? (
                          <p className="mt-1.5 text-sm font-medium text-accent-deep">{citation.sectionHint}</p>
                        ) : null}
                      </div>
                    </div>

                    {shown ? (
                      <blockquote className="mt-2 border-l-2 border-accent/40 bg-accent/[0.04] py-2 pl-3 pr-2 text-sm leading-relaxed text-ink">
                        “{highlightPassage(shown, focusTerms, isActive)}”
                      </blockquote>
                    ) : null}

                    <div className="mt-2 flex flex-wrap items-center gap-3">
                      <button
                        type="button"
                        className="text-sm font-medium text-accent-deep hover:underline"
                        onClick={() => setExpanded((prev) => ({ ...prev, [n]: !isOpen }))}
                      >
                        {isOpen ? (long && expanded[n] === true ? 'Show less' : 'Hide passage') : 'Show passage'}
                      </button>
                      <button
                        type="button"
                        className="text-sm font-medium text-accent-deep hover:underline disabled:opacity-60"
                        disabled={opening}
                        onClick={async () => {
                          setOpenError(null)
                          setOpeningId(citation.materialId)
                          try {
                            await openMaterialAtPage(citation.materialId, citation.pageNumber)
                          } catch {
                            setOpenError('Could not open that PDF. Try again from course materials.')
                          } finally {
                            setOpeningId(null)
                          }
                        }}
                      >
                        {opening ? 'Opening…' : `Open PDF at page ${citation.pageNumber}`}
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

function countMentions(answer: string, citationCount: number): Record<number, number> {
  const counts: Record<number, number> = {}
  const pattern = /\[(\d+)]/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(answer)) !== null) {
    const n = Number(match[1])
    if (n >= 1 && n <= citationCount) {
      counts[n] = (counts[n] ?? 0) + 1
    }
  }
  return counts
}

const STOP = new Set([
  'a', 'an', 'the', 'and', 'or', 'of', 'to', 'in', 'on', 'for', 'is', 'are', 'was', 'were',
  'be', 'as', 'by', 'with', 'from', 'that', 'this', 'it', 'its', 'you', 'your', 'what',
  'how', 'why', 'when', 'which', 'about', 'into', 'than', 'then', 'also', 'can', 'do',
  'does', 'did', 'will', 'would', 'should', 'could', 'not', 'no', 'yes', 'direct', 'answer',
  'explanation', 'key', 'terms', 'der', 'die', 'das', 'und', 'oder', 'ein', 'eine', 'ist',
  'sind', 'nicht', 'mit', 'auf', 'für', 'den', 'dem', 'des',
])

function significantTerms(text: string): Set<string> {
  const out = new Set<string>()
  for (const raw of text.toLowerCase().split(/[^a-z0-9äöüß]+/i)) {
    if (!raw) {
      continue
    }
    if (/^\d+$/.test(raw)) {
      out.add(raw)
      continue
    }
    if (raw.length < 3 || STOP.has(raw)) {
      continue
    }
    out.add(raw)
  }
  return out
}

function highlightPassage(excerpt: string, focus: Set<string>, emphasize: boolean): ReactNode {
  if (focus.size === 0) {
    return excerpt
  }
  const parts = excerpt.split(/([^a-z0-9äöüß]+)/i)
  return parts.map((part, index) => {
    const key = part.toLowerCase()
    if (focus.has(key) || (/^\d+$/.test(part) && focus.has(part))) {
      return (
        <mark
          key={index}
          className={
            emphasize
              ? 'rounded-sm bg-accent/35 px-0.5 text-ink'
              : 'rounded-sm bg-accent/20 px-0.5 text-ink'
          }
        >
          {part}
        </mark>
      )
    }
    return part
  })
}
