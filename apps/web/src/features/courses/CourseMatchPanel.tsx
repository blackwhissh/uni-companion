import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, ConfirmDialog, Panel, SectionLabel, StatusPill } from '../../shared/ui/ui.tsx'
import { useAuth } from '../auth/use-auth.ts'
import { getMatchStatus, getMatchSuggestions, setMatchIntents, setMatchOptIn } from './match-api.ts'
import type { CollaborationIntentId } from './match-api.ts'
import { COLLABORATION_INTENTS, intentLabel, matchCountLabel } from './match-ui.ts'

export function CourseMatchPanel({ courseId }: { courseId: string }) {
  const { user } = useAuth()
  const displayName = user?.displayName?.trim() ?? ''
  const queryClient = useQueryClient()
  const status = useQuery({
    queryKey: ['match', courseId],
    queryFn: () => getMatchStatus(courseId),
  })
  const [confirmOptIn, setConfirmOptIn] = useState(false)
  const [confirmOptOut, setConfirmOptOut] = useState(false)
  const update = useMutation({
    mutationFn: (optedIn: boolean) => setMatchOptIn(courseId, optedIn, displayName),
    onSuccess: async (next) => {
      queryClient.setQueryData(['match', courseId], next)
      await queryClient.invalidateQueries({ queryKey: ['match-suggestions', courseId] })
    },
  })
  const optedIn = status.data?.optedIn === true
  const activeCount = status.data?.activeCount ?? 0
  const suggestions = useQuery({
    queryKey: ['match-suggestions', courseId],
    queryFn: () => getMatchSuggestions(courseId),
    enabled: optedIn,
  })
  const saveIntents = useMutation({
    mutationFn: (intents: CollaborationIntentId[]) => setMatchIntents(courseId, intents, displayName),
    onSuccess: (next) => {
      queryClient.setQueryData(['match-suggestions', courseId], next)
    },
  })

  function toggleIntent(id: CollaborationIntentId) {
    const current = suggestions.data?.intents ?? []
    const next = current.includes(id) ? current.filter((intent) => intent !== id) : [...current, id]
    saveIntents.mutate(next)
  }

  return (
    <Panel className="mt-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SectionLabel>Study partners</SectionLabel>
        {status.data ? <StatusPill tone={optedIn ? 'ok' : 'neutral'}>{optedIn ? 'Discoverable' : 'Private'}</StatusPill> : null}
      </div>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        Opt in to be listed to classmates in this course who are also looking for a study partner. They can see your
        display name and the intents you choose. Your email stays private. You can opt out at any time, and leaving the
        course removes you immediately.
      </p>
      {status.isLoading ? (
        <p className="mt-3 text-sm text-muted" role="status">
          Loading study-partner status…
        </p>
      ) : null}
      {status.isError ? (
        <div className="mt-3">
          <Alert>Could not load study-partner status.</Alert>
        </div>
      ) : null}
      {status.data ? (
        <p className="mt-3 text-sm font-medium text-ink" role="status">
          {matchCountLabel(activeCount, optedIn)}
        </p>
      ) : null}
      {update.error instanceof ApiError ? (
        <div className="mt-3">
          <Alert>{update.error.message}</Alert>
        </div>
      ) : null}
      {saveIntents.error instanceof ApiError ? (
        <div className="mt-3">
          <Alert>{saveIntents.error.message}</Alert>
        </div>
      ) : null}
      <div className="mt-4">
        {optedIn ? (
          <Button type="button" variant="ghost" disabled={update.isPending} onClick={() => setConfirmOptOut(true)}>
            Stop sharing
          </Button>
        ) : displayName ? (
          <Button
            type="button"
            disabled={update.isPending || !status.data}
            onClick={() => setConfirmOptIn(true)}
          >
            Opt in to study partners
          </Button>
        ) : (
          <p className="text-sm text-muted">
            <Link to="/profile" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
              Add a display name
            </Link>{' '}
            before opting in.
          </p>
        )}
      </div>

      {optedIn ? (
        <div className="mt-6 border-t border-line/70 pt-5">
          <SectionLabel>What you want</SectionLabel>
          <fieldset className="mt-3" disabled={saveIntents.isPending || displayName.length === 0}>
            <legend className="text-sm text-muted">
              Classmates appear when you share an intent. Your email is not shown.
            </legend>
            <div className="mt-3 flex flex-col gap-2">
              {COLLABORATION_INTENTS.map((intent) => (
                <label key={intent.id} className="flex items-center gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    checked={suggestions.data?.intents.includes(intent.id) ?? false}
                    onChange={() => toggleIntent(intent.id)}
                  />
                  {intent.label}
                </label>
              ))}
            </div>
          </fieldset>

          {suggestions.isLoading ? (
            <p className="mt-4 text-sm text-muted" role="status">
              Loading classmates…
            </p>
          ) : null}
          {suggestions.isError ? (
            <div className="mt-4">
              <Alert>Could not load classmate suggestions.</Alert>
            </div>
          ) : null}
          {suggestions.data && suggestions.data.intents.length === 0 ? (
            <p className="mt-4 text-sm text-muted">Choose what you want to work on to see classmates.</p>
          ) : null}
          {suggestions.data && suggestions.data.intents.length > 0 && suggestions.data.suggestions.length === 0 ? (
            <p className="mt-4 text-sm text-muted">No opted-in classmates share those intents yet.</p>
          ) : null}
          {suggestions.data && suggestions.data.suggestions.length > 0 ? (
            <ul className="mt-4 flex flex-col gap-2">
              {suggestions.data.suggestions.map((suggestion) => (
                <li key={suggestion.userId} className="rounded-xl border border-line/80 px-4 py-3">
                  <p className="font-medium text-ink">{suggestion.displayName}</p>
                  <p className="mt-1 text-sm text-muted">
                    Also looking for {suggestion.sharedIntents.map((intent) => intentLabel(intent)).join(', ')}
                  </p>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      <ConfirmDialog
        open={confirmOptIn}
        title="Opt in to study partners?"
        body="Classmates who have also opted in can see your display name and shared intents. They cannot see your email. Asking to connect by email comes later, and only if you accept."
        confirmLabel="Opt in"
        onCancel={() => setConfirmOptIn(false)}
        onConfirm={() => {
          setConfirmOptIn(false)
          update.mutate(true)
        }}
      />
      <ConfirmDialog
        open={confirmOptOut}
        title="Stop sharing?"
        body="You will disappear from this course's study-partner list immediately, including your name and intents."
        confirmLabel="Stop sharing"
        danger
        onCancel={() => setConfirmOptOut(false)}
        onConfirm={() => {
          setConfirmOptOut(false)
          update.mutate(false)
        }}
      />
    </Panel>
  )
}
