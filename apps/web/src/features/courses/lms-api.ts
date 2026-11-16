import { learningFetch } from '../../shared/api/learning-client.ts'

export type LmsProvider = 'FAKE'

export type LmsLink = {
  courseId: string
  provider: LmsProvider
  externalCourseId: string
  enabled: boolean
  lastSyncedAt?: string | null
  lastStatus?: string | null
  lastMessage?: string | null
}

export type LmsSyncResult = {
  courseId: string
  provider: LmsProvider
  externalCourseId: string
  status: string
  imported: number
  updated: number
  skipped: number
  syncedAt: string
  materialIds: string[]
  message: string
}

export function getLmsLink(courseId: string) {
  return learningFetch<LmsLink>(`/api/courses/${courseId}/lms`)
}

export function upsertLmsLink(
  courseId: string,
  body: { provider: LmsProvider; externalCourseId: string; enabled?: boolean },
) {
  return learningFetch<LmsLink>(`/api/courses/${courseId}/lms`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function syncLms(courseId: string) {
  return learningFetch<LmsSyncResult>(`/api/courses/${courseId}/lms/sync`, {
    method: 'POST',
  })
}
