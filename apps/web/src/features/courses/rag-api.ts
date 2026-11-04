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

export type SearchHit = {
  materialId: string
  title: string
  pageNumber: number
  excerpt: string
}

export type SearchResult = {
  hits: SearchHit[]
}

export function askCourseQuestion(
  courseId: string,
  question: string,
  materialIds: string[],
  language?: 'EN' | 'DE',
) {
  return withRetry(() =>
    learningFetch<RagAnswer>('/api/rag/query', {
      method: 'POST',
      body: JSON.stringify({
        courseId,
        question,
        materialIds,
        ...(language ? { language } : {}),
      }),
    }),
  )
}

export function searchLectures(courseId: string, query: string) {
  return withRetry(() =>
    learningFetch<SearchResult>('/api/rag/search', {
      method: 'POST',
      body: JSON.stringify({ courseId, query }),
    }),
  )
}

export function summarizeMaterial(courseId: string, materialId: string, language: 'EN' | 'DE') {
  return withRetry(() =>
    learningFetch<RagAnswer>('/api/rag/summary', {
      method: 'POST',
      body: JSON.stringify({ courseId, materialId, language }),
    }),
  )
}

async function withRetry<T>(request: () => Promise<T>) {
  try {
    return await request()
  } catch (err) {
    if (err instanceof ApiError && (err.status === 502 || err.status === 503 || err.status === 504)) {
      return request()
    }
    throw err
  }
}
