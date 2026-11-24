import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../../api/authApi'
import { setUnauthorizedHandler } from '../../api/client'
import {
  clearSession,
  loadSession,
  saveSession,
  sessionFromAuth,
  type StoredSession,
} from '../../api/tokenStore'
import type { LoginRequest, RegisterRequest } from '../../types/api'

interface AuthState {
  session: StoredSession | null
  user: StoredSession['user'] | null
  isAuthenticated: boolean
  /** Set when a session ended because the token expired or the backend returned 401. */
  sessionExpired: boolean
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegisterRequest) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<StoredSession | null>(() => loadSession())
  const [sessionExpired, setSessionExpired] = useState(false)

  const endSession = useCallback(
    (expired: boolean) => {
      clearSession()
      setSession(null)
      setSessionExpired(expired)
      queryClient.clear()
    },
    [queryClient],
  )

  // A 401 on an authenticated request means the token is invalid or expired.
  useEffect(() => {
    setUnauthorizedHandler(() => endSession(true))
    return () => setUnauthorizedHandler(null)
  }, [endSession])

  // Log out locally when the token reaches its expiry time.
  useEffect(() => {
    if (!session) return
    const ms = session.expiresAt - Date.now()
    if (ms <= 0) {
      endSession(true)
      return
    }
    const id = window.setTimeout(() => endSession(true), Math.min(ms, 2_147_000_000))
    return () => window.clearTimeout(id)
  }, [session, endSession])

  const accept = useCallback((s: StoredSession) => {
    saveSession(s)
    setSession(s)
    setSessionExpired(false)
  }, [])

  const login = useCallback(
    async (body: LoginRequest) => accept(sessionFromAuth(await authApi.login(body))),
    [accept],
  )
  const register = useCallback(
    async (body: RegisterRequest) => accept(sessionFromAuth(await authApi.register(body))),
    [accept],
  )
  const logout = useCallback(() => endSession(false), [endSession])

  const value = useMemo<AuthState>(
    () => ({
      session,
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      sessionExpired,
      login,
      register,
      logout,
    }),
    [session, sessionExpired, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
