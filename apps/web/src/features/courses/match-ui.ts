export const COLLABORATION_INTENTS = [
  { id: 'STUDY_GROUP', label: 'Study group' },
  { id: 'PROJECT', label: 'Project partner' },
  { id: 'THESIS', label: 'Thesis buddy' },
  { id: 'LANGUAGE', label: 'Language practice' },
] as const

export function intentLabel(id: string) {
  return COLLABORATION_INTENTS.find((intent) => intent.id === id)?.label ?? id
}

/** Honest study-partner count. The number is the live opt-in total for one course. */
export function matchCountLabel(activeCount: number, optedIn: boolean): string {
  const count = Number.isFinite(activeCount) ? Math.max(0, Math.floor(activeCount)) : 0
  if (count === 0) {
    return 'Nobody in this course is looking for a study partner yet.'
  }
  if (count === 1 && optedIn) {
    return 'You’re the only one looking right now — hang tight for classmates.'
  }
  if (count === 1) {
    return '1 classmate in this course is looking for a study partner.'
  }
  const includingYou = optedIn ? ', including you' : ''
  return `${count} classmates in this course are looking for a study partner${includingYou}.`
}
