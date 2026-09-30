import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { useAuth } from '../features/auth/use-auth.ts'
import { shouldSkipReturnPath } from '../features/auth/auth-session-sync.ts'
import { Page } from '../shared/ui/ui.tsx'

function SessionLoading() {
  return (
    <Page narrow>
      <p className="animate-pulse-soft text-muted">Restoring your session…</p>
    </Page>
  )
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth()
  const location = useLocation()
  if (!ready) {
    return <SessionLoading />
  }
  if (!user) {
    // After logout, do not stash the previous page as the next login return path.
    if (shouldSkipReturnPath()) {
      return <Navigate to="/login" replace />
    }
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children
}

export function RequireRole({ role, children }: { role: string; children: ReactNode }) {
  const { user, ready } = useAuth()
  const location = useLocation()
  if (!ready) {
    return <SessionLoading />
  }
  if (!user) {
    if (shouldSkipReturnPath()) {
      return <Navigate to="/login" replace />
    }
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (!user.roles.includes(role)) {
    return <NotAllowed />
  }
  return children
}

export function NotAllowed() {
  return (
    <Page narrow>
      <h1 className="font-display text-2xl font-semibold text-ink">Not allowed</h1>
      <p className="mt-2 text-sm text-muted">
        You do not have permission to view this page. If you think this is a mistake, ask a course admin for access.
      </p>
      <p className="mt-6">
        <Link to="/courses" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
          Back to courses
        </Link>
      </p>
    </Page>
  )
}
