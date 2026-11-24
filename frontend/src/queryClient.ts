import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/errors'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        // Retry once, and only for transient failures (never for 4xx).
        retry: (count, error) =>
          count < 1 && error instanceof ApiError && (error.kind === 'network' || error.kind === 'server'),
      },
      mutations: { retry: false },
    },
  })
}
