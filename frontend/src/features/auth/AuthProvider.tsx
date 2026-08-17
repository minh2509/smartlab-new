import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { apiClient } from '../../lib/apiClient'
import type { AccountResponse, AuthResponse } from '../../shared/types/api'
import { AuthContext } from './authContext'

const TOKEN_KEY = 'smartlab.token'
const SESSION_KEY = 'smartlab.sessionId'

export function AuthProvider({ children }: { children: ReactNode }) {
  const tokenRef = useRef<string | null>(localStorage.getItem(TOKEN_KEY))
  const [token, setToken] = useState<string | null>(() => tokenRef.current)
  const [sessionId, setSessionId] = useState<string | null>(() => localStorage.getItem(SESSION_KEY))
  const [profile, setProfile] = useState<AccountResponse | null>(null)
  const [isHydrating, setIsHydrating] = useState(() => Boolean(tokenRef.current))
  const profileRequestRef = useRef<{ token: string; request: Promise<AccountResponse> } | null>(null)

  const isAuthenticated = Boolean(token && profile && !isHydrating)

  const persistAuth = useCallback((result: AuthResponse) => {
    profileRequestRef.current = null
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(SESSION_KEY, result.sessionId)
    tokenRef.current = result.token
    setToken(result.token)
    setSessionId(result.sessionId)
    setProfile(null)
    setIsHydrating(true)
  }, [])

  const clearAuth = useCallback(() => {
    profileRequestRef.current = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(SESSION_KEY)
    tokenRef.current = null
    setToken(null)
    setSessionId(null)
    setProfile(null)
    setIsHydrating(false)
  }, [])

  const hydrateProfile = useCallback((sessionToken: string) => {
    if (profileRequestRef.current?.token === sessionToken) return profileRequestRef.current.request

    setIsHydrating(true)
    const request = apiClient<AccountResponse>('/profile', { token: sessionToken })
      .then((data) => {
        if (tokenRef.current === sessionToken) setProfile(data)
        return data
      })
      .catch((error: unknown) => {
        if (tokenRef.current === sessionToken) clearAuth()
        throw error
      })
      .finally(() => {
        if (profileRequestRef.current?.request === request) {
          profileRequestRef.current = null
          if (tokenRef.current === sessionToken) setIsHydrating(false)
        }
      })
    profileRequestRef.current = { token: sessionToken, request }
    return request
  }, [clearAuth])

  const refreshProfile = useCallback(() => {
    if (!token) return Promise.resolve(null)
    return hydrateProfile(token)
  }, [hydrateProfile, token])

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await apiClient<AuthResponse>('/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      persistAuth(result)
      await hydrateProfile(result.token)
    },
    [hydrateProfile, persistAuth],
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
    if (!token) {
      setIsHydrating(false)
      return
    }
    void hydrateProfile(token).catch(() => undefined)
  }, [hydrateProfile, token])

  const value = useMemo(
    () => ({ token, sessionId, profile, isAuthenticated, isHydrating, login, logout, clearAuth, refreshProfile }),
    [clearAuth, isAuthenticated, isHydrating, login, logout, profile, refreshProfile, sessionId, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
