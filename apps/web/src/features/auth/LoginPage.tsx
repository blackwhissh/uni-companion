import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from './use-auth.ts'
import { AuthForm, Field } from './AuthForm.tsx'
import { homePathFor } from './home-path.ts'

type LocationState = { from?: { pathname?: string; search?: string; hash?: string } }

function targetFrom(state: unknown, roles: string[]) {
  const from = (state as LocationState | null)?.from
  if (from?.pathname) {
    return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
  }
  return homePathFor(roles)
}

export function LoginPage() {
  const { login, user, ready } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  if (ready && user) {
    return <Navigate to={targetFrom(location.state, user.roles)} replace />
  }

  return (
    <AuthForm
      title="Log in"
      subtitle="Pick up where you left off — courses, materials, and matching stay with your account."
      submitLabel="Log in"
      onSubmit={async (values) => {
        const next = await login(values.email ?? email, values.password ?? password)
        navigate(targetFrom(location.state, next.roles), { replace: true })
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
