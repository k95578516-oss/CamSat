import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'
import { ApiError, toApiError } from './errors'

const config = { headers: {} } as InternalAxiosRequestConfig

function httpError(status: number, data: unknown) {
  return new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data,
  })
}

describe('toApiError', () => {
  it('maps network failures without a response', () => {
    const e = toApiError(new AxiosError('Network Error', 'ERR_NETWORK', config))
    expect(e.kind).toBe('network')
    expect(e.message).toMatch(/cannot reach/i)
  })

  it('maps timeouts', () => {
    expect(toApiError(new AxiosError('timeout', 'ECONNABORTED', config)).kind).toBe('timeout')
  })

  it('uses the backend message and field errors for 400 responses', () => {
    const e = toApiError(httpError(400, { status: 400, message: 'Validation failed', fieldErrors: { name: 'Mission name is required' } }))
    expect(e.kind).toBe('validation')
    expect(e.message).toBe('Validation failed')
    expect(e.fieldErrors).toEqual({ name: 'Mission name is required' })
  })

  it('maps 401, 403, 404 and 409', () => {
    expect(toApiError(httpError(401, {})).kind).toBe('unauthorized')
    expect(toApiError(httpError(403, {})).kind).toBe('forbidden')
    expect(toApiError(httpError(404, {})).kind).toBe('not_found')
    expect(toApiError(httpError(409, { message: 'Mission already has runs' })).message).toBe('Mission already has runs')
  })

  it('never shows the body message of a 5xx response', () => {
    const e = toApiError(httpError(500, { message: 'SQL error: could not execute statement at com.example.Foo' }))
    expect(e.kind).toBe('server')
    expect(e.message).not.toMatch(/sql|com\.example/i)
  })

  it('passes ApiError instances through unchanged', () => {
    const original = new ApiError('conflict', 'x', 409)
    expect(toApiError(original)).toBe(original)
  })
})
