import axios from 'axios'
import type { ApiErrorBody } from '../types/api'

export type ApiErrorKind =
  | 'network'
  | 'timeout'
  | 'cancelled'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'validation'
  | 'server'
  | 'unknown'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status?: number
  readonly fieldErrors: Record<string, string>

  constructor(kind: ApiErrorKind, message: string, status?: number, fieldErrors?: Record<string, string> | null) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.fieldErrors = fieldErrors ?? {}
  }
}

function kindFromStatus(status: number): ApiErrorKind {
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  if (status === 404) return 'not_found'
  if (status === 409) return 'conflict'
  if (status === 400 || status === 422) return 'validation'
  if (status >= 500) return 'server'
  return 'unknown'
}

const FALLBACK: Record<ApiErrorKind, string> = {
  network: 'Cannot reach the CamSat backend. Check that it is running and that the API URL is correct.',
  timeout: 'The request timed out before the backend answered.',
  cancelled: 'The request was cancelled.',
  unauthorized: 'Your session is missing or has expired. Sign in again.',
  forbidden: 'Your account is not allowed to perform this action.',
  not_found: 'The requested resource was not found.',
  conflict: 'The request conflicts with the current state of the resource.',
  validation: 'The backend rejected the submitted values.',
  server: 'The backend reported an internal error. Try again later.',
  unknown: 'Something went wrong while talking to the backend.',
}

/** Converts any thrown value into an ApiError with a safe, user-facing message. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isCancel(error)) return new ApiError('cancelled', FALLBACK.cancelled)
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError('timeout', FALLBACK.timeout)
    }
    if (!error.response) return new ApiError('network', FALLBACK.network)
    const status = error.response.status
    const kind = kindFromStatus(status)
    const body = (typeof error.response.data === 'object' ? error.response.data : null) as ApiErrorBody | null
    // 5xx bodies may contain internals: only trust the message for client errors.
    const serverMessage = status < 500 && typeof body?.message === 'string' && body.message.trim() ? body.message : null
    return new ApiError(kind, serverMessage ?? FALLBACK[kind], status, body?.fieldErrors ?? null)
  }
  return new ApiError('unknown', FALLBACK.unknown)
}
