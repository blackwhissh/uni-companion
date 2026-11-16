import { userFacingError } from '../../shared/api/error-catalog.ts'

export function studyWorkingCopy(noun: 'deck' | 'quiz', force: boolean) {
  if (force) {
    return `Making another ${noun} from the lectures you picked…`
  }
  return `Putting a ${noun} together from the lectures you picked. This can take a minute if we need a new version…`
}

export function studyActionError(err: unknown, fallback: string) {
  return userFacingError(err, fallback)
}
