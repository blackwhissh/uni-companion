import { describe, expect, it } from 'vitest'
import { studyWorkingCopy } from './study-ui.ts'

describe('studyWorkingCopy', () => {
  it('explains reuse vs force in plain language', () => {
    expect(studyWorkingCopy('deck', false)).toMatch(/Putting a deck together/)
    expect(studyWorkingCopy('quiz', true)).toMatch(/Making another quiz/)
  })
})
