export type ContinueStep = {
  date: string
  kind: 'QUIZ' | 'GAP' | 'REVIEW' | 'EXAM_PASSED'
  prompt: string | null
  missCount: number
}

export function localIsoDate(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** The step a student should do today, or the next gap if today is only a review. */
export function todayStep(steps: ContinueStep[] | undefined, today: string): ContinueStep | null {
  if (!steps || steps.length === 0) {
    return null
  }
  const actionable = steps.filter((step) => step.kind === 'GAP' || step.kind === 'QUIZ')
  return actionable.find((step) => step.date === today) ?? actionable.find((step) => step.date >= today) ?? null
}

const MARKS: { kind: string; label: string }[] = [
  { kind: 'SUMMARY', label: 'Summarized' },
  { kind: 'CARDS', label: 'Cards' },
  { kind: 'QUIZ', label: 'Quizzed' },
  { kind: 'CHAT', label: 'Chat' },
]

export function lectureMarks(kinds: string[] | undefined): string[] {
  const labels = MARKS.filter((mark) => kinds?.includes(mark.kind)).map((mark) => mark.label)
  return labels.length > 0 ? labels : ['Not started']
}
