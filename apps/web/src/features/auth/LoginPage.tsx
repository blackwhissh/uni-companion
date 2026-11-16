import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { useAuth } from './use-auth.ts'
import { AuthForm, Field } from './AuthForm.tsx'
import { consumeSkipReturnPath, shouldSkipReturnPath } from './auth-session-sync.ts'
import { homePathFor } from './home-path.ts'

type LocationState = { from?: { pathname?: string; search?: string; hash?: string } }

function resolvePostLoginTarget(state: unknown, roles: string[]) {
  if (shouldSkipReturnPath()) {
    return homePathFor(roles)
  }
  const from = (state as LocationState | null)?.from
  if (from?.pathname && from.pathname.startsWith('/') && !from.pathname.startsWith('//')) {
    return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
  }
  return homePathFor(roles)
}

export function LoginPage() {
  const { login, user, ready } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // Session already live (refresh or post-login) — never keep the form mounted beside the next page.
  if (ready && user) {
    const target = resolvePostLoginTarget(location.state, user.roles)
    consumeSkipReturnPath()
    return <Navigate to={target} replace state={{}} />
  }

  return (
    <AuthForm
      title="Log in"
      subtitle="Pick up where you left off — courses, materials, and matching stay with your account."
      submitLabel="Log in"
      onSubmit={async (values) => {
        await login(values.email ?? email, values.password ?? password)
      }}
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
            Create an account
          </Link>
        </>
      }
    >
      <Field
        label="Username"
        name="email"
        type="text"
        value={email}
        onChange={setEmail}
        autoComplete="username"
        hint="Use your username (for example student1) or email."
      />
      <Field
        label="Password"
        name="password"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="current-password"
      />
    </AuthForm>
  )
}
