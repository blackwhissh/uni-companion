import { ApiError } from '../../shared/api/identity-client.ts'
import { learningFetch, learningFetchStream } from '../../shared/api/learning-client.ts'

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

export type StudyChatMessage = {
  id: string
  role: 'USER' | 'ASSISTANT' | string
  content: string
  citations: RagCitation[]
  createdAt: string
}

export type StudyChatSession = {
  id: string
  courseId: string
  materialIds: string[]
  language: string | null
  messages: StudyChatMessage[]
  createdAt: string
  updatedAt: string
}

export function startStudyChat(courseId: string, materialIds: string[], language?: 'EN' | 'DE') {
  return withRetry(() =>
    learningFetch<StudyChatSession>('/api/rag/chat/sessions', {
      method: 'POST',
      body: JSON.stringify({
        courseId,
        materialIds,
        ...(language ? { language } : {}),
      }),
    }),
  )
}

export function getStudyChat(sessionId: string) {
  return withRetry(() => learningFetch<StudyChatSession>(`/api/rag/chat/sessions/${sessionId}`))
}

export type LatestStudyChat = {
  id: string
  courseId: string
  materialIds: string[]
  updatedAt: string
  preview: string
}

export async function latestStudyChat(courseId: string): Promise<LatestStudyChat | null> {
  try {
    const body = await learningFetch<LatestStudyChat>(`/api/rag/chat/courses/${courseId}/latest`)
    if (!body || typeof body.id !== 'string' || typeof body.preview !== 'string') {
      return null
    }
    return body
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null
    }
    throw err
  }
}

export function sendStudyChatMessage(sessionId: string, question: string) {
  return withRetry(() =>
    learningFetch<StudyChatSession>(`/api/rag/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ question }),
    }),
  )
}

export type StudyChatStreamHandlers = {
  onToken?: (delta: string) => void
  onDone: (session: StudyChatSession) => void
}

/**
 * Progressive study-chat reply over SSE (`token` deltas, then `done` with the saved session).
 * Failures are not auto-retried — the user message may already be persisted server-side.
 */
export async function streamStudyChatMessage(
  sessionId: string,
  question: string,
  handlers: StudyChatStreamHandlers,
): Promise<StudyChatSession> {
  const response = await learningFetchStream(`/api/rag/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    body: JSON.stringify({ question }),
  })

  if (!response.body) {
    throw new ApiError('REQUEST_FAILED', 'Could not open the study chat stream.', 502, null)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed: StudyChatSession | null = null
  let streamError: ApiError | null = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      const parsed = parseSseBlock(part)
      if (!parsed) {
        continue
      }
      if (parsed.event === 'token') {
        const delta = typeof parsed.data?.delta === 'string' ? parsed.data.delta : ''
        if (delta) {
          handlers.onToken?.(delta)
        }
      } else if (parsed.event === 'done') {
        completed = parsed.data as StudyChatSession
        handlers.onDone(completed)
      } else if (parsed.event === 'error') {
        const message =
          typeof parsed.data?.message === 'string'
            ? parsed.data.message
            : 'Could not draft an answer from the published materials. Try again shortly.'
        const status = typeof parsed.data?.status === 'number' ? parsed.data.status : 502
        streamError = new ApiError('STREAM_FAILED', message, status, null)
      }
    }
  }

  if (streamError) {
    throw streamError
  }
  if (!completed) {
    throw new ApiError('REQUEST_FAILED', 'The study chat stream ended without a reply.', 502, null)
  }
  return completed
}

function parseSseBlock(block: string): { event: string; data: Record<string, unknown> } | null {
  const lines = block.split('\n')
  let event = 'message'
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }
  if (dataLines.length === 0) {
    return null
  }
  try {
    return { event, data: JSON.parse(dataLines.join('\n')) as Record<string, unknown> }
  } catch {
    return null
  }
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
