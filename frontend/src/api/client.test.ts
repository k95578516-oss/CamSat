import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient, get, post, setUnauthorizedHandler } from './client'
import { ApiError } from './errors'
import { saveSession } from './tokenStore'
import { fakeSession } from '../test/utils'

const original = apiClient.defaults.adapter
let seen: InternalAxiosRequestConfig | null = null

function respondWith(status: number, data: unknown): AxiosAdapter {
  return async (config) => {
    seen = config
    const response = { data, status, statusText: '', headers: {}, config }
    if (status >= 400) throw new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, response)
    return response
  }
}

beforeEach(() => {
  seen = null
})
afterEach(() => {
  apiClient.defaults.adapter = original
  setUnauthorizedHandler(null)
})

describe('apiClient', () => {
  it('attaches the bearer token when a session exists', async () => {
    saveSession(fakeSession({ token: 'abc.def.ghi' }))
    apiClient.defaults.adapter = respondWith(200, { ok: true })
    await get('/api/missions')
    expect(seen?.headers.get('Authorization')).toBe('Bearer abc.def.ghi')
  })

  it('sends no Authorization header without a session', async () => {
    apiClient.defaults.adapter = respondWith(200, {})
    await post('/api/auth/login', { email: 'a@b.c', password: 'x' })
    expect(seen?.headers.has('Authorization')).toBe(false)
  })

  it('ends the session on 401 for an authenticated request', async () => {
    saveSession(fakeSession())
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = respondWith(401, { message: 'expired' })
    await expect(get('/api/missions')).rejects.toMatchObject({ kind: 'unauthorized' })
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not treat a failed login (no token) as an expired session', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = respondWith(401, { message: 'Bad credentials' })
    await expect(post('/api/auth/login', {})).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()
  })

  it('keeps the session on 403', async () => {
    saveSession(fakeSession())
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = respondWith(403, {})
    await expect(get('/api/missions/9')).rejects.toMatchObject({ kind: 'forbidden' })
    expect(handler).not.toHaveBeenCalled()
  })
})
