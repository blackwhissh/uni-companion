import { describe, expect, it } from 'vitest'
import { ApiError } from './identity-client.ts'
import { userFacingError } from './error-catalog.ts'

describe('userFacingError', () => {
  it('keeps specific BAD_REQUEST messages from the API', () => {
    expect(userFacingError(new ApiError('BAD_REQUEST', 'Pick at least one lecture.', 400), 'fallback')).toBe(
      'Pick at least one lecture.',
    )
  })

  it('maps generic status-named codes to catalog copy', () => {
    expect(userFacingError(new ApiError('SERVICE_UNAVAILABLE', 'Service Unavailable', 503), 'fallback')).toBe(
      'Still working on that. Give it a moment, then try again.',
    )
  })

  it('falls back for unknown errors', () => {
    expect(userFacingError(new Error('boom'), 'Could not answer that.')).toBe('Could not answer that.')
  })
})
