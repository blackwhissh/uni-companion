import { learningFetch } from '../../shared/api/learning-client.ts'

export type Course = {
  id: string
  title: string
  code: string
  term: string
  visibility: 'UNPUBLISHED' | 'PUBLISHED'
  enrolled: boolean
  owned: boolean
}

export function listCourses() {
  return learningFetch<Course[]>('/api/courses')
}

export function createCourse(input: { title: string; code: string; term: string }) {
  return learningFetch<Course>('/api/courses', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function publishCourse(id: string) {
  return learningFetch<Course>(`/api/courses/${id}/visibility`, {
    method: 'PATCH',
    body: JSON.stringify({ visibility: 'PUBLISHED' }),
  })
}

export function unpublishCourse(id: string) {
  return learningFetch<Course>(`/api/courses/${id}/visibility`, {
    method: 'PATCH',
    body: JSON.stringify({ visibility: 'UNPUBLISHED' }),
  })
}

export function deleteCourse(id: string) {
  return learningFetch<void>(`/api/courses/${id}`, {
    method: 'DELETE',
  })
}

export function enrollInCourse(id: string) {
  return learningFetch<{ courseId: string }>(`/api/courses/${id}/enroll`, {
    method: 'POST',
  })
}

export function unenrollFromCourse(id: string) {
  return learningFetch<void>(`/api/courses/${id}/enroll`, {
    method: 'DELETE',
  })
}

export function getCourse(id: string) {
  return learningFetch<Course>(`/api/courses/${id}`)
}
