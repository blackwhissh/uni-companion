import { ApiError } from '../../shared/api/identity-client.ts'
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

export async function askCourseQuestion(courseId: string, question: string, materialIds: string[]) {
  const request = () =>
    learningFetch<RagAnswer>('/api/rag/query', {
      method: 'POST',
      body: JSON.stringify({ courseId, question, materialIds }),
    })
  try {
    return await request()
  } catch (err) {
    if (err instanceof ApiError && (err.status === 502 || err.status === 503 || err.status === 504)) {
      return request()
    }
    throw err
  }
}
