import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from './use-auth.ts'
import { AuthForm, Field } from './AuthForm.tsx'
import { homePathFor } from './home-path.ts'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')

  return (
    <AuthForm
      title="Create an account"
      subtitle="Join your course cohort. Admins publish materials; you enroll and study."
      submitLabel="Create account"
      onSubmit={async () => {
        const user = await register(email, password, displayName)
        navigate(homePathFor(user.roles))
      }}
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
            Log in
          </Link>
        </>
      }
    >
      <Field label="Display name" type="text" value={displayName} onChange={setDisplayName} autoComplete="name" />
      <Field label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
      <Field label="Password" type="password" value={password} onChange={setPassword} autoComplete="new-password" />
    </AuthForm>
  )
}
