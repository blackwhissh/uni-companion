import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { setAccessToken } from '../shared/api/access-token.ts'
import { blockTokenRefresh } from '../shared/api/identity-client.ts'
import { clearAllMaterialSelections } from '../features/courses/material-selection-storage.ts'
import { clearAllQuizAnswers } from '../features/courses/quiz-answer-storage.ts'
import { clearAllStudyGenerations } from '../features/courses/study-generation-storage.ts'
import { markLogoutNavigation, subscribeAuthLogout } from '../features/auth/auth-session-sync.ts'
import { useAuth } from '../features/auth/use-auth.ts'

/**
 * Keeps other open tabs in sync when one tab logs out (lab PCs / shared browsers).
 */
export function AuthSessionSync() {
  const { setUser } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  useEffect(() => {
    return subscribeAuthLogout(() => {
      markLogoutNavigation()
      blockTokenRefresh()
      setAccessToken(null)
      clearAllQuizAnswers()
      clearAllMaterialSelections()
      clearAllStudyGenerations()
      queryClient.clear()
      setUser(null)
      navigate('/login', { replace: true, state: {} })
    })
  }, [navigate, queryClient, setUser])

  return null
}
