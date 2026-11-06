import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { deleteOwnAccount, exportPersonalData, fetchMe, updateProfile } from '../auth/auth-api.ts'
import { useAuth } from '../auth/use-auth.ts'
import { Field } from '../auth/AuthForm.tsx'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, ConfirmDialog, Page, PageHeader, Panel, SectionLabel } from '../../shared/ui/ui.tsx'

export function ProfilePage() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['me'], queryFn: fetchMe })
  const [displayName, setDisplayName] = useState<string | null>(null)
  const [interests, setInterests] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [exported, setExported] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const nameValue = displayName ?? profile.data?.displayName ?? ''
  const interestsValue = interests ?? profile.data?.interests ?? ''
  const platformAdmin = profile.data?.roles.includes('ADMIN') === true

  const save = useMutation({
    mutationFn: () => updateProfile({ displayName: nameValue, interests: interestsValue }),
    onSuccess: (updated) => {
      setUser(updated)
      queryClient.setQueryData(['me'], updated)
      setSaved(true)
      setExported(false)
      setError(null)
    },
    onError: (err) => {
      setSaved(false)
      setError(err instanceof ApiError ? err.message : 'Something went wrong.')
    },
  })

  const downloadData = useMutation({
    mutationFn: exportPersonalData,
    onSuccess: (payload) => {
      setError(null)
      setSaved(false)
      setExported(true)
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `uni-companion-data-${payload.account.email}.json`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    },
    onError: (err) => {
      setExported(false)
      setError(err instanceof ApiError ? err.message : 'Could not export your data.')
    },
  })

  const removeAccount = useMutation({
    mutationFn: deleteOwnAccount,
    onSuccess: () => {
      setUser(null)
      queryClient.clear()
      navigate('/login', { replace: true, state: {} })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not delete your account.')
    },
  })

  return (
    <Page narrow>
      <PageHeader
        eyebrow="Account"
        title="Profile"
        description="This is how classmates recognize you when you look for a study partner. Your email stays private until you both agree to share it."
      />

      {profile.isLoading ? <p className="mt-8 animate-pulse-soft text-muted">Loading your profile…</p> : null}

      {profile.data ? (
        <>
          <Panel className="mt-8">
            <form
              className="flex flex-col gap-4"
              onSubmit={(event) => {
                event.preventDefault()
                setSaved(false)
                setExported(false)
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
              {saved ? <p className="text-sm font-medium text-ok">All set — profile saved.</p> : null}
              <Button type="submit" disabled={save.isPending}>
                Save changes
              </Button>
            </form>
          </Panel>

          <Panel className="mt-6">
            <SectionLabel>Your data</SectionLabel>
            <p className="mt-2 text-sm leading-relaxed text-muted">
              Take a copy of what’s stored for you — profile, courses you’ve joined, study plans, quiz history, private
              notes, and study-partner connections — or close the account for good.
            </p>
            {exported ? (
              <p className="mt-3 text-sm font-medium text-ok" role="status">
                Your data file is ready — check your downloads.
              </p>
            ) : null}
            <div className="mt-4 flex flex-wrap gap-3">
              <Button
                type="button"
                variant="secondary"
                disabled={downloadData.isPending}
                onClick={() => downloadData.mutate()}
              >
                {downloadData.isPending ? 'Gathering your data…' : 'Download my data'}
              </Button>
              {!platformAdmin ? (
                <Button
                  type="button"
                  variant="danger"
                  disabled={removeAccount.isPending}
                  onClick={() => setConfirmDelete(true)}
                >
                  Close my account
                </Button>
              ) : null}
            </div>
          </Panel>

          <ConfirmDialog
            open={confirmDelete}
            title="Close your account for good?"
            body="This removes your profile, courses you’ve joined, quiz history, study plans, private notes, and study-partner connections. There’s no undo."
            confirmLabel="Close account"
            danger
            onCancel={() => setConfirmDelete(false)}
            onConfirm={() => {
              setConfirmDelete(false)
              removeAccount.mutate()
            }}
          />
        </>
      ) : null}
    </Page>
  )
}
