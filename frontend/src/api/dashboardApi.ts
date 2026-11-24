import { apiClient, get } from './client'
import type { AnalyticsResponse, DashboardResponse } from '../types/api'

export const dashboardApi = {
  dashboard: (signal?: AbortSignal) => get<DashboardResponse>('/api/dashboard', { signal }),
  analytics: (signal?: AbortSignal) => get<AnalyticsResponse>('/api/analytics', { signal }),
  /**
   * Connectivity probe: any HTTP answer (even 401) proves the backend is reachable.
   * The backend has no dedicated health endpoint yet (see FRONTEND_BACKEND_GAPS.md).
   */
  probe: async (): Promise<{ reachable: boolean; status?: number; latencyMs: number }> => {
    const t0 = performance.now()
    try {
      const res = await apiClient.get('/api/dashboard', { validateStatus: () => true, timeout: 5000 })
      return { reachable: true, status: res.status, latencyMs: Math.round(performance.now() - t0) }
    } catch {
      return { reachable: false, latencyMs: Math.round(performance.now() - t0) }
    }
  },
}
