import { ApiError } from './identity-client.ts'

/**
 * Maps stable API error codes (and a few status fallbacks) to student-facing copy.
 * Prefer codes over raw backend sentences so wording stays consistent across pages.
 */
const BY_CODE: Record<string, string> = {
  VALIDATION_FAILED: 'Check what you entered and try again.',
  INVALID_JSON: 'That request was not readable. Refresh and try again.',
  UPLOAD_TOO_LARGE: 'That file is too large to upload.',
  NOT_FOUND: 'Nothing is available at this address.',
  METHOD_NOT_ALLOWED: 'That action is not available here.',
  INTERNAL_ERROR: 'Something went wrong on our side. Try again shortly.',
  UNAUTHORIZED: 'Please log in again.',
  FORBIDDEN: 'You do not have permission to do that.',
  CONFLICT: 'That request is already in progress. Wait a moment, then try again.',
  UNPROCESSABLE_ENTITY: 'That request could not be applied as sent.',
  BAD_GATEWAY: 'A study service is temporarily unavailable. Try again shortly.',
  SERVICE_UNAVAILABLE: 'Still working on that. Give it a moment, then try again.',
  GATEWAY_TIMEOUT: 'That took too long. Try again in a moment.',
  STREAM_FAILED: 'Could not finish the study chat reply. Try again.',
  REQUEST_FAILED: 'Request failed. Try again.',
}

const BY_STATUS: Record<number, string> = {
  401: BY_CODE.UNAUTHORIZED,
  403: BY_CODE.FORBIDDEN,
  404: BY_CODE.NOT_FOUND,
  409: BY_CODE.CONFLICT,
  422: BY_CODE.UNPROCESSABLE_ENTITY,
  429: 'Too many requests. Wait a bit, then try again.',
  502: BY_CODE.BAD_GATEWAY,
  503: BY_CODE.SERVICE_UNAVAILABLE,
  504: BY_CODE.GATEWAY_TIMEOUT,
}

export function userFacingError(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) {
    return fallback
  }
  // Keep specific API reasons for validation / domain BAD_REQUEST messages.
  if (err.code === 'BAD_REQUEST' || err.code === 'VALIDATION_FAILED') {
    return err.message || BY_CODE.VALIDATION_FAILED
  }
  if (err.message && !isGenericHttpPhrase(err.message)) {
    return err.message
  }
  return BY_CODE[err.code] ?? BY_STATUS[err.status] ?? fallback
}

function isGenericHttpPhrase(message: string) {
  return /^(Bad Request|Unauthorized|Forbidden|Not Found|Conflict|Unprocessable Entity|Internal Server Error|Service Unavailable|Bad Gateway|Gateway Timeout)$/i.test(
    message.trim(),
  )
}

export function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
