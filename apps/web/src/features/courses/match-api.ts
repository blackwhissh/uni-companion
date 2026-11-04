import { learningFetch } from '../../shared/api/learning-client.ts'

export type MatchStatus = {
  optedIn: boolean
  activeCount: number
}

export type CollaborationIntentId = 'STUDY_GROUP' | 'PROJECT' | 'THESIS' | 'LANGUAGE'

export type PartnerSuggestion = {
  userId: string
  displayName: string
  sharedIntents: CollaborationIntentId[]
}

export type MatchSuggestions = {
  discoverable: boolean
  intents: CollaborationIntentId[]
  suggestions: PartnerSuggestion[]
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
