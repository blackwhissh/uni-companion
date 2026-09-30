import { NavLink, Link, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../features/auth/use-auth.ts'
import { homePathFor } from '../features/auth/home-path.ts'
import { Button } from '../shared/ui/ui.tsx'

export function AppHeader() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const home = user ? homePathFor(user.roles) : '/'
  const isStaff = user?.roles.includes('COURSE_ADMIN') || user?.roles.includes('ADMIN')
  const shortName = user ? shortDisplayName(user.displayName) : ''

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `shrink-0 rounded-md px-2 py-1.5 text-sm font-medium transition ${
      isActive ? 'bg-mist text-ink' : 'text-muted hover:bg-mist/70 hover:text-ink'
    }`

  async function onLogout() {
    await logout()
    queryClient.clear()
    navigate('/login', { replace: true, state: {} })
  }

  return (
    <header className="sticky top-0 z-20 border-b border-line/80 bg-paper/80 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-2 px-3 sm:h-auto sm:gap-4 sm:px-6 sm:py-3.5">
        <Link to={home} className="group flex min-w-0 shrink items-center gap-2 sm:gap-2.5">
          <span
            aria-hidden
            className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-ink text-sm font-semibold text-accent transition group-hover:scale-105"
          >
            UC
          </span>
          <span className="hidden truncate font-display text-lg font-semibold tracking-tight text-ink sm:inline">
            Uni Companion
          </span>
        </Link>
        <nav className="ml-auto flex max-w-full items-center justify-end gap-0.5 overflow-x-auto whitespace-nowrap sm:gap-2">
          {user ? (
            <>
              <span
                className="mr-1 max-w-[6.5rem] truncate text-xs font-medium text-ink-soft sm:max-w-[9rem] sm:text-sm"
                title={user.displayName}
              >
                <span className="sm:hidden">{shortName}</span>
                <span className="hidden sm:inline">{user.displayName}</span>
              </span>
              <NavLink to="/courses" className={linkClass}>
                Courses
              </NavLink>
              {isStaff ? (
                <NavLink to="/admin/courses" className={linkClass}>
                  <span className="sm:hidden">Admin</span>
                  <span className="hidden sm:inline">Course console</span>
                </NavLink>
              ) : null}
              <NavLink to="/profile" className={linkClass}>
                Profile
              </NavLink>
              <Button type="button" variant="ghost" className="!shrink-0 !px-2.5" onClick={() => void onLogout()}>
                Log out
              </Button>
            </>
          ) : (
            <>
              <NavLink to="/login" className={linkClass}>
                Log in
              </NavLink>
              <Link
                to="/register"
                className="ml-1 inline-flex shrink-0 items-center justify-center rounded-lg bg-ink px-3 py-1.5 text-sm font-medium text-white transition hover:bg-ink-soft"
              >
                Register
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}

function shortDisplayName(displayName: string) {
  const trimmed = displayName.trim()
  if (!trimmed) {
    return 'Account'
  }
  const parts = trimmed.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return parts[0].length > 10 ? `${parts[0].slice(0, 9)}…` : parts[0]
  }
  return `${parts[0]} ${parts[1][0]}.`
}
