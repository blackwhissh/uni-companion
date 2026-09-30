import { Outlet } from 'react-router'
import { AppHeader } from './AppHeader.tsx'
import { AuthSessionSync } from './AuthSessionSync.tsx'

export function AppShell() {
  return (
    <div className="flex min-h-screen flex-col">
      <AuthSessionSync />
      <AppHeader />
      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  )
}
