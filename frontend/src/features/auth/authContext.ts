import { createContext, useContext } from 'react'
import type { AccountResponse } from '../../shared/types/api'

export type AuthContextValue = {
  token: string | null
  sessionId: string | null
  profile: AccountResponse | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshProfile: () => Promise<AccountResponse | null>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
