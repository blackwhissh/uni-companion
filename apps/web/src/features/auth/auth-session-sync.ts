/** Cross-tab logout + skip stale return-path after logout (lab PC / shared browser). */

const SKIP_RETURN_KEY = 'uc_skip_return_path'
const AUTH_EVENT_KEY = 'uc_auth_event'
const CHANNEL_NAME = 'uc-auth'

export function markLogoutNavigation() {
  try {
    sessionStorage.setItem(SKIP_RETURN_KEY, '1')
  } catch {
    // ignore
  }
}

export function shouldSkipReturnPath(): boolean {
  try {
    return sessionStorage.getItem(SKIP_RETURN_KEY) === '1'
  } catch {
    return false
  }
}

/** Clears the flag and returns whether a logout had asked to skip return-to. */
export function consumeSkipReturnPath(): boolean {
  try {
    if (sessionStorage.getItem(SKIP_RETURN_KEY) === '1') {
      sessionStorage.removeItem(SKIP_RETURN_KEY)
      return true
    }
  } catch {
    // ignore
  }
  return false
}

export function broadcastAuthLogout() {
  markLogoutNavigation()
  try {
    const channel = new BroadcastChannel(CHANNEL_NAME)
    channel.postMessage({ type: 'logout' })
    channel.close()
  } catch {
    // BroadcastChannel unavailable (older browsers / opaque origins)
  }
  try {
    // storage events notify *other* tabs; same-tab listeners ignore this.
    localStorage.setItem(AUTH_EVENT_KEY, JSON.stringify({ type: 'logout', at: Date.now() }))
    localStorage.removeItem(AUTH_EVENT_KEY)
  } catch {
    // ignore
  }
}

export function subscribeAuthLogout(onLogout: () => void): () => void {
  let channel: BroadcastChannel | null = null
  const handleMessage = (event: MessageEvent) => {
    if (event.data?.type === 'logout') {
      onLogout()
    }
  }
  try {
    channel = new BroadcastChannel(CHANNEL_NAME)
    channel.addEventListener('message', handleMessage)
  } catch {
    channel = null
  }

  const handleStorage = (event: StorageEvent) => {
    if (event.key !== AUTH_EVENT_KEY || !event.newValue) {
      return
    }
    try {
      const payload = JSON.parse(event.newValue) as { type?: string }
      if (payload.type === 'logout') {
        onLogout()
      }
    } catch {
      // ignore
    }
  }
  window.addEventListener('storage', handleStorage)

  return () => {
    channel?.removeEventListener('message', handleMessage)
    channel?.close()
    window.removeEventListener('storage', handleStorage)
  }
}
