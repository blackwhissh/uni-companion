import { describe, expect, it } from 'vitest'
import { draftTitleWarning, looksLikeDraftTitle, matchDraftTitle } from './draft-title.ts'

describe('draft title heuristic', () => {
  it('ignores bracketed TEST lecture prefixes', () => {
    expect(looksLikeDraftTitle('[TEST] Lecture 3 – Sorting')).toBe(false)
    expect(matchDraftTitle('[TEST] Lecture 3 – Sorting')).toEqual({ matched: false })
  })

  it('flags real draft markers and names the trigger word', () => {
    expect(looksLikeDraftTitle('[TEST] Vorlesung 4 – Hashing (Entwurf)')).toBe(true)
    expect(draftTitleWarning('[TEST] Vorlesung 4 – Hashing (Entwurf)')).toContain('Entwurf')
  })
})
