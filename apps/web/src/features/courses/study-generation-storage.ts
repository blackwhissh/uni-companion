/** Remember an in-flight study generation so F5 can resume instead of dropping to the picker. */
const PREFIX = 'uc-study-gen:'

export type StudyGenKind = 'flashcards' | 'quiz'

export type StudyGenState = {
  materialIds: string[]
  force: boolean
  startedAt: number
}

function key(kind: StudyGenKind, courseId: string) {
  return `${PREFIX}${kind}:${courseId}`
}

function store(): Storage | null {
  try {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage
  } catch {
    return null
  }
}

export function saveStudyGeneration(kind: StudyGenKind, courseId: string, state: StudyGenState) {
  const storage = store()
  if (!storage || !courseId) {
    return
  }
  try {
    storage.setItem(key(kind, courseId), JSON.stringify(state))
  } catch {
    // ignore
  }
}

export function loadStudyGeneration(kind: StudyGenKind, courseId: string): StudyGenState | null {
  const storage = store()
  if (!storage || !courseId) {
    return null
  }
  try {
    const raw = storage.getItem(key(kind, courseId))
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as StudyGenState
    if (!Array.isArray(parsed.materialIds) || parsed.materialIds.length === 0) {
      return null
    }
    // Drop stale locks older than 15 minutes.
    if (Date.now() - (parsed.startedAt ?? 0) > 15 * 60_000) {
      storage.removeItem(key(kind, courseId))
      return null
    }
    return {
      materialIds: parsed.materialIds.map(String),
      force: parsed.force === true,
      startedAt: parsed.startedAt,
    }
  } catch {
    return null
  }
}

export function clearStudyGeneration(kind: StudyGenKind, courseId: string) {
  const storage = store()
  if (!storage || !courseId) {
    return
  }
  try {
    storage.removeItem(key(kind, courseId))
  } catch {
    // ignore
  }
}

export function clearAllStudyGenerations() {
  const storage = store()
  if (!storage) {
    return
  }
  try {
    const toRemove: string[] = []
    for (let i = 0; i < storage.length; i++) {
      const entry = storage.key(i)
      if (entry?.startsWith(PREFIX)) {
        toRemove.push(entry)
      }
    }
    for (const entry of toRemove) {
      storage.removeItem(entry)
    }
  } catch {
    // ignore
  }
}
