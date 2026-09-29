import { AuthProvider } from '../features/auth/AuthProvider.tsx'
import { AppProviders } from './providers.tsx'
import { AppRouter } from './router.tsx'

export function App() {
  return (
    <AppProviders>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </AppProviders>
  )
}
