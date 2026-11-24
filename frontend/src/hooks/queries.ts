import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { missionApi } from '../api/missionApi'
import { simulationApi } from '../api/simulationApi'
import { telemetryApi } from '../api/telemetryApi'
import { eventApi } from '../api/eventApi'
import { recoveryApi } from '../api/recoveryApi'
import { reportApi } from '../api/reportApi'
import { dashboardApi } from '../api/dashboardApi'
import type { MissionRequest, SimulationRunResponse } from '../types/api'

export const keys = {
  missions: (search = '') => ['missions', search] as const,
  mission: (id: number) => ['mission', id] as const,
  runs: (missionId: number) => ['runs', missionId] as const,
  run: (runId: number) => ['run', runId] as const,
  recent: ['runs', 'recent'] as const,
  telemetry: (runId: number) => ['telemetry', runId] as const,
  latestReading: (runId: number) => ['telemetry', runId, 'latest'] as const,
  events: (runId: number) => ['events', runId] as const,
  latestEvents: (runId: number) => ['events', runId, 'latest'] as const,
  eventCount: (runId: number) => ['events', runId, 'count'] as const,
  recovery: (runId: number) => ['recovery', runId] as const,
  report: (runId: number) => ['report', runId] as const,
  dashboard: ['dashboard'] as const,
  analytics: ['analytics'] as const,
}

/* ---------- missions ---------- */
export const useMissions = (search = '') =>
  useQuery({ queryKey: keys.missions(search), queryFn: ({ signal }) => missionApi.list(search, signal) })

export const useMission = (id: number | null) =>
  useQuery({
    queryKey: keys.mission(id ?? -1),
    queryFn: ({ signal }) => missionApi.get(id as number, signal),
    enabled: id !== null,
  })

export function useCreateMission() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: MissionRequest) => missionApi.create(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['missions'] })
      void qc.invalidateQueries({ queryKey: keys.dashboard })
    },
  })
}

export function useUpdateMission(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: MissionRequest) => missionApi.update(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['missions'] })
      void qc.invalidateQueries({ queryKey: keys.mission(id) })
    },
  })
}

export function useDeleteMission() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => missionApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['missions'] })
      void qc.invalidateQueries({ queryKey: keys.dashboard })
    },
  })
}

/* ---------- runs ---------- */
/** Poll every 1.5 s while any run is executing, so async runs show progress and finish without a manual refresh. */
const POLL_MS = 1500
const isActive = (s: string) => s === 'RUNNING' || s === 'PAUSED'

/** True while the run is RUNNING/PAUSED according to any run list or run query already in the cache. */
function runIsActive(qc: ReturnType<typeof useQueryClient>, runId: number | null): boolean {
  if (runId === null) return false
  for (const [, data] of qc.getQueriesData<SimulationRunResponse[]>({ queryKey: ['runs'] })) {
    const hit = Array.isArray(data) ? data.find((r) => r.id === runId) : undefined
    if (hit) return isActive(hit.status)
  }
  const single = qc.getQueryData<SimulationRunResponse>(keys.run(runId))
  return single ? isActive(single.status) : false
}

export const useRuns = (missionId: number | null) => {
  const qc = useQueryClient()
  return useQuery({
    queryKey: keys.runs(missionId ?? -1),
    queryFn: async ({ signal }) => {
      const prev = qc.getQueryData<SimulationRunResponse[]>(keys.runs(missionId ?? -1))
      const next = await simulationApi.runsByMission(missionId as number, signal)
      // A run that just left RUNNING/PAUSED: refresh telemetry, events, recovery, report and dashboards once.
      for (const r of next) {
        const before = prev?.find((p) => p.id === r.id)
        if (before && isActive(before.status) && !isActive(r.status)) invalidateRunData(qc, r.id)
      }
      return next
    },
    enabled: missionId !== null,
    refetchInterval: (q) => (q.state.data?.some((r) => isActive(r.status)) ? POLL_MS : false),
  })
}

export const useRun = (runId: number | null) =>
  useQuery({
    queryKey: keys.run(runId ?? -1),
    queryFn: ({ signal }) => simulationApi.getRun(runId as number, signal),
    enabled: runId !== null,
    refetchInterval: (q) => (q.state.data && isActive(q.state.data.status) ? POLL_MS : false),
  })

export const useRecentRuns = () =>
  useQuery({ queryKey: keys.recent, queryFn: ({ signal }) => simulationApi.recentRuns(signal) })

