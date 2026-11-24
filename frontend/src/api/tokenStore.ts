import type { AuthResponse } from '../types/api'

/**
 * The backend returns the JWT in the response body (no cookie, no refresh endpoint),
 * so the token is kept in sessionStorage (cleared when the tab closes).
 * Trade-off: script running on this origin can read it, so avoid unsafe HTML and keep dependencies lean.
 */
const KEY = 'camsat.session'

export interface StoredSession {
  token: string
  tokenType: string
  expiresAt: number
  user: { id: number; name: string; email: string; role: string; createdAt: string }
}

export function sessionFromAuth(auth: AuthResponse, now = Date.now()): StoredSession {
  return {
    token: auth.token,
    tokenType: auth.tokenType || 'Bearer',
    expiresAt: now + auth.expiresInSeconds * 1000,
    user: { id: auth.userId, name: auth.name, email: auth.email, role: auth.role, createdAt: auth.createdAt },
  }
}

export function loadSession(now = Date.now()): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as StoredSession
    if (!s?.token || typeof s.expiresAt !== 'number' || s.expiresAt <= now) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return s
  } catch {
    return null
  }
}

export function saveSession(session: StoredSession): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    /* storage unavailable: session lasts for this page view only */
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}

export function getToken(): string | null {
  return loadSession()?.token ?? null
}
