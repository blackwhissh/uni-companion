/** Honest study-partner count. The number is the live opt-in total for one course. */
export function matchCountLabel(activeCount: number, optedIn: boolean): string {
  const count = Number.isFinite(activeCount) ? Math.max(0, Math.floor(activeCount)) : 0
  if (count === 0) {
    return 'No students in this course are looking for a study partner yet.'
  }
  if (count === 1 && optedIn) {
    return 'You are the only student in this course looking for a study partner.'
  }
  if (count === 1) {
    return '1 student in this course is looking for a study partner.'
  }
  const includingYou = optedIn ? ', including you' : ''
  return `${count} students in this course are looking for a study partner${includingYou}.`
}
