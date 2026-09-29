import { describe, expect, it } from 'vitest'
import { ApiError } from '../../shared/api/identity-client.ts'
import { studyActionError, studyWorkingCopy } from './study-ui.ts'

describe('study UI copy', () => {
  it('explains reused vs new generation', () => {
    expect(studyWorkingCopy('deck', false)).toMatch(/Preparing a shared deck/)
    expect(studyWorkingCopy('quiz', true)).toMatch(/Creating another shared quiz/)
  })

  it('maps a 503 generation wait to a retryable message', () => {
    expect(studyActionError(new ApiError('UNAVAILABLE', 'Generation is taking longer than expected.', 503), 'fallback')).toBe(
      'Generation is still running. Wait a moment, then try again.',
    )
  })

  it('keeps the server message for other API errors', () => {
    expect(studyActionError(new ApiError('BAD_GATEWAY', 'The model returned no usable quiz.', 502), 'fallback')).toBe(
      'The model returned no usable quiz.',
    )
  })
})
