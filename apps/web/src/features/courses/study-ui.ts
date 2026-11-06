import { ApiError } from '../../shared/api/identity-client.ts'

export function studyWorkingCopy(noun: 'deck' | 'quiz', force: boolean) {
  if (force) {
    return `Making another ${noun} from the lectures you picked…`
  }
  return `Putting a ${noun} together from the lectures you picked. This can take a minute if we need a new version…`
}

export function studyActionError(err: unknown, fallback: string) {
  if (!(err instanceof ApiError)) {
    return fallback
  }
  if (err.status === 503) {
    return 'Still working on that. Give it a moment, then try again.'
  }
  return err.message
}
