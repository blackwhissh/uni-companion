import { describe, expect, it } from 'vitest'
import { noteFromAnswer, sectionLabel, splitStudyAnswer } from './study-answer-sections.ts'

describe('splitStudyAnswer', () => {
  it('separates the result from the walkthrough and the terms', () => {
    const sections = splitStudyAnswer(`## Direct answer
Consensus keeps replicas consistent. [1]

## Explanation
1. Replicas agree on one value. [1]
**Result:** replicas stay consistent. [1]

## Key terms
- **Consensus** — agreement on one value [1]
`)

    expect(sections.map((section) => section.kind)).toEqual(['result', 'walkthrough', 'terms'])
    expect(sectionLabel(sections[0])).toBe('The result')
    expect(sectionLabel(sections[1])).toBe('Walk through it')
    expect(sectionLabel(sections[2])).toBe('Remember')
    expect(sections[0].body).toContain('Consensus keeps replicas consistent.')
    expect(sections[0].body).not.toContain('##')
  })

  it('uses German labels when the reply is in German', () => {
    const sections = splitStudyAnswer(`## Direkte Antwort
Konsens hält Replikate konsistent. [1]

## Erklärung
1. Die Replikate einigen sich. [1]
`)

    expect(sectionLabel(sections[0])).toBe('Das Ergebnis')
    expect(sectionLabel(sections[1])).toBe('Schritt für Schritt')
  })

  it('leaves a refusal without tutor headings as one block', () => {
    const sections = splitStudyAnswer('I could not find that in the lectures you picked.')
    expect(sections).toEqual([
      {
        kind: 'plain',
        heading: null,
        body: 'I could not find that in the lectures you picked.',
      },
    ])
    expect(sectionLabel(sections[0])).toBeNull()
  })

  it('saves only the result, titled with the question', () => {
    expect(
      noteFromAnswer('What is consensus?', `## Direct answer\nReplicas stay consistent. [1]\n\n## Explanation\nLonger walkthrough.`),
    ).toEqual({
      title: 'What is consensus?',
      body: 'Replicas stay consistent. [1]',
    })
    expect(noteFromAnswer('Hi', 'No heading here.')).toBeNull()
  })
})
