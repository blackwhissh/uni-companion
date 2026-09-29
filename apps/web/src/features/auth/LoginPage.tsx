import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from './use-auth.ts'
import { AuthForm, Field } from './AuthForm.tsx'
import { homePathFor } from './home-path.ts'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  return (
    <AuthForm
      title="Log in"
      subtitle="Pick up where you left off — courses, materials, and matching stay with your account."
      submitLabel="Log in"
      onSubmit={async () => {
        const user = await login(email, password)
        navigate(homePathFor(user.roles))
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
      <Field label="Email" type="text" value={email} onChange={setEmail} autoComplete="username" />
      <Field
        label="Password"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="current-password"
      />
    </AuthForm>
  )
}
