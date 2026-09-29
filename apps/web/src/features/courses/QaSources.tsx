import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { SectionLabel } from '../../shared/ui/ui.tsx'
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
  const mentionCounts = useMemo(() => countMentions(answer, citations.length), [answer, citations.length])

  useEffect(() => {
    function onHash() {
      const match = /^#qa-source-(\d+)$/.exec(window.location.hash)
      if (!match) {
        return
      }
      const n = Number(match[1])
      setActive(n)
      window.setTimeout(() => setActive((current) => (current === n ? null : current)), 2200)
    }
    onHash()
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  if (citations.length === 0) {
    return null
  }

  return (
    <div className="mt-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <SectionLabel>Sources used</SectionLabel>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted">
            Passages the answer cites. Tap a numbered chip in the answer to jump here.
          </p>
        </div>
        <Link to={`/courses/${courseId}`} className="text-sm font-medium text-accent-deep hover:underline">
          Open course materials
        </Link>
      </div>

      <ol className="mt-5 flex flex-col gap-4">
        {citations.map((citation, index) => {
          const n = index + 1
          const isActive = active === n
          const isOpen = expanded[n] === true
          const long = citation.excerpt.length > 220
          const shown = !long || isOpen ? citation.excerpt : `${citation.excerpt.slice(0, 200).trim()}…`
          const mentions = mentionCounts[n] ?? 0

          return (
            <li key={`${citation.materialId}-${citation.pageNumber}-${n}`} id={`qa-source-${n}`}>
              <article
                className={`scroll-mt-24 overflow-hidden rounded-2xl border transition ${
                  isActive
                    ? 'border-accent bg-accent/5 shadow-[0_0_0_3px_rgb(47_158_138_/_0.18)]'
                    : 'border-line bg-white/75 hover:border-accent/35'
                }`}
              >
                <div className="flex items-stretch gap-0">
                  <div className="w-1.5 shrink-0 bg-accent/70" aria-hidden />
                  <div className="min-w-0 flex-1 px-5 py-4">
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

                    <blockquote className="mt-3 border-l-2 border-accent/30 pl-3 text-sm leading-relaxed text-ink-soft">
                      “{shown}”
                    </blockquote>

                    {long ? (
                      <button
                        type="button"
                        className="mt-2 text-sm font-medium text-accent-deep hover:underline"
                        onClick={() => setExpanded((prev) => ({ ...prev, [n]: !isOpen }))}
                      >
                        {isOpen ? 'Show less' : 'Show more'}
                      </button>
                    ) : null}
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
