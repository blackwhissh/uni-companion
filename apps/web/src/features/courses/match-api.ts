import { learningFetch } from '../../shared/api/learning-client.ts'

export type MatchStatus = {
  optedIn: boolean
  activeCount: number
}

export function getMatchStatus(courseId: string) {
  return learningFetch<MatchStatus>(`/api/match/courses/${courseId}/active-count`)
}

export function setMatchOptIn(courseId: string, optedIn: boolean) {
  return learningFetch<MatchStatus>(`/api/match/courses/${courseId}/opt-in`, {
    method: 'PUT',
    body: JSON.stringify({ optedIn }),
  })
}
