import { get } from './client'
import type { RecoveryResponse } from '../types/api'

export const recoveryApi = {
  get: (runId: number, signal?: AbortSignal) => get<RecoveryResponse>(`/api/runs/${runId}/recovery`, { signal }),
}
