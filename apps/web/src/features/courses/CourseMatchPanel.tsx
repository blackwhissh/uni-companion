import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, ConfirmDialog, Panel, SectionLabel, StatusPill } from '../../shared/ui/ui.tsx'
import { getMatchStatus, setMatchOptIn } from './match-api.ts'
import { matchCountLabel } from './match-ui.ts'

export function CourseMatchPanel({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const status = useQuery({
    queryKey: ['match', courseId],
    queryFn: () => getMatchStatus(courseId),
  })
  const [confirmOptIn, setConfirmOptIn] = useState(false)
  const [confirmOptOut, setConfirmOptOut] = useState(false)
  const update = useMutation({
    mutationFn: (optedIn: boolean) => setMatchOptIn(courseId, optedIn),
    onSuccess: (next) => {
      queryClient.setQueryData(['match', courseId], next)
    },
  })
  const optedIn = status.data?.optedIn === true
  const activeCount = status.data?.activeCount ?? 0

  return (
    <Panel className="mt-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SectionLabel>Study partners</SectionLabel>
        {status.data ? <StatusPill tone={optedIn ? 'ok' : 'neutral'}>{optedIn ? 'Discoverable' : 'Private'}</StatusPill> : null}
      </div>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        Opt in to be counted by classmates who are also looking for a study partner in this course. Your name and email
        stay private. You can opt out at any time, and leaving the course removes you immediately.
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
      <div className="mt-4">
        {optedIn ? (
          <Button type="button" variant="ghost" disabled={update.isPending} onClick={() => setConfirmOptOut(true)}>
            Stop sharing
          </Button>
        ) : (
          <Button
            type="button"
            disabled={update.isPending || !status.data}
            onClick={() => setConfirmOptIn(true)}
          >
            Opt in to study partners
          </Button>
        )}
      </div>
      <ConfirmDialog
        open={confirmOptIn}
        title="Opt in to study partners?"
        body="This adds you to the study-partner count for this course. Classmates see the number only, not your name or email. Contact sharing is not part of this step."
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
        body="You will disappear from this course's study-partner count immediately. Classmates never saw your name or email."
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
