export type User = {
  id: string
  email: string
  displayName: string
  interests: string | null
  roles: string[]
  emailVerified?: boolean
  createdAt: string
}

export type AuthResponse = {
  token: string
  user: User
}
