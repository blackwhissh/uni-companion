import { learningFetch } from '../../shared/api/learning-client.ts'

export type StudentNote = {
  id: string
  courseId: string
  title: string
  body: string
  createdAt: string
  updatedAt: string
}

export function listCourseNotes(courseId: string) {
  return learningFetch<StudentNote[]>(`/api/notes/courses/${courseId}`)
}

export function createCourseNote(courseId: string, input: { title: string; body: string }) {
  return learningFetch<StudentNote>(`/api/notes/courses/${courseId}`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateCourseNote(courseId: string, noteId: string, input: { title: string; body: string }) {
  return learningFetch<StudentNote>(`/api/notes/courses/${courseId}/${noteId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteCourseNote(courseId: string, noteId: string) {
  return learningFetch<void>(`/api/notes/courses/${courseId}/${noteId}`, {
    method: 'DELETE',
  })
}
