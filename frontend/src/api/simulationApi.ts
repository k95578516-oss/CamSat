import { get, post } from './client'
import { env } from '../config/env'
import type { SimulationRunResponse } from '../types/api'

export const simulationApi = {
  createRun: (missionId: number) => post<SimulationRunResponse>(`/api/missions/${missionId}/runs`),
  runsByMission: (missionId: number, signal?: AbortSignal) =>
    get<SimulationRunResponse[]>(`/api/missions/${missionId}/runs`, { signal }),
  getRun: (runId: number, signal?: AbortSignal) => get<SimulationRunResponse>(`/api/runs/${runId}`, { signal }),
  recentRuns: (signal?: AbortSignal) => get<SimulationRunResponse[]>('/api/runs/recent', { signal }),
  /**
   * The backend answers 202 Accepted with the run in RUNNING state and simulates in the background;
   * poll getRun for progress. (The long timeout is kept so a legacy blocking backend still works.)
   */
  start: (runId: number) =>
    post<SimulationRunResponse>(`/api/runs/${runId}/start`, undefined, { timeout: env.simulationStartTimeoutMs }),
  pause: (runId: number) => post<SimulationRunResponse>(`/api/runs/${runId}/pause`),
  resume: (runId: number) => post<SimulationRunResponse>(`/api/runs/${runId}/resume`),
  cancel: (runId: number) => post<SimulationRunResponse>(`/api/runs/${runId}/cancel`),
}
