import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchMe, updateProfile } from '../auth/auth-api.ts'
import { useAuth } from '../auth/use-auth.ts'
import { Field } from '../auth/AuthForm.tsx'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, Page, PageHeader, Panel } from '../../shared/ui/ui.tsx'

export function ProfilePage() {
  const { setUser } = useAuth()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['me'], queryFn: fetchMe })
  const [displayName, setDisplayName] = useState<string | null>(null)
  const [interests, setInterests] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const nameValue = displayName ?? profile.data?.displayName ?? ''
  const interestsValue = interests ?? profile.data?.interests ?? ''

  const save = useMutation({
    mutationFn: () => updateProfile({ displayName: nameValue, interests: interestsValue }),
    onSuccess: (updated) => {
      setUser(updated)
      queryClient.setQueryData(['me'], updated)
      setSaved(true)
      setError(null)
    },
    onError: (err) => {
      setSaved(false)
      setError(err instanceof ApiError ? err.message : 'Something went wrong.')
    },
  })

  return (
    <Page narrow>
      <PageHeader
        eyebrow="Account"
        title="Profile"
        description="Your display name and study interests appear on your account. Matching is not live yet."
      />

      {profile.isLoading ? <p className="mt-8 animate-pulse-soft text-muted">Loading profile…</p> : null}

      {profile.data ? (
        <Panel className="mt-8">
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault()
              setSaved(false)
              save.mutate()
            }}
          >
            <Field label="Display name" type="text" value={nameValue} onChange={setDisplayName} autoComplete="name" />
            <Field
              label="Interests"
              type="text"
              value={interestsValue}
              onChange={setInterests}
              autoComplete="off"
              hint="e.g. distributed systems, thesis partners, German practice"
            />
            {error ? <Alert>{error}</Alert> : null}
            {saved ? <p className="text-sm font-medium text-ok">Saved.</p> : null}
            <Button type="submit" disabled={save.isPending}>
              Save profile
            </Button>
          </form>
        </Panel>
      ) : null}
    </Page>
  )
}
