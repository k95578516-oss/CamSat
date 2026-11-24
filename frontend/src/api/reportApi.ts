import { get } from './client'
import type { MissionReportResponse } from '../types/api'

export const reportApi = {
  get: (runId: number, signal?: AbortSignal) =>
    get<MissionReportResponse>(`/api/runs/${runId}/report`, { signal }),
}
