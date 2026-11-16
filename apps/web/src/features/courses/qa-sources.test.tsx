import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QaSources } from './QaSources.tsx'
import * as materialApi from './material-api.ts'
import type { RagCitation } from './rag-api.ts'

const citations: RagCitation[] = [
  {
    materialId: 'material-1',
    title: 'Lecture 1',
    sectionHint: 'Chapter 1 — Consensus',
    pageNumber: 3,
    excerpt: 'A'.repeat(450),
  },
  {
    materialId: 'material-2',
    title: 'Lecture 2',
    pageNumber: 7,
    excerpt: 'Short passage about Verfügbarkeit in distributed systems.',
  },
]

function renderSources(answer = 'Consensus needs a majority [1]. See also [1] and [2].') {
  return render(
    <MemoryRouter>
      <QaSources courseId="course-1" citations={citations} answer={answer} />
    </MemoryRouter>,
  )
}

describe('QaSources', () => {
  afterEach(() => {
    cleanup()
    window.location.hash = ''
    vi.restoreAllMocks()
  })

  it('shows mention counts and expands long excerpts', () => {
    renderSources()

    expect(screen.getByText('Sources used')).toBeInTheDocument()
    expect(screen.getByText(/Cited 2× in answer/)).toBeInTheDocument()
    expect(screen.getByText(/Cited 1× in answer/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show more' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Show more' }))
    expect(screen.getByRole('button', { name: 'Show less' })).toBeInTheDocument()
  })

  it('highlights German umlaut terms from the answer in excerpts', () => {
    render(
      <MemoryRouter>
        <QaSources
          courseId="course-1"
          citations={[citations[1]]}
          answer="Verfügbarkeit bleibt zentral für verteilte Systeme."
        />
      </MemoryRouter>,
    )

    const marks = screen.getAllByText('Verfügbarkeit')
    expect(marks.some((el) => el.tagName === 'MARK')).toBe(true)
  })

  it('jumps to a source when the hash matches a citation chip', async () => {
    const scrollIntoView = vi.fn()
    HTMLElement.prototype.scrollIntoView = scrollIntoView

    renderSources()
    window.location.hash = '#qa-source-2'
    window.dispatchEvent(new HashChangeEvent('hashchange'))

    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalled()
    })
    const article = document.getElementById('qa-source-2')?.querySelector('article')
    expect(article?.className).toMatch(/border-accent/)
  })

  it('opens the PDF at the cited page', async () => {
    const openAtPage = vi.spyOn(materialApi, 'openMaterialAtPage').mockResolvedValue(undefined)
    renderSources()

    fireEvent.click(screen.getByRole('button', { name: 'Open PDF at page 3' }))

    await waitFor(() => {
      expect(openAtPage).toHaveBeenCalledWith('material-1', 3)
    })
  })
})
