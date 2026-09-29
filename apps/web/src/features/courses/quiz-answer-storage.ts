/** Persist in-progress quiz answers across tabs and reloads (local only). */
const PREFIX = 'uc-quiz-answers:'

function storageKey(quizId: string, userId?: string | null) {
  return PREFIX + (userId ? `${userId}:` : '') + quizId
}

function store(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

export function loadQuizAnswers(quizId: string, userId?: string | null): Record<string, number> {
  if (!quizId) {
    return {}
  }
  const storage = store()
  if (!storage) {
    return {}
  }
  try {
    const raw = storage.getItem(storageKey(quizId, userId)) ?? storage.getItem(PREFIX + quizId)
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

export function saveQuizAnswers(quizId: string, answers: Record<string, number>, userId?: string | null) {
  if (!quizId) {
    return
  }
  const storage = store()
  if (!storage) {
    return
  }
  try {
    const key = storageKey(quizId, userId)
    if (Object.keys(answers).length === 0) {
      storage.removeItem(key)
      return
    }
    storage.setItem(key, JSON.stringify(answers))
  } catch {
    // Ignore quota / private-mode failures.
  }
}

export function clearQuizAnswers(quizId: string, userId?: string | null) {
  if (!quizId) {
    return
  }
  const storage = store()
  if (!storage) {
    return
  }
  try {
    storage.removeItem(storageKey(quizId, userId))
    storage.removeItem(PREFIX + quizId)
  } catch {
    // ignore
  }
}

/** Remove every in-progress quiz answer from this browser (call on logout). */
export function clearAllQuizAnswers() {
  const storage = store()
  if (!storage) {
    return
  }
  try {
    const keys: string[] = []
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i)
      if (key && key.startsWith(PREFIX)) {
        keys.push(key)
      }
    }
    for (const key of keys) {
      storage.removeItem(key)
    }
  } catch {
    // ignore
  }
}
