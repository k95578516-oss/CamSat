import { describe, expect, it } from 'vitest'
import { clearSession, loadSession, saveSession, sessionFromAuth } from './tokenStore'
import { fakeSession } from '../test/utils'

describe('tokenStore', () => {
  it('round-trips a valid session', () => {
    saveSession(fakeSession({ token: 't1' }))
    expect(loadSession()?.token).toBe('t1')
  })

  it('discards and removes an expired session', () => {
    saveSession(fakeSession({ expiresAt: Date.now() - 1000 }))
    expect(loadSession()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('ignores corrupt storage', () => {
    sessionStorage.setItem('camsat.session', '{not json')
    expect(loadSession()).toBeNull()
  })

  it('computes the expiry from expiresInSeconds', () => {
    const s = sessionFromAuth(
      { token: 't', tokenType: 'Bearer', expiresInSeconds: 3600, userId: 1, name: 'A', email: 'a@b.c', role: 'USER', createdAt: '' },
      1_000,
    )
    expect(s.expiresAt).toBe(1_000 + 3_600_000)
  })

  it('clears the session', () => {
    saveSession(fakeSession())
    clearSession()
    expect(loadSession()).toBeNull()
  })
})
