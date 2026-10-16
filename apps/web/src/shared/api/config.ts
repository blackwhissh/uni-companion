const identityDefault = 'http://localhost:8081'
const learningDefault = 'http://localhost:8082'

/**
 * Normal local/prod: absolute URL or path prefix (/identity, /learning).
 * QA Vite mode (`.env.qa`): empty string = same-origin + dev proxy for tunnel cookies.
 */
function envBase(
  name: 'VITE_IDENTITY_API_URL' | 'VITE_LEARNING_API_URL',
  fallback: string,
): string {
  const raw = import.meta.env[name]
  if (raw === undefined || raw === null) {
    return fallback
  }
  const trimmed = String(raw).trim()
  if (trimmed === '' || trimmed === '/') {
    return ''
  }
  return trimmed.replace(/\/$/, '')
}

export const identityApiUrl = envBase('VITE_IDENTITY_API_URL', identityDefault)
export const learningApiUrl = envBase('VITE_LEARNING_API_URL', learningDefault)