export function useCreateRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (missionId: number) => simulationApi.createRun(missionId),
    onSuccess: (run) => {
      void qc.invalidateQueries({ queryKey: keys.runs(run.missionId) })
      void qc.invalidateQueries({ queryKey: keys.recent })
    },
  })
}

function invalidateRunData(qc: ReturnType<typeof useQueryClient>, runId: number, missionId?: number) {
  void qc.invalidateQueries({ queryKey: keys.run(runId) })
  void qc.invalidateQueries({ queryKey: keys.recent })
  void qc.invalidateQueries({ queryKey: keys.dashboard })
  void qc.invalidateQueries({ queryKey: keys.analytics })
  void qc.invalidateQueries({ queryKey: ['telemetry', runId] })
  void qc.invalidateQueries({ queryKey: ['events', runId] })
  void qc.invalidateQueries({ queryKey: keys.recovery(runId) })
  void qc.invalidateQueries({ queryKey: keys.report(runId) })
  if (missionId !== undefined) void qc.invalidateQueries({ queryKey: keys.runs(missionId) })
}

export function useStartRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (runId: number) => simulationApi.start(runId),
    // Also refresh on error: a timed-out request may still have completed on the server.
    onSettled: (data, _err, runId) => invalidateRunData(qc, runId, data?.missionId),
  })
}

export function usePauseRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (runId: number) => simulationApi.pause(runId),
    onSettled: (data, _err, runId) => invalidateRunData(qc, runId, data?.missionId),
  })
}

export function useResumeRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (runId: number) => simulationApi.resume(runId),
    onSettled: (data, _err, runId) => invalidateRunData(qc, runId, data?.missionId),
  })
}

export function useCancelRun() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (runId: number) => simulationApi.cancel(runId),
    onSettled: (data, _err, runId) => invalidateRunData(qc, runId, data?.missionId),
  })
}

/* ---------- run data ---------- */
export const useTelemetry = (runId: number | null) => {
  const qc = useQueryClient()
  return useQuery({
    queryKey: keys.telemetry(runId ?? -1),
    queryFn: ({ signal }) => telemetryApi.list(runId as number, signal),
    enabled: runId !== null,
    // Live while the run executes, so data written by the background worker appears without a manual refresh.
    refetchInterval: () => (runIsActive(qc, runId) ? POLL_MS : false),
  })
}

export const useLatestReading = (runId: number | null) =>
  useQuery({
    queryKey: keys.latestReading(runId ?? -1),
    queryFn: ({ signal }) => telemetryApi.latestReading(runId as number, signal),
    enabled: runId !== null,
  })

export const useEvents = (runId: number | null) => {
  const qc = useQueryClient()
  return useQuery({
    queryKey: keys.events(runId ?? -1),
    queryFn: ({ signal }) => eventApi.list(runId as number, signal),
    enabled: runId !== null,
    // Live while the run executes, so data written by the background worker appears without a manual refresh.
    refetchInterval: () => (runIsActive(qc, runId) ? POLL_MS : false),
  })
}

export const useLatestEvents = (runId: number | null) =>
  useQuery({
    queryKey: keys.latestEvents(runId ?? -1),
    queryFn: ({ signal }) => eventApi.latest(runId as number, signal),
    enabled: runId !== null,
  })

export const useEventCount = (runId: number | null) =>
  useQuery({
    queryKey: keys.eventCount(runId ?? -1),
    queryFn: ({ signal }) => eventApi.count(runId as number, signal),
    enabled: runId !== null,
  })

export const useRecovery = (runId: number | null) => {
  const qc = useQueryClient()
  return useQuery({
    queryKey: keys.recovery(runId ?? -1),
    queryFn: ({ signal }) => recoveryApi.get(runId as number, signal),
    enabled: runId !== null,
    // Live while the run executes, so data written by the background worker appears without a manual refresh.
    refetchInterval: () => (runIsActive(qc, runId) ? POLL_MS : false),
  })
}

export const useReport = (runId: number | null) => {
  const qc = useQueryClient()
  return useQuery({
    queryKey: keys.report(runId ?? -1),
    queryFn: ({ signal }) => reportApi.get(runId as number, signal),
    enabled: runId !== null,
    // Live while the run executes, so data written by the background worker appears without a manual refresh.
    refetchInterval: () => (runIsActive(qc, runId) ? POLL_MS : false),
  })
}

/* ---------- dashboard ---------- */
export const useDashboard = () =>
  useQuery({ queryKey: keys.dashboard, queryFn: ({ signal }) => dashboardApi.dashboard(signal) })

export const useAnalytics = () =>
  useQuery({ queryKey: keys.analytics, queryFn: ({ signal }) => dashboardApi.analytics(signal) })