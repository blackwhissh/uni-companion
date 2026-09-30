import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { StudyVersionControls } from './StudyVersionControls.tsx'

describe('study version controls', () => {
  afterEach(() => {
    cleanup()
  })

  it('describes a resumed shared version', () => {
    render(
      <StudyVersionControls
        noun="deck"
        delivery="RESUMED"
        versions={[{ id: 'deck-1', version: 1, createdAt: '2026-09-29T00:00:00Z', current: true }]}
        onSelect={() => undefined}
        onReport={() => undefined}
      />,
    )

    expect(screen.getByText('Opening the shared deck saved for this material selection.')).toBeInTheDocument()
  })

  it('asks before recording a quality report', () => {
    const onReport = vi.fn()
    render(
      <StudyVersionControls
        noun="quiz"
        delivery="CREATED"
        versions={[{ id: 'quiz-1', version: 1, createdAt: '2026-09-29T00:00:00Z', current: true }]}
        onSelect={() => undefined}
        onReport={onReport}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Report quality issue' }))
    expect(onReport).not.toHaveBeenCalled()
    const dialog = screen.getByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Report' }))
    expect(onReport).toHaveBeenCalledTimes(1)
  })
})
