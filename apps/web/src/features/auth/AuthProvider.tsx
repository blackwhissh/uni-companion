import { useState } from 'react'
import type { ReactNode } from 'react'
import { setAccessToken } from '../../shared/api/access-token.ts'
import { loginAccount, registerAccount } from './auth-api.ts'
import { AuthContext } from './use-auth.ts'
import type { User } from './types.ts'

export function AuthProvider({
  children,
  initialUser = null,
}: {
  children: ReactNode
  initialUser?: User | null
}) {
  const [user, setUserState] = useState<User | null>(initialUser)

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

  function logout() {
    setAccessToken(null)
    setUserState(null)
  }

  function setUser(next: User) {
    setUserState(next)
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout, setUser }}>{children}</AuthContext.Provider>
  )
}
