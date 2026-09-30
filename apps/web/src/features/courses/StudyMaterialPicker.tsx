import { useEffect, useMemo, useRef, useState } from 'react'
import { Button, Panel, SectionLabel } from '../../shared/ui/ui.tsx'
import type { Material } from './material-api.ts'

type Props = {
  materials: Material[]
  title?: string
  description?: string
  confirmLabel: string
  pending?: boolean
  initialSelectedIds?: string[]
  onCancel?: () => void
  onConfirm: (materialIds: string[]) => void
}

export function StudyMaterialPicker({
  materials,
  title = 'Choose materials',
  description = 'Select which published lecture PDFs to use for generation.',
  confirmLabel,
  pending = false,
  initialSelectedIds,
  onCancel,
  onConfirm,
}: Props) {
  const ready = useMemo(
    () => materials.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY'),
    [materials],
  )
  const [selected, setSelected] = useState<Set<string>>(() => selectionFor(ready, [], initialSelectedIds))
  const initialized = useRef(ready.length > 0)

  useEffect(() => {
    if (ready.length === 0) {
      return
    }
    setSelected((current) => {
      if (!initialized.current) {
        initialized.current = true
        return selectionFor(ready, [...current], initialSelectedIds)
      }
      const readyIds = new Set(ready.map((material) => material.id))
      const kept = [...current].filter((id) => readyIds.has(id))
      if (kept.length > 0) {
        return new Set(kept)
      }
      // Materials often load after mount — restore the remembered selection once ready.
      return selectionFor(ready, [], initialSelectedIds)
    })
  }, [ready, initialSelectedIds])

  const allSelected = ready.length > 0 && selected.size === ready.length
  const someSelected = selected.size > 0

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(ready.map((material) => material.id)))
  }

  return (
    <section className="mt-8 animate-rise-delay">
      <SectionLabel>{title}</SectionLabel>
      <Panel className="mt-4">
        <p className="text-sm text-muted">{description}</p>

        <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-ink-soft">
            {selected.size} of {ready.length} selected
          </p>
          <Button type="button" variant="ghost" disabled={ready.length === 0 || pending} onClick={toggleAll}>
            {allSelected ? 'Clear all' : 'Select all'}
          </Button>
        </div>

        <ul className="mt-4 flex flex-col gap-2">
          {ready.map((material) => {
            const checked = selected.has(material.id)
            return (
              <li key={material.id}>
                <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-line bg-white/70 px-3 py-2.5 text-sm transition hover:border-accent/40">
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={checked}
                    disabled={pending}
                    onChange={() => toggle(material.id)}
                  />
                  <span>
                    <span className="font-medium text-ink">{material.title}</span>
                    <span className="mt-0.5 block text-xs text-muted">{material.fileName}</span>
                  </span>
                </label>
              </li>
            )
          })}
        </ul>

        <div className="mt-5 flex flex-col gap-2">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              disabled={!someSelected || pending}
              title={!someSelected ? 'Pick at least one material' : undefined}
              onClick={() => onConfirm([...selected])}
            >
              {pending ? 'Generating…' : confirmLabel}
            </Button>
            {onCancel ? (
              <Button type="button" variant="ghost" disabled={pending} onClick={onCancel}>
                Cancel
              </Button>
            ) : null}
          </div>
          {!someSelected ? (
            <p className="text-sm text-muted" role="status">
              Pick at least one material to continue.
            </p>
          ) : null}
        </div>
      </Panel>
    </section>
  )
}

function selectionFor(
  ready: Material[],
  currentIds: string[],
  initialSelectedIds?: string[],
): Set<string> {
  const readyIds = ready.map((material) => material.id)
  const readySet = new Set(readyIds)
  const kept = currentIds.filter((id) => readySet.has(id))
  if (kept.length > 0) {
    return new Set(kept)
  }
  const preferred = (initialSelectedIds ?? []).filter((id) => readySet.has(id))
  if (preferred.length > 0) {
    return new Set(preferred)
  }
  // One material: select it. Several: start empty so students choose deliberately.
  if (readyIds.length === 1) {
    return new Set(readyIds)
  }
  return new Set<string>()
}
