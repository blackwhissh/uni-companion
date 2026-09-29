import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, Page } from '../../shared/ui/ui.tsx'

export function AuthForm({
  title,
  subtitle,
  submitLabel,
  onSubmit,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  submitLabel: string
  onSubmit: () => Promise<void>
  children: ReactNode
  footer: ReactNode
}) {
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setPending(true)
    try {
      await onSubmit()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setPending(false)
    }
  }

  return (
    <Page narrow className="animate-rise py-14">
      <div className="surface-panel rounded-2xl p-6 sm:p-8">
        <p className="text-sm font-semibold uppercase tracking-[0.16em] text-accent-deep">Uni Companion</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight text-ink">{title}</h1>
        {subtitle ? <p className="mt-2 text-sm leading-relaxed text-muted">{subtitle}</p> : null}
        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit}>
          {children}
          {error ? <Alert>{error}</Alert> : null}
          <Button type="submit" disabled={pending} className="mt-1 w-full !py-2.5">
            {submitLabel}
          </Button>
        </form>
        <p className="mt-5 text-sm text-muted">{footer}</p>
      </div>
    </Page>
  )
}

export function Field({
  label,
  type,
  value,
  onChange,
  autoComplete,
  hint,
}: {
  label: string
  type: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  hint?: string
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm font-medium text-ink-soft">
      {label}
      <input
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-line bg-white/80 px-3 py-2.5 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25"
      />
      {hint ? <span className="text-xs font-normal text-muted">{hint}</span> : null}
    </label>
  )
}

const selectClassName =
  'appearance-none rounded-lg border border-line bg-white/80 bg-[length:1rem] bg-[right_0.75rem_center] bg-no-repeat px-3 py-2.5 pr-10 text-base font-normal text-ink shadow-[inset_0_1px_0_rgb(7_52_60_/_0.03)] outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25'

const selectChevron =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3E%3Cpath stroke='%235a7378' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='m6 8 4 4 4-4'/%3E%3C/svg%3E\")"

export function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder,
  hint,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: ReadonlyArray<{ value: string; label: string }>
  placeholder?: string
  hint?: string
}) {
  const id = `select-${label.toLowerCase().replace(/\s+/g, '-')}`
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink-soft">
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={selectClassName}
        style={{ backgroundImage: selectChevron }}
      >
        {placeholder ? (
          <option value="" disabled>
            {placeholder}
          </option>
        ) : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {hint ? <span className="text-xs font-normal text-muted">{hint}</span> : null}
    </div>
  )
}
