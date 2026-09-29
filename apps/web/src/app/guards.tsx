import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from '../features/auth/use-auth.ts'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}

export function RequireRole({ role, children }: { role: string; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (!user.roles.includes(role)) {
    return <Navigate to="/courses" replace />
  }
  return children
}
