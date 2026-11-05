import { learningFetch } from '../../shared/api/learning-client.ts'

export type MatchStatus = {
  optedIn: boolean
  activeCount: number
}

export type CollaborationIntentId = 'STUDY_GROUP' | 'PROJECT' | 'THESIS' | 'LANGUAGE'

export type ContactRelation = 'NONE' | 'OUTGOING' | 'INCOMING' | 'ACCEPTED'

export type PartnerSuggestion = {
  userId: string
  displayName: string
  sharedIntents: CollaborationIntentId[]
  contact?: ContactRelation
  requestId?: string
  email?: string
}

export type ContactRequest = {
  id: string
  userId: string
  displayName: string
  direction: 'INCOMING' | 'OUTGOING'
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED'
  email?: string
}

export type MatchSuggestions = {
  discoverable: boolean
  intents: CollaborationIntentId[]
  suggestions: PartnerSuggestion[]
  requests?: ContactRequest[]
}

export function getMatchStatus(courseId: string) {
  return learningFetch<MatchStatus>(`/api/match/courses/${courseId}/active-count`)
}

export function setMatchOptIn(courseId: string, optedIn: boolean, displayName: string) {
  return learningFetch<MatchStatus>(`/api/match/courses/${courseId}/opt-in`, {
    method: 'PUT',
    body: JSON.stringify({ optedIn, displayName }),
  })
}

export function getMatchSuggestions(courseId: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/suggestions`)
}

export function setMatchIntents(courseId: string, intents: CollaborationIntentId[], displayName: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/intents`, {
    method: 'PUT',
    body: JSON.stringify({ intents, displayName }),
  })
}

export function requestContact(courseId: string, userId: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/contacts`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  })
}

export function acceptContact(courseId: string, requestId: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/contacts/${requestId}/accept`, {
    method: 'POST',
  })
}

export function declineContact(courseId: string, requestId: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/contacts/${requestId}/decline`, {
    method: 'POST',
  })
}

export function removeContact(courseId: string, requestId: string) {
  return learningFetch<MatchSuggestions>(`/api/match/courses/${courseId}/contacts/${requestId}`, {
    method: 'DELETE',
  })
}
