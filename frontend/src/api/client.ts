import axios, { type AxiosRequestConfig } from 'axios'
import { env } from '../config/env'
import { getToken } from './tokenStore'
import { toApiError } from './errors'

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

/** Registered by the auth provider so a 401 on an authenticated request ends the session. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler
}

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.apiTimeoutMs,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = getToken()
  // Never log the token or the Authorization header.
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiError = toApiError(error)
    const hadToken = Boolean(axios.isAxiosError(error) && error.config?.headers?.has('Authorization'))
    if (apiError.kind === 'unauthorized' && hadToken) onUnauthorized?.()
    return Promise.reject(apiError)
  },
)

export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return (await apiClient.get<T>(url, config)).data
}
export async function post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return (await apiClient.post<T>(url, body, config)).data
}
export async function put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return (await apiClient.put<T>(url, body, config)).data
}
export async function del(url: string, config?: AxiosRequestConfig): Promise<void> {
  await apiClient.delete(url, config)
}
