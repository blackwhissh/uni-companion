import { useState } from 'react'
import { Button, ConfirmDialog } from '../../shared/ui/ui.tsx'
import type { StudyDelivery, StudyVersion } from './study-api.ts'

type Props = {
  noun: 'deck' | 'quiz'
  delivery: StudyDelivery
  versions: StudyVersion[]
  busy?: boolean
  onSelect: (id: string) => void
  onReport: () => void
}

export function StudyVersionControls({ noun, delivery, versions, busy = false, onSelect, onReport }: Props) {
  const [confirmReport, setConfirmReport] = useState(false)

  return (
    <div className="mt-4 rounded-xl border border-line bg-mist/50 px-4 py-3">
      <p className="text-sm text-ink-soft" aria-live="polite">
        {deliveryMessage(delivery, noun)}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <span className="mr-1 text-xs font-semibold uppercase tracking-[0.12em] text-muted">Versions</span>
        {versions.map((version) => (
          <Button
            key={version.id}
            type="button"
            variant={version.current ? 'secondary' : 'ghost'}
            disabled={busy || version.current}
            onClick={() => onSelect(version.id)}
          >
            {noun === 'deck' ? 'Deck' : 'Quiz'} {version.version}
          </Button>
        ))}
        <Button type="button" variant="ghost" disabled={busy} onClick={() => setConfirmReport(true)}>
          Report quality issue
        </Button>
      </div>

      <ConfirmDialog
        open={confirmReport}
        title={`Report this ${noun}?`}
        body={`Report this ${noun} as a quality issue? Repeated reports can retire it for everyone.`}
        confirmLabel="Report"
        danger
        onCancel={() => setConfirmReport(false)}
        onConfirm={() => {
          setConfirmReport(false)
          onReport()
        }}
      />
    </div>
  )
}

function deliveryMessage(delivery: StudyDelivery, noun: 'deck' | 'quiz') {
  switch (delivery) {
    case 'CREATED':
      return `Fresh ${noun} for the lectures you picked — you’re the first to use this set.`
    case 'REUSED':
      return `Opening the ${noun} already made for these lectures.`
    case 'NEXT_SHARED':
      return `Opening the next ${noun} for these lectures.`
    case 'CYCLED':
      return `Back to ${noun} 1 for these lectures.`
    case 'SELECTED':
      return `Opening the ${noun} version you picked.`
    case 'RESUMED':
      return `Picking up the ${noun} you left for these lectures.`
    default:
      return `Opening the ${noun} for these lectures.`
  }
}
