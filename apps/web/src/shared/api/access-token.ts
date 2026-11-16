/**
 * Access tokens live in memory only. Refresh uses the HttpOnly cookie.
 * Avoid localStorage so a XSS bug cannot read a long-lived bearer token.
 */
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export function clearAccessToken() {
  accessToken = null
}
