/** Persist in-progress quiz answers across navigation (session only). */
const PREFIX = 'uc-quiz-answers:'

export function loadQuizAnswers(quizId: string): Record<string, number> {
  if (!quizId || typeof sessionStorage === 'undefined') {
    return {}
  }
  try {
    const raw = sessionStorage.getItem(PREFIX + quizId)
    if (!raw) {
      return {}
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const out: Record<string, number> = {}
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === 'number' && Number.isInteger(value) && value >= 0) {
        out[key] = value
      }
    }
    return out
  } catch {
    return {}
  }
}

export function saveQuizAnswers(quizId: string, answers: Record<string, number>) {
  if (!quizId || typeof sessionStorage === 'undefined') {
    return
  }
  try {
    if (Object.keys(answers).length === 0) {
      sessionStorage.removeItem(PREFIX + quizId)
      return
    }
    sessionStorage.setItem(PREFIX + quizId, JSON.stringify(answers))
  } catch {
    // Ignore quota / private-mode failures.
  }
}

export function clearQuizAnswers(quizId: string) {
  if (!quizId || typeof sessionStorage === 'undefined') {
    return
  }
  try {
    sessionStorage.removeItem(PREFIX + quizId)
  } catch {
    // ignore
  }
}
