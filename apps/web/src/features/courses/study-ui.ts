import { ApiError } from '../../shared/api/identity-client.ts'

export function studyWorkingCopy(noun: 'deck' | 'quiz', force: boolean) {
  if (force) {
    return `Creating another shared ${noun} from your selected materials…`
  }
  return `Preparing a shared ${noun} for your selected materials. This can take a minute if a new version is needed…`
}

export function studyActionError(err: unknown, fallback: string) {
  if (!(err instanceof ApiError)) {
    return fallback
  }
  if (err.status === 503) {
    return 'Generation is still running. Wait a moment, then try again.'
  }
  return err.message
}
