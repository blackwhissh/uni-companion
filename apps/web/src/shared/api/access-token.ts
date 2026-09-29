const STORAGE_KEY = 'uc_access_token'

let accessToken: string | null = null

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function setAccessToken(token: string | null) {
  accessToken = token
  try {
    if (token) {
      localStorage.setItem(STORAGE_KEY, token)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // ignore quota / private mode
  }
}

export function getAccessToken() {
  if (accessToken) {
    return accessToken
  }
  const stored = readStored()
  accessToken = stored
  return accessToken
}

export function clearAccessToken() {
  setAccessToken(null)
}
