import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { StudyMarkdown } from './StudyMarkdown.tsx'

describe('StudyMarkdown', () => {
  afterEach(() => cleanup())

  it('renders headings, lists, emphasis, and citation chips', () => {
    render(
      <StudyMarkdown
        markdown={`## Direct answer
Consensus keeps replicas consistent [1].

## Key terms
- **Consensus** — agreement among replicas [1]
`}
        citations={[{ index: 1, title: 'Lecture 1', pageNumber: 3, sectionHint: 'Chapter 1 — Basics' }]}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Direct answer' })).toBeInTheDocument()
    expect(screen.getByText('Consensus', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /Source 1:/ })[0]).toHaveAttribute('href', '#qa-source-1')
    expect(screen.getAllByRole('link', { name: /Source 1:/ })[0]).toHaveAttribute(
      'title',
      'Source 1: Lecture 1, Chapter 1 — Basics, page 3',
    )
    expect(screen.getByText(/agreement among replicas/)).toBeInTheDocument()
  })
})
