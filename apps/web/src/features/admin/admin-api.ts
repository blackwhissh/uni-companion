import { identityFetch } from '../../shared/api/identity-client.ts'
import { learningFetch } from '../../shared/api/learning-client.ts'

export type AdminAccount = {
  id: string
  email: string
  displayName: string
  roles: string[]
  createdAt: string
}

export type AdminCourse = {
  id: string
  title: string
  code: string
  term: string
  visibility: 'UNPUBLISHED' | 'PUBLISHED'
  ownerId: string
  enrollmentCount: number
  publishedReadyMaterials: number
  failedMaterials: number
  otherMaterials: number
}

export function listAdminAccounts() {
  return identityFetch<AdminAccount[]>('/api/admin/accounts')
}

export function setCourseAdmin(id: string, courseAdmin: boolean) {
  return identityFetch<AdminAccount>(`/api/admin/accounts/${id}/course-admin`, {
    method: 'PUT',
    body: JSON.stringify({ courseAdmin }),
  })
}

export function listAdminCourses() {
  return learningFetch<AdminCourse[]>('/api/admin/courses')
}
