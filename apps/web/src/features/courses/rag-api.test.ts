import { describe, expect, it } from 'vitest'
import { consumeSseStream } from './rag-api.ts'

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  let i = 0
  return new ReadableStream({
    pull(controller) {
      if (i >= chunks.length) {
        controller.close()
        return
      }
      controller.enqueue(encoder.encode(chunks[i++]))
    },
  })
}

describe('consumeSseStream', () => {
  it('emits status and deltas as chunks arrive, before done', async () => {
    const statuses: string[] = []
    const deltas: string[] = []
    const body = streamOf([
      'event: status\ndata: {"phase":"retrieving"}\n\n',
      'event: status\ndata: {"phase":"generating"}\n\n',
      'event: answer\ndata: {"delta":"Hello"}\n\n',
      'event: answer\ndata: {"delta":" world"}\n\n',
      'event: done\ndata: {"answer":"Hello world","citations":[]}\n\n',
    ])

    const result = await consumeSseStream(body, {
      onStatus: (phase) => statuses.push(phase),
      onDelta: (delta) => deltas.push(delta),
    })

    expect(statuses).toEqual(['retrieving', 'generating'])
    expect(deltas).toEqual(['Hello', ' world'])
    expect(result).toEqual({ answer: 'Hello world', citations: [] })
  })

  it('parses events split across TCP chunks', async () => {
    const deltas: string[] = []
    const body = streamOf([
      'event: answer\ndata: {"delta":"par',
      'tial"}\n\nevent: done\ndata: {"answer":"partial","citations":[]}\n\n',
    ])

    const result = await consumeSseStream(body, {
      onDelta: (delta) => deltas.push(delta),
    })

    expect(deltas).toEqual(['partial'])
    expect(result.answer).toBe('partial')
  })

  it('throws when the stream ends without a done event', async () => {
    const body = streamOf(['event: status\ndata: {"phase":"retrieving"}\n\n'])
    await expect(consumeSseStream(body, {})).rejects.toMatchObject({
      message: 'The answer stream ended early.',
    })
  })
})
