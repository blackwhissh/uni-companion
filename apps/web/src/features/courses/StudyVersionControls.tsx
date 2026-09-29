import { Button } from '../../shared/ui/ui.tsx'
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
        <Button
          type="button"
          variant="ghost"
          disabled={busy}
          onClick={() => {
            if (
              window.confirm(
                `Report this ${noun} as a quality issue? Repeated reports can retire it for everyone.`,
              )
            ) {
              onReport()
            }
          }}
        >
          Report quality issue
        </Button>
      </div>
    </div>
  )
}

function deliveryMessage(delivery: StudyDelivery, noun: 'deck' | 'quiz') {
  switch (delivery) {
    case 'CREATED':
      return `A new shared ${noun} was created for this material selection.`
    case 'REUSED':
      return `Opening the shared ${noun} for this material selection.`
    case 'NEXT_SHARED':
      return `Opening the next shared ${noun} for this material selection.`
    case 'CYCLED':
      return `Back to shared ${noun} 1 for this material selection.`
    case 'SELECTED':
      return `Opening the ${noun} version you selected.`
    case 'RESUMED':
      return `Opening the shared ${noun} saved for this material selection.`
    default:
      return `Opening the shared ${noun} for this material selection.`
  }
}
