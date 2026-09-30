import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'success' | 'warn' | 'danger'

const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-ink text-white shadow-[0_1px_0_rgb(7_52_60_/_0.2)] hover:bg-[#145864] hover:shadow-[0_8px_18px_rgb(7_52_60_/_0.22)] focus-visible:outline-ink',
  secondary:
    'border border-ink/20 bg-white/70 text-ink hover:border-ink/40 hover:bg-white focus-visible:outline-ink',
  ghost: 'text-ink-soft hover:bg-mist focus-visible:outline-ink',
  success:
    'border border-ok/25 bg-emerald-50 text-ok hover:border-ok/45 hover:bg-emerald-100 focus-visible:outline-ok',
  warn:
    'border border-warn/30 bg-amber-50 text-warn hover:border-warn/50 hover:bg-amber-100 focus-visible:outline-warn',
  danger: 'border border-danger/30 bg-red-50 text-danger hover:border-danger/45 hover:bg-red-100 focus-visible:outline-danger',
}

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return (
    <button
      {...props}
      className={`inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition duration-200 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 ${variants[variant]} ${className}`}
    />
  )
}

export function ButtonLink({
  to,
  children,
  variant = 'primary',
  className = '',
}: {
  to: string
  children: ReactNode
  variant?: ButtonVariant
  className?: string
}) {
  return (
    <Link
      to={to}
      className={`inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition duration-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 ${variants[variant]} ${className}`}
    >
      {children}
    </Link>
  )
}

export function Page({
  children,
  narrow = false,
  className = '',
}: {
  children: ReactNode
  narrow?: boolean
  className?: string
}) {
  return (
    <main className={`mx-auto w-full px-6 py-10 ${narrow ? 'max-w-lg' : 'max-w-3xl'} ${className}`}>
      {children}
    </main>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: ReactNode
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="animate-rise flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow ? <p className="text-sm font-medium text-muted">{eyebrow}</p> : null}
        <h1 className="font-display mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">{title}</h1>
        {description ? <p className="mt-2 max-w-2xl text-base leading-relaxed text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="surface-panel animate-rise-delay mt-8 rounded-2xl px-6 py-10 text-center">
      <p className="font-display text-xl font-semibold text-ink">{title}</p>
      <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-muted">{description}</p>
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </div>
  )
}

export function StatusPill({
  tone = 'neutral',
  children,
  pulse = false,
}: {
  tone?: 'neutral' | 'ok' | 'warn' | 'danger' | 'accent'
  children: ReactNode
  pulse?: boolean
}) {
  const tones = {
    neutral: 'bg-mist text-ink-soft',
    ok: 'bg-emerald-50 text-ok',
    warn: 'bg-amber-50 text-warn',
    danger: 'bg-red-50 text-danger',
    accent: 'bg-teal-50 text-accent-deep',
  }
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide ${tones[tone]} ${pulse ? 'animate-pulse-soft' : ''}`}
    >
      {children}
    </span>
  )
}

export function Panel({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`surface-panel animate-rise-delay rounded-2xl p-5 ${className}`}>{children}</div>
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-muted">{children}</h2>
}

export function Alert({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-lg border border-danger/20 bg-red-50 px-3 py-2 text-sm text-danger">
      {children}
    </p>
  )
}

export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel = 'Cancel',
  danger = false,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  body: string
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  if (!open) {
    return null
  }
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 p-4 backdrop-blur-[2px]"
      role="presentation"
      onClick={onCancel}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-body"
        className="w-full max-w-md rounded-2xl border border-line bg-white p-5 shadow-[0_24px_60px_rgb(7_52_60_/_0.28)]"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="confirm-dialog-title" className="font-display text-xl font-semibold text-ink">
          {title}
        </h2>
        <p id="confirm-dialog-body" className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-muted">
          {body}
        </p>
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" autoFocus onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button type="button" variant={danger ? 'danger' : 'primary'} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}

export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex items-center gap-1 text-sm font-medium text-muted transition hover:text-ink">
      <span aria-hidden>←</span> {children}
    </Link>
  )
}
