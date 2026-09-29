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

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-2.5 py-1.5 text-sm font-medium transition ${
      isActive ? 'bg-mist text-ink' : 'text-muted hover:bg-mist/70 hover:text-ink'
    }`

  async function onLogout() {
    await logout()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  return (
    <header className="sticky top-0 z-20 border-b border-line/80 bg-paper/80 backdrop-blur-md">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-6 py-3.5">
        <Link to={home} className="group flex items-center gap-2.5">
          <span
            aria-hidden
            className="grid h-8 w-8 place-items-center rounded-lg bg-ink text-sm font-semibold text-accent transition group-hover:scale-105"
          >
            UC
          </span>
          <span className="font-display text-lg font-semibold tracking-tight text-ink">Uni Companion</span>
        </Link>
        <nav className="flex flex-wrap items-center justify-end gap-1 sm:gap-2">
          {user ? (
            <>
              <span className="mr-1 max-w-[9rem] truncate text-sm text-muted" title={user.displayName}>
                {user.displayName}
              </span>
              <NavLink to="/courses" className={linkClass}>
                Courses
              </NavLink>
              {user.roles.includes('COURSE_ADMIN') || user.roles.includes('ADMIN') ? (
                <NavLink to="/admin/courses" className={linkClass}>
                  Course console
                </NavLink>
              ) : null}
              <NavLink to="/profile" className={linkClass}>
                Profile
              </NavLink>
              <Button type="button" variant="ghost" className="!px-2.5" onClick={() => void onLogout()}>
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
                className="ml-1 inline-flex items-center justify-center rounded-lg bg-ink px-3.5 py-1.5 text-sm font-medium text-white transition hover:bg-ink-soft"
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
