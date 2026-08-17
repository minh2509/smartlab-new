import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { apiClient } from '../../lib/apiClient'
import type { AccountResponse, AuthResponse } from '../../shared/types/api'
import { AuthContext } from './authContext'

const TOKEN_KEY = 'smartlab.token'
const SESSION_KEY = 'smartlab.sessionId'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [sessionId, setSessionId] = useState<string | null>(() => localStorage.getItem(SESSION_KEY))
  const [profile, setProfile] = useState<AccountResponse | null>(null)
  const profileRequestRef = useRef<Promise<AccountResponse | null> | null>(null)

  const isAuthenticated = Boolean(token)

  const persistAuth = useCallback((result: AuthResponse) => {
    profileRequestRef.current = null
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(SESSION_KEY, result.sessionId)
    setToken(result.token)
    setSessionId(result.sessionId)
  }, [])

  const clearAuth = useCallback(() => {
    profileRequestRef.current = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(SESSION_KEY)
    setToken(null)
    setSessionId(null)
    setProfile(null)
  }, [])

  const refreshProfile = useCallback(() => {
    if (!token) return Promise.resolve(null)
    if (profileRequestRef.current) return profileRequestRef.current

    const request = apiClient<AccountResponse>('/profile', { token })
      .then((data) => {
        setProfile(data)
        return data
      })
      .catch((error: unknown) => {
        clearAuth()
        throw error
      })
      .finally(() => {
        if (profileRequestRef.current === request) profileRequestRef.current = null
      })
    profileRequestRef.current = request
    return request
  }, [clearAuth, token])

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await apiClient<AuthResponse>('/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      persistAuth(result)
      const account = await apiClient<AccountResponse>('/profile', { token: result.token })
      setProfile(account)
    },
    [persistAuth],
  )

  const logout = useCallback(async () => {
    try {
      if (token) {
        await apiClient<string>('/logout', { method: 'POST', token })
      }
    } finally {
      clearAuth()
    }
  }, [clearAuth, token])

  useEffect(() => {
    if (!token) return
    void refreshProfile().catch(() => undefined)
  }, [refreshProfile, token])

  const value = useMemo(
    () => ({ token, sessionId, profile, isAuthenticated, login, logout, clearAuth, refreshProfile }),
    [clearAuth, isAuthenticated, login, logout, profile, refreshProfile, sessionId, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
