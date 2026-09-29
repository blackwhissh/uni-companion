import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { getAccessToken, setAccessToken } from '../../shared/api/access-token.ts'
import { loginAccount, logoutAccount, registerAccount, restoreSession } from './auth-api.ts'
import { AuthContext } from './use-auth.ts'
import type { User } from './types.ts'

export function AuthProvider({
  children,
  initialUser = null,
  initialReady = false,
}: {
  children: ReactNode
  initialUser?: User | null
  /** When true, skip async restore (tests). */
  initialReady?: boolean
}) {
  const [user, setUserState] = useState<User | null>(initialUser)
  const [ready, setReady] = useState(initialReady || initialUser !== null)

  useEffect(() => {
    if (initialReady || initialUser !== null) {
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        getAccessToken()
        const session = await restoreSession()
        if (cancelled) {
          return
        }
        if (session) {
          setAccessToken(session.token)
          setUserState(session.user)
        }
      } finally {
        if (!cancelled) {
          setReady(true)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [initialReady, initialUser])

  function applySession(token: string, next: User) {
    setAccessToken(token)
    setUserState(next)
  }

  async function login(email: string, password: string) {
    const session = await loginAccount({ email, password })
    applySession(session.token, session.user)
    return session.user
  }

  async function register(email: string, password: string, displayName: string) {
    const session = await registerAccount({ email, password, displayName })
    applySession(session.token, session.user)
    return session.user
  }

  async function logout() {
    await logoutAccount()
    setAccessToken(null)
    setUserState(null)
  }

  function setUser(next: User) {
    setUserState(next)
  }

  return (
    <AuthContext.Provider value={{ user, ready, login, register, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}
