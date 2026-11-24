import { get } from './client'
import type { TelemetryReadingResponse } from '../types/api'

export const telemetryApi = {
  /** Returns the whole recorded series (the backend has no pagination or time filter). */
  list: (runId: number, signal?: AbortSignal) =>
    get<TelemetryReadingResponse[]>(`/api/runs/${runId}/telemetry`, { signal }),
  latestReading: (runId: number, signal?: AbortSignal) =>
    get<TelemetryReadingResponse>(`/api/runs/${runId}/telemetry/latest-reading`, { signal }),
}
