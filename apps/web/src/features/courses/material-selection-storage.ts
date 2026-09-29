/** Remember last material selection per course + study mode. */
const PREFIX = 'uc-material-selection:'

export type StudyPickerKind = 'qa' | 'flashcards' | 'quiz'

function storageKey(kind: StudyPickerKind, courseId: string) {
  return `${PREFIX}${kind}:${courseId}`
}

function store(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

export function loadMaterialSelection(kind: StudyPickerKind, courseId: string): string[] {
  const storage = store()
  if (!storage || !courseId) {
    return []
  }
  try {
    const raw = storage.getItem(storageKey(kind, courseId))
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.map(String).filter(Boolean)
  } catch {
    return []
  }
}

export function saveMaterialSelection(kind: StudyPickerKind, courseId: string, materialIds: string[]) {
  const storage = store()
  if (!storage || !courseId) {
    return
  }
  try {
    if (materialIds.length === 0) {
      storage.removeItem(storageKey(kind, courseId))
      return
    }
    storage.setItem(storageKey(kind, courseId), JSON.stringify(materialIds))
  } catch {
    // ignore
  }
}

export function clearAllMaterialSelections() {
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
