import { learningFetch } from '../../shared/api/learning-client.ts'

export type RagCitation = {
  materialId: string
  title: string
  sectionHint?: string | null
  pageNumber: number
  excerpt: string
}

export type RagAnswer = {
  answer: string
  citations: RagCitation[]
}

export function askCourseQuestion(courseId: string, question: string, materialIds: string[]) {
  return learningFetch<RagAnswer>('/api/rag/query', {
    method: 'POST',
    body: JSON.stringify({ courseId, question, materialIds }),
  })
}
