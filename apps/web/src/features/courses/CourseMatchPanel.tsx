import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import { Alert, Button, ConfirmDialog, Panel, SectionLabel, StatusPill } from '../../shared/ui/ui.tsx'
import { useAuth } from '../auth/use-auth.ts'
import { acceptContact, declineContact, getMatchStatus, getMatchSuggestions, removeContact, requestContact, setMatchIntents, setMatchOptIn } from './match-api.ts'
import type { CollaborationIntentId, ContactRelation, ContactRequest, PartnerSuggestion } from './match-api.ts'
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
  const [pendingContact, setPendingContact] = useState<PendingContact | null>(null)
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
    enabled: status.isSuccess,
  })
  const saveIntents = useMutation({
    mutationFn: (intents: CollaborationIntentId[]) => setMatchIntents(courseId, intents, displayName),
    onSuccess: (next) => {
      queryClient.setQueryData(['match-suggestions', courseId], next)
    },
  })
  const sendContact = useMutation({
    mutationFn: (action: PendingContact) => {
      if (action.kind === 'request') {
        return requestContact(courseId, action.userId)
      }
      if (action.kind === 'accept') {
        return acceptContact(courseId, action.requestId)
      }
      if (action.kind === 'cancel' || action.kind === 'disconnect') {
        return removeContact(courseId, action.requestId)
      }
      return declineContact(courseId, action.requestId)
    },
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
        {status.data ? <StatusPill tone={optedIn ? 'ok' : 'neutral'}>{optedIn ? 'Looking for partners' : 'Not sharing'}</StatusPill> : null}
      </div>
      <p className="mt-3 text-sm leading-relaxed text-muted">
        Looking for someone to study with? Opt in and classmates who’ve done the same can see your name and what you
        want help with. Your email stays private until you both agree to share it. You can step back anytime — leaving
        the course takes you off the list right away.
      </p>
      {status.isLoading ? (
        <p className="mt-3 text-sm text-muted" role="status">
          Checking whether you’re sharing…
        </p>
      ) : null}
      {status.isError ? (
        <div className="mt-3">
          <Alert>Couldn’t check your study-partner settings. Try again in a moment.</Alert>
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
      {sendContact.error instanceof ApiError ? (
        <div className="mt-3">
          <Alert>{sendContact.error.message}</Alert>
        </div>
      ) : null}
      <div className="mt-4">
        {optedIn ? (
          <Button type="button" variant="ghost" disabled={update.isPending} onClick={() => setConfirmOptOut(true)}>
            Step back
          </Button>
        ) : displayName ? (
          <Button
            type="button"
            disabled={update.isPending || !status.data}
            onClick={() => setConfirmOptIn(true)}
          >
            Find study partners
          </Button>
        ) : (
          <p className="text-sm text-muted">
            <Link to="/profile" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
              Add a display name
            </Link>{' '}
            first — that’s how classmates will recognize you.
          </p>
        )}
      </div>

      {optedIn ? (
        <div className="mt-6 border-t border-line/70 pt-5">
          <SectionLabel>What you’re looking for</SectionLabel>
          <fieldset className="mt-3" disabled={saveIntents.isPending || displayName.length === 0}>
            <legend className="text-sm text-muted">
              Tick what you want, and we’ll show people looking for the same thing. Emails stay private until you accept
              a request.
            </legend>
            <div className="mt-3 flex flex-col gap-2">
              {COLLABORATION_INTENTS.map((intent) => (
                <label key={intent.id} className="flex items-center gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    checked={suggestions.data?.intents?.includes(intent.id) ?? false}
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
          {suggestions.data && (suggestions.data.intents?.length ?? 0) === 0 ? (
            <p className="mt-4 text-sm text-muted">Pick what you’re after and we’ll show classmates looking for the same.</p>
          ) : null}
          {suggestions.data && (suggestions.data.intents?.length ?? 0) > 0 && (suggestions.data.suggestions?.length ?? 0) === 0 ? (
            <p className="mt-4 text-sm text-muted">Nobody else is looking for that yet — check back after more people opt in.</p>
          ) : null}
          {(suggestions.data?.suggestions?.length ?? 0) > 0 ? (
            <ul className="mt-4 flex flex-col gap-2">
              {(suggestions.data?.suggestions ?? []).map((suggestion) => (
                <li key={suggestion.userId} className="rounded-xl border border-line/80 px-4 py-3">
                  <p className="font-medium text-ink">{suggestion.displayName}</p>
                  <p className="mt-1 text-sm text-muted">
                    Also looking for {suggestion.sharedIntents.map((intent) => intentLabel(intent)).join(', ')}
                  </p>
                  <ContactActions
                    name={suggestion.displayName}
                    contact={suggestion.contact ?? 'NONE'}
                    requestId={suggestion.requestId}
                    email={suggestion.email}
                    busy={sendContact.isPending}
                    onRequest={() => setPendingContact({ kind: 'request', userId: suggestion.userId, name: suggestion.displayName })}
                    onAccept={() =>
                      suggestion.requestId
                        ? setPendingContact({ kind: 'accept', requestId: suggestion.requestId, name: suggestion.displayName })
                        : undefined
                    }
                    onDecline={() =>
                      suggestion.requestId
                        ? setPendingContact({ kind: 'decline', requestId: suggestion.requestId, name: suggestion.displayName })
                        : undefined
                    }
                    onCancel={() =>
                      suggestion.requestId
                        ? setPendingContact({ kind: 'cancel', requestId: suggestion.requestId, name: suggestion.displayName })
                        : undefined
                    }
                    onDisconnect={() =>
                      suggestion.requestId
                        ? setPendingContact({ kind: 'disconnect', requestId: suggestion.requestId, name: suggestion.displayName })
                        : undefined
                    }
                  />
                </li>
              ))}
            </ul>
          ) : null}
          <OtherRequests
            suggestions={suggestions.data?.suggestions ?? []}
            requests={Array.isArray(suggestions.data?.requests) ? suggestions.data.requests : []}
            busy={sendContact.isPending}
            onAccept={(request) => setPendingContact({ kind: 'accept', requestId: request.id, name: request.displayName })}
            onDecline={(request) => setPendingContact({ kind: 'decline', requestId: request.id, name: request.displayName })}
            onCancel={(request) => setPendingContact({ kind: 'cancel', requestId: request.id, name: request.displayName })}
            onDisconnect={(request) => setPendingContact({ kind: 'disconnect', requestId: request.id, name: request.displayName })}
          />
        </div>
      ) : null}

      {!optedIn && acceptedContacts(suggestions.data?.requests).length > 0 ? (
        <ul className="mt-4 flex flex-col gap-2">
          {acceptedContacts(suggestions.data?.requests).map((request) => (
            <li key={request.id} className="rounded-xl border border-line/80 px-4 py-3">
              <p className="font-medium text-ink">{request.displayName}</p>
              <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-ink">Email: {request.email}</p>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={sendContact.isPending}
                  onClick={() => setPendingContact({ kind: 'disconnect', requestId: request.id, name: request.displayName })}
                >
                  Disconnect
                </Button>
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      <ConfirmDialog
        open={confirmOptIn}
        title="Show up for study partners?"
        body="Classmates who’ve also opted in can see your name and what you’re looking for. They won’t see your email unless you accept a contact request."
        confirmLabel="I’m in"
        onCancel={() => setConfirmOptIn(false)}
        onConfirm={() => {
          setConfirmOptIn(false)
          update.mutate(true)
        }}
      />
      <ConfirmDialog
        open={confirmOptOut}
        title="Step back from the list?"
        body="You’ll leave this course’s partner list right away, and any pending requests disappear. Connections you’ve already accepted keep their emails."
        confirmLabel="Step back"
        danger
        onCancel={() => setConfirmOptOut(false)}
        onConfirm={() => {
          setConfirmOptOut(false)
          update.mutate(false)
        }}
      />
      <ConfirmDialog
        open={pendingContact !== null}
        title={contactTitle(pendingContact)}
        body={contactBody(pendingContact)}
        confirmLabel={contactConfirmLabel(pendingContact)}
        danger={
          pendingContact?.kind === 'decline' ||
          pendingContact?.kind === 'cancel' ||
          pendingContact?.kind === 'disconnect'
        }
        onCancel={() => setPendingContact(null)}
        onConfirm={() => {
          if (pendingContact) {
            sendContact.mutate(pendingContact)
          }
          setPendingContact(null)
        }}
      />
    </Panel>
  )
}

function OtherRequests({
  suggestions,
  requests,
  busy,
  onAccept,
  onDecline,
  onCancel,
  onDisconnect,
}: {
  suggestions: PartnerSuggestion[]
  requests: ContactRequest[]
  busy: boolean
  onAccept: (request: ContactRequest) => void
  onDecline: (request: ContactRequest) => void
  onCancel: (request: ContactRequest) => void
  onDisconnect: (request: ContactRequest) => void
}) {
  const listed = new Set(suggestions.map((suggestion) => suggestion.userId))
  const extras = requests.filter((request) => !listed.has(request.userId) && request.status !== 'DECLINED')
  if (extras.length === 0) {
    return null
  }
  return (
    <ul className="mt-4 flex flex-col gap-2">
      {extras.map((request) => (
        <li key={request.id} className="rounded-xl border border-line/80 px-4 py-3">
          <p className="font-medium text-ink">{request.displayName}</p>
          <ContactActions
            name={request.displayName}
            contact={request.status === 'ACCEPTED' ? 'ACCEPTED' : request.direction === 'INCOMING' ? 'INCOMING' : 'OUTGOING'}
            requestId={request.id}
            email={request.email}
            busy={busy}
            onRequest={() => undefined}
            onAccept={() => onAccept(request)}
            onDecline={() => onDecline(request)}
            onCancel={() => onCancel(request)}
            onDisconnect={() => onDisconnect(request)}
          />
        </li>
      ))}
    </ul>
  )
}

function ContactActions({
  name,
  contact,
  requestId,
  email,
  busy,
  onRequest,
  onAccept,
  onDecline,
  onCancel,
  onDisconnect,
}: {
  name: string
  contact: ContactRelation
  requestId?: string
  email?: string
  busy: boolean
  onRequest: () => void
  onAccept: () => void
  onDecline: () => void
  onCancel: () => void
  onDisconnect: () => void
}) {
  if (contact === 'OUTGOING') {
    return (
      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">Waiting to hear back from {name}.</p>
        {requestId ? (
          <Button type="button" variant="ghost" disabled={busy} onClick={onCancel}>
            Take back request
          </Button>
        ) : null}
      </div>
    )
  }
  if (contact === 'ACCEPTED' && email) {
    return (
      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-ink">Email: {email}</p>
        {requestId ? (
          <Button type="button" variant="ghost" disabled={busy} onClick={onDisconnect}>
            Disconnect
          </Button>
        ) : null}
      </div>
    )
  }
  if (contact === 'INCOMING' && requestId) {
    return (
      <div className="mt-3 flex gap-2">
        <Button type="button" variant="success" disabled={busy} onClick={onAccept}>
          Accept
        </Button>
        <Button type="button" variant="danger" disabled={busy} onClick={onDecline}>
          Decline
        </Button>
      </div>
    )
  }
  if (contact === 'NONE') {
    return (
      <div className="mt-3">
        <Button type="button" variant="secondary" disabled={busy} onClick={onRequest}>
          Say hello
        </Button>
      </div>
    )
  }
  return null
}

type PendingContact =
  | { kind: 'request'; userId: string; name: string }
  | { kind: 'accept'; requestId: string; name: string }
  | { kind: 'decline'; requestId: string; name: string }
  | { kind: 'cancel'; requestId: string; name: string }
  | { kind: 'disconnect'; requestId: string; name: string }

function acceptedContacts(requests: ContactRequest[] | undefined) {
  if (!Array.isArray(requests)) {
    return []
  }
  return requests.filter((request) => request.status === 'ACCEPTED' && request.email)
}

function contactTitle(action: PendingContact | null) {
  if (action?.kind === 'accept') {
    return `Share emails with ${action.name}?`
  }
  if (action?.kind === 'decline') {
    return `Pass on ${action.name}'s request?`
  }
  if (action?.kind === 'cancel') {
    return `Take back your hello to ${action.name}?`
  }
  if (action?.kind === 'disconnect') {
    return `Disconnect from ${action.name}?`
  }
  return action ? `Reach out to ${action.name}?` : ''
}

function contactBody(action: PendingContact | null) {
  if (action?.kind === 'accept') {
    return `You’ll see each other’s email so you can actually get in touch.`
  }
  if (action?.kind === 'decline') {
    return 'Your email stays private, and they won’t see a connection.'
  }
  if (action?.kind === 'cancel') {
    return `We’ll pull back the request you sent to ${action.name}.`
  }
  if (action?.kind === 'disconnect') {
    return `You’ll both lose the shared emails for this course. You can always say hello again later.`
  }
  return action
    ? `${action.name} will see that you’d like to connect. Your email stays hidden until they accept.`
    : ''
}

function contactConfirmLabel(action: PendingContact | null) {
  if (action?.kind === 'accept') {
    return 'Share emails'
  }
  if (action?.kind === 'decline') {
    return 'Pass for now'
  }
  if (action?.kind === 'cancel') {
    return 'Take it back'
  }
  if (action?.kind === 'disconnect') {
    return 'Disconnect'
  }
  return 'Say hello'
}
