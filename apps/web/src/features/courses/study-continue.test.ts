import { describe, expect, it } from 'vitest'
import { lectureMarks, todayStep } from './study-continue.ts'
import type { ContinueStep } from './study-continue.ts'

const gap = (date: string, prompt: string): ContinueStep => ({
  date,
  kind: 'GAP',
  prompt,
  missCount: 1,
})

describe('todayStep', () => {
  it('prefers the gap scheduled for today', () => {
    const step = todayStep(
      [gap('2026-11-16', 'What is Raft?'), gap('2026-11-17', 'What is a quorum?')],
      '2026-11-16',
    )
    expect(step?.prompt).toBe('What is Raft?')
  })

  it('moves to the next gap when today has none', () => {
    const step = todayStep([gap('2026-11-18', 'What is Raft?')], '2026-11-16')
    expect(step?.prompt).toBe('What is Raft?')
  })
})

describe('lectureMarks', () => {
  it('names what the student has already done with a lecture', () => {
    expect(lectureMarks(undefined)).toEqual(['Not started'])
    expect(lectureMarks(['CHAT', 'QUIZ'])).toEqual(['Quizzed', 'Chat'])
  })
})