const identityDefault = 'http://localhost:8081'
const learningDefault = 'http://localhost:8082'

export const identityApiUrl = import.meta.env.VITE_IDENTITY_API_URL || identityDefault
export const learningApiUrl = import.meta.env.VITE_LEARNING_API_URL || learningDefault
