import { ApiError } from '../../shared/api/identity-client.ts'
import { learningFetch } from '../../shared/api/learning-client.ts'

export type Flashcard = {
  id: string
  front: string
  back: string
}

export type FlashcardDeck = {
  id: string
  courseId: string
  version: number
  availableVersions: number
  maxVersions: number
  delivery: StudyDelivery
  versions: StudyVersion[]
  cards: Flashcard[]
}

export type QuizQuestion = {
  id: string
  prompt: string
  options: string[]
}

export type Quiz = {
  id: string
  courseId: string
  version: number
  availableVersions: number
  maxVersions: number
  delivery: StudyDelivery
  versions: StudyVersion[]
  questions: QuizQuestion[]
}

export type StudyDelivery = 'CREATED' | 'REUSED' | 'RESUMED' | 'NEXT_SHARED' | 'CYCLED' | 'SELECTED'

export type StudyVersion = {
  id: string
  version: number
  createdAt: string
  current: boolean
}

export type ReportResult = {
  retired: boolean
}

export type AnswerReview = {
  questionId: string
  selectedIndex: number
  correctIndex: number
  correct: boolean
  correctOption: string
  explanation: string
}

export type QuizAttempt = {
  id: string
  score: number
  total: number
  reviews: AnswerReview[]
}

function materialQuery(materialIds: string[]) {
  return materialIds.map((id) => `materialIds=${encodeURIComponent(id)}`).join('&')
}

export async function generateFlashcards(courseId: string, materialIds: string[], force = false) {
  const query = force ? '?force=true' : ''
  return learningFetch<FlashcardDeck>(`/api/study/courses/${courseId}/flashcards/generate${query}`, {
    method: 'POST',
    body: JSON.stringify({ materialIds }),
  })
}

export async function listFlashcards(courseId: string, materialIds: string[]) {
  try {
    return await learningFetch<FlashcardDeck>(
      `/api/study/courses/${courseId}/flashcards?${materialQuery(materialIds)}`,
    )
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null
    }
    throw err
  }
}

export function selectFlashcardVersion(courseId: string, materialIds: string[], versionId: string) {
  return learningFetch<FlashcardDeck>(`/api/study/courses/${courseId}/flashcards/select`, {
    method: 'POST',
    body: JSON.stringify({ materialIds, versionId }),
  })
}

export function reportFlashcardVersion(deckId: string) {
  return learningFetch<ReportResult>(`/api/study/flashcards/${deckId}/report`, {
    method: 'POST',
    body: JSON.stringify({ reason: 'Student reported a quality issue.' }),
  })
}

export async function generateQuiz(courseId: string, materialIds: string[], force = false) {
  const query = force ? '?force=true' : ''
  return learningFetch<Quiz>(`/api/study/courses/${courseId}/quizzes/generate${query}`, {
    method: 'POST',
    body: JSON.stringify({ materialIds }),
  })
}

export async function listQuiz(courseId: string, materialIds: string[]) {
  try {
    return await learningFetch<Quiz>(`/api/study/courses/${courseId}/quizzes?${materialQuery(materialIds)}`)
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null
    }
    throw err
  }
}

export function selectQuizVersion(courseId: string, materialIds: string[], versionId: string) {
  return learningFetch<Quiz>(`/api/study/courses/${courseId}/quizzes/select`, {
    method: 'POST',
    body: JSON.stringify({ materialIds, versionId }),
  })
}

export function reportQuizVersion(quizId: string) {
  return learningFetch<ReportResult>(`/api/study/quizzes/${quizId}/report`, {
    method: 'POST',
    body: JSON.stringify({ reason: 'Student reported a quality issue.' }),
  })
}

export async function submitQuiz(
  quizId: string,
  answers: { questionId: string; selectedIndex: number }[],
) {
  return learningFetch<QuizAttempt>(`/api/study/quizzes/${quizId}/submit`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  })
}
