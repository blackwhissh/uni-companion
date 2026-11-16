import { learningApiUrl } from '../../shared/api/config.ts'
import { getAccessToken } from '../../shared/api/access-token.ts'
import { ApiError, tryRefreshAccessToken } from '../../shared/api/identity-client.ts'
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

export type AskCourseQuestionInput = {
  courseId: string
  question: string
  materialIds: string[]
  priorQuestion?: string | null
  priorAnswer?: string | null
}

export async function askCourseQuestion(
  courseId: string,
  question: string,
  materialIds: string[],
  prior?: { question: string; answer: string } | null,
) {
  const request = () =>
    learningFetch<RagAnswer>('/api/rag/query', {
      method: 'POST',
      body: JSON.stringify({
        courseId,
        question,
        materialIds,
        priorQuestion: prior?.question ?? null,
        priorAnswer: prior?.answer ?? null,
      }),
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

export type QaHistoryTurn = {
  id: string
  question: string
  answer: string
  citations: RagCitation[]
  insufficient: boolean
  latencyMs: number
  citedCount: number
  followUp: boolean
  createdAt: string
}

export type QaHistoryResponse = {
  turns: QaHistoryTurn[]
}

export type QaCourseStats = {
  totalTurns: number
  avgLatencyMs: number
  citeRate: number
  insufficientRate: number
}

export function listCourseQaHistory(courseId: string, limit = 40) {
  const params = new URLSearchParams({ limit: String(limit) })
  return learningFetch<QaHistoryResponse>(`/api/rag/courses/${courseId}/history?${params}`)
}

export function getCourseQaStats(courseId: string) {
  return learningFetch<QaCourseStats>(`/api/rag/courses/${courseId}/stats`)
}

export type RagStreamHandlers = {
  onStatus?: (phase: string) => void
  onDelta?: (delta: string) => void
}

/** POST SSE stream: status → answer deltas → done (full answer + citations). */
export async function askCourseQuestionStream(
  input: AskCourseQuestionInput,
  handlers: RagStreamHandlers = {},
  allowRefresh = true,
): Promise<RagAnswer> {
  const headers = new Headers({
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
  })
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${learningApiUrl}/api/rag/query/stream`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify({
      courseId: input.courseId,
      question: input.question,
      materialIds: input.materialIds,
      priorQuestion: input.priorQuestion ?? null,
      priorAnswer: input.priorAnswer ?? null,
    }),
  })
  if (response.status === 401 && allowRefresh) {
    const refreshed = await tryRefreshAccessToken()
    if (refreshed) {
      return askCourseQuestionStream(input, handlers, false)
    }
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(
      body?.code ?? 'REQUEST_FAILED',
      body?.message ?? 'Could not answer that question.',
      response.status,
      body?.details ?? null,
    )
  }

  if (!response.body) {
    const text = await response.text()
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(text))
        controller.close()
      },
    })
    return consumeSseStream(stream, handlers)
  }
  return consumeSseStream(response.body, handlers)
}

/** @internal exported for unit tests */
export async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  handlers: RagStreamHandlers,
): Promise<RagAnswer> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let donePayload: RagAnswer | null = null
  let streamError: ApiError | null = null

  const applyBlock = (block: string) => {
    const event = parseSseBlock(block)
    if (!event) {
      return
    }
    if (event.event === 'status' && event.data && typeof event.data === 'object') {
      const phase = (event.data as { phase?: string }).phase
      if (phase) {
        handlers.onStatus?.(phase)
      }
    } else if (event.event === 'answer' && event.data && typeof event.data === 'object') {
      const delta = (event.data as { delta?: string }).delta
      if (delta) {
        handlers.onDelta?.(delta)
      }
    } else if (event.event === 'done' && event.data && typeof event.data === 'object') {
      const payload = event.data as RagAnswer
      donePayload = { answer: payload.answer ?? '', citations: payload.citations ?? [] }
    } else if (event.event === 'error' && event.data && typeof event.data === 'object') {
      const err = event.data as { status?: number; message?: string }
      streamError = new ApiError(
        'REQUEST_FAILED',
        err.message ?? 'Could not answer that question.',
        err.status ?? 502,
        null,
      )
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let sep = buffer.indexOf('\n\n')
    while (sep >= 0) {
      applyBlock(buffer.slice(0, sep))
      buffer = buffer.slice(sep + 2)
      sep = buffer.indexOf('\n\n')
    }
  }
  buffer += decoder.decode().replace(/\r\n/g, '\n')
  if (buffer.trim()) {
    applyBlock(buffer)
  }

  if (streamError) {
    throw streamError
  }
  if (!donePayload) {
    throw new ApiError('REQUEST_FAILED', 'The answer stream ended early.', 502, null)
  }
  return donePayload
}

function parseSseBlock(block: string): { event: string; data: unknown } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) {
    return null
  }
  const raw = dataLines.join('\n')
  try {
    return { event, data: JSON.parse(raw) }
  } catch {
    return { event, data: raw }
  }
}
