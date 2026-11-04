import { describe, expect, it } from 'vitest'
import { matchCountLabel } from './match-ui.ts'

describe('matchCountLabel', () => {
  it('does not invent classmates when nobody has opted in', () => {
    expect(matchCountLabel(0, false)).toMatch(/No students/)
    expect(matchCountLabel(0, true)).toMatch(/No students/)
  })

  it('says when you are the only opted-in student', () => {
    expect(matchCountLabel(1, true)).toMatch(/only student/)
    expect(matchCountLabel(1, false)).toBe('1 student in this course is looking for a study partner.')
  })

  it('includes you in a larger real count only when you opted in', () => {
    expect(matchCountLabel(3, true)).toBe(
      '3 students in this course are looking for a study partner, including you.',
    )
    expect(matchCountLabel(3, false)).toBe('3 students in this course are looking for a study partner.')
  })
})
