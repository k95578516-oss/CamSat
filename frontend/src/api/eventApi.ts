import { get } from './client'
import type { FlightEventResponse } from '../types/api'

export const eventApi = {
  list: (runId: number, signal?: AbortSignal) => get<FlightEventResponse[]>(`/api/runs/${runId}/events`, { signal }),
  latest: (runId: number, signal?: AbortSignal) =>
    get<FlightEventResponse[]>(`/api/runs/${runId}/events/latest`, { signal }),
  count: (runId: number, signal?: AbortSignal) => get<number>(`/api/runs/${runId}/events/count`, { signal }),
}
