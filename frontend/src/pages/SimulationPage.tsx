import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Ban, Pause, Play, Rocket } from 'lucide-react'
import { useCancelRun, useCreateRun, useMission, useMissions, usePauseRun, useRecentRuns, useResumeRun, useRuns, useStartRun } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { toApiError } from '../api/errors'
import { env } from '../config/env'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { SelectField } from '../components/ui/Field'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { StatusBadge } from '../components/ui/StatusBadge'
import { useToast } from '../components/ui/Toast'
import { fmtDateTime, fmtDuration, fmtNum, wallClockDuration } from '../utils/format'
import type { SimulationRunResponse } from '../types/api'

export default function SimulationPage() {
  const { missionId, setMission } = useRunSelection()
  const toast = useToast()
  const missions = useMissions()
  const mission = useMission(missionId)
  const runs = useRuns(missionId)
  const recent = useRecentRuns()
  const createRun = useCreateRun()
  const startRun = useStartRun()
  const cancelRun = useCancelRun()
  const pauseRun = usePauseRun()
  const resumeRun = useResumeRun()

  const [activeRunId, setActiveRunId] = useState<number | null>(null)
  const [inFlight, setInFlight] = useState<number | null>(null)
  const [lastError, setLastError] = useState<string | null>(null)
  // Synchronous guard: state updates are async, so two quick clicks could both pass a state check.
  const lock = useRef(false)

  const busy = inFlight !== null || createRun.isPending

  async function runStart(runId: number) {
    if (lock.current) return
    lock.current = true
    setLastError(null)
    setActiveRunId(runId)
    setInFlight(runId)
    try {
      const result = await startRun.mutateAsync(runId)
      if (result.status === 'COMPLETED') toast.success(`Run #${runId} completed.`)
      else if (result.status === 'FAILED') toast.error(`Run #${runId} failed: ${result.finalResult ?? 'no reason reported'}`)
      else if (result.status === 'RUNNING') toast.info(`Run #${runId} started. Progress updates below.`)
      else toast.info(`Run #${runId} finished with status ${result.status}.`)
    } catch (e) {
      const err = toApiError(e)
      const hint =
        err.kind === 'timeout' || err.kind === 'network'
          ? ' The backend may still have processed the run; its current state is being re-read below.'
          : ''
      setLastError(`${err.message}${hint}`)
      toast.error(err.message)
    } finally {
      lock.current = false
      setInFlight(null)
    }
  }

  async function createAndStart() {
    if (missionId === null || lock.current) return
    lock.current = true
    setLastError(null)
    let runId: number
    try {
      const run = await createRun.mutateAsync(missionId)
      runId = run.id
      setActiveRunId(run.id)
    } catch (e) {
      lock.current = false
      const err = toApiError(e)
      setLastError(err.message)
      toast.error(err.message)
      return
    }
    // Release and re-acquire synchronously (no await in between), so a second click cannot slip in.
    lock.current = false
    await runStart(runId)
  }

  async function createOnly() {
    if (missionId === null || busy) return
    try {
      const run = await createRun.mutateAsync(missionId)
      setActiveRunId(run.id)
      toast.success(`Created run #${run.id}. It has not been started.`)
    } catch (e) {
      toast.error(toApiError(e).message)
    }
  }

  async function cancel(runId: number) {
    try {
      await cancelRun.mutateAsync(runId)
      toast.success(`Run #${runId} cancelled.`)
    } catch (e) {
      toast.error(toApiError(e).message)
    }
  }

  async function pauseResume(runId: number, pause: boolean) {
    try {
      await (pause ? pauseRun : resumeRun).mutateAsync(runId)
      toast.success(`Run #${runId} ${pause ? 'paused' : 'resumed'}.`)
    } catch (e) {
      toast.error(toApiError(e).message)
    }
  }

  const activeRun: SimulationRunResponse | undefined = runs.data?.find((r) => r.id === activeRunId) ?? runs.data?.[0]

  return (
    <>
      <PageHeader
        title="Simulation control"
        subtitle="Create a run for a mission and start it. The backend simulates in the background; this page refreshes the run while it is running, and it can be paused, resumed or cancelled."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="1 · Choose a mission" className="lg:col-span-1">
          <SelectField label="Mission" value={missionId ?? ''} disabled={missions.isLoading || busy} onChange={(e) => { setActiveRunId(null); setMission(e.target.value ? Number(e.target.value) : null) }}>
            <option value="">{missions.isLoading ? 'Loading…' : 'Select a mission'}</option>
            {missions.data?.map((m) => (
              <option key={m.id} value={m.id}>{m.name} (#{m.id})</option>
            ))}
          </SelectField>
          {missions.isError && <div className="mt-3"><ErrorState error={missions.error} onRetry={() => void missions.refetch()} title="Could not load missions" /></div>}
          {missions.data && missions.data.length === 0 && (
            <EmptyState title="No missions" description="Create a mission before starting a simulation." action={<Link to="/missions/new" className="text-sm text-accent hover:underline">New mission</Link>} />
          )}
          {mission.data && (
            <dl className="mt-4 space-y-1.5 text-sm">
              {([
                ['Payload mass', fmtNum(mission.data.payloadMassKg, 2, 'kg')],
                ['Release altitude', fmtNum(mission.data.releaseAltitudeM, 0, 'm')],
                ['Parachute diameter', fmtNum(mission.data.parachuteDiameterM, 2, 'm')],
                ['Drag coefficient', fmtNum(mission.data.dragCoefficient, 2)],
                ['Wind', `${fmtNum(mission.data.windSpeedMps, 1, 'm/s')} @ ${mission.data.windDirectionDeg}°`],
                ['Initial battery', `${mission.data.initialBatteryPercent}%`],
              ] as [string, string][]).map(([k, v]) => (
                <div key={k} className="flex justify-between gap-2"><dt className="text-muted">{k}</dt><dd className="num">{v}</dd></div>
              ))}
            </dl>
          )}
          <div className="mt-4 flex flex-col gap-2">
            <Button variant="primary" disabled={missionId === null || busy} loading={busy} onClick={() => void createAndStart()} icon={<Rocket className="h-4 w-4" aria-hidden />}>
              {busy ? 'Simulating…' : 'Create run and start'}
            </Button>
            <Button disabled={missionId === null || busy} onClick={() => void createOnly()}>
              Create run without starting
            </Button>
          </div>
          <p className="mt-3 text-xs text-muted">Simulation parameters come from the mission configuration. The backend start endpoint accepts no extra parameters.</p>
        </Card>

        <div className="space-y-4 lg:col-span-2">
          <Card title="2 · Run status">
            {inFlight !== null && (
              <div className="mb-3 space-y-2">
                <Notice tone="info">
                  Run #{inFlight} is being simulated by the backend. Waiting for the backend to accept the start request (up to {Math.round(env.simulationStartTimeoutMs / 1000)} s).
                </Notice>
                <progress aria-label="Simulation in progress" className="h-1.5 w-full accent-cyan-400" />
              </div>
            )}
            {lastError && <div className="mb-3"><Notice tone="crit">{lastError}</Notice></div>}
            {!missionId && <EmptyState title="No mission selected" description="Select a mission to see and start its runs." />}
            {missionId !== null && runs.isLoading && <LoadingState label="Loading runs…" />}
            {runs.isError && <ErrorState error={runs.error} onRetry={() => void runs.refetch()} title="Could not load runs" />}
            {missionId !== null && runs.data && runs.data.length === 0 && <EmptyState title="No runs yet" description="Use “Create run and start” to run this mission." />}
            {activeRun && (
              <div className="rounded-md border border-line bg-ink-950 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">Run #{activeRun.id}</span>
                    <StatusBadge status={activeRun.status} />
                  </div>
                  <div className="flex flex-wrap gap-2 print:hidden">
                    {activeRun.status === 'CREATED' && (
                      <Button variant="primary" disabled={busy} onClick={() => void runStart(activeRun.id)} icon={<Play className="h-4 w-4" aria-hidden />}>Start</Button>
                    )}
                    <Button disabled={activeRun.status !== 'RUNNING' || pauseRun.isPending} title={activeRun.status === 'RUNNING' ? 'Pause this run' : 'Only a running run can be paused'} onClick={() => void pauseResume(activeRun.id, true)} icon={<Pause className="h-4 w-4" aria-hidden />}>Pause</Button>
                    <Button disabled={activeRun.status !== 'PAUSED' || resumeRun.isPending} title={activeRun.status === 'PAUSED' ? 'Resume this run' : 'Only a paused run can be resumed'} onClick={() => void pauseResume(activeRun.id, false)} icon={<Play className="h-4 w-4" aria-hidden />}>Resume</Button>
                    <Button
                      disabled={(activeRun.status !== 'CREATED' && activeRun.status !== 'RUNNING' && activeRun.status !== 'PAUSED') || inFlight === activeRun.id}
                      title={inFlight === activeRun.id ? 'Wait until the start request has been accepted.' : 'Cancel this run'}
                      loading={cancelRun.isPending}
                      onClick={() => void cancel(activeRun.id)}
                      icon={<Ban className="h-4 w-4" aria-hidden />}
                    >
                      Cancel
                    </Button>
                  </div>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4">
                  <div><dt className="text-xs text-muted">Started</dt><dd className="text-xs">{fmtDateTime(activeRun.startedAt)}</dd></div>
                  <div><dt className="text-xs text-muted">Ended</dt><dd className="text-xs">{fmtDateTime(activeRun.endedAt)}</dd></div>
                  <div><dt className="text-xs text-muted">Wall-clock duration</dt><dd className="num">{wallClockDuration(activeRun.startedAt, activeRun.endedAt)}</dd></div>
                  <div><dt className="text-xs text-muted">Landing time (simulated)</dt><dd className="num">{fmtDuration(activeRun.landingTimeSeconds)}</dd></div>
                  <div><dt className="text-xs text-muted">Landing drift</dt><dd className="num">{fmtNum(activeRun.landingDriftM, 1, 'm')}</dd></div>
                </dl>
                {(activeRun.status === 'RUNNING' || activeRun.status === 'PAUSED') && (
                  <div className="mt-3 space-y-1">
                    <progress aria-label="Simulation progress" className="h-1.5 w-full accent-cyan-400" max={100} value={activeRun.progressPercent ?? undefined} />
                    <p className="text-xs text-muted">
                      {activeRun.status === 'PAUSED' ? 'Paused' : 'Simulating'}
                      {activeRun.elapsedSimTimeSeconds != null ? ` · simulated time ${fmtDuration(activeRun.elapsedSimTimeSeconds)}` : ''}
                      {activeRun.progressPercent != null ? ` · about ${Math.round(activeRun.progressPercent)} % of the descent` : ''}
                    </p>
                  </div>
                )}
                {activeRun.finalResult && (
                  <div className="mt-3">
                    <Notice tone={activeRun.status === 'FAILED' ? 'crit' : 'info'}>
                      <strong className="font-medium">{activeRun.status === 'FAILED' ? 'Failure reason: ' : 'Result: '}</strong>
                      {activeRun.finalResult}
                    </Notice>
                  </div>
                )}
                {(activeRun.status === 'COMPLETED' || activeRun.status === 'FAILED' || activeRun.status === 'CANCELLED') && (
                  <div className="mt-3 flex flex-wrap gap-3 text-sm">
                    <Link className="text-accent hover:underline" to={`/telemetry?mission=${activeRun.missionId}&run=${activeRun.id}`}>Telemetry</Link>
                    <Link className="text-accent hover:underline" to={`/visualization?mission=${activeRun.missionId}&run=${activeRun.id}`}>3D view</Link>
                    <Link className="text-accent hover:underline" to={`/events?mission=${activeRun.missionId}&run=${activeRun.id}`}>Events</Link>
                    <Link className="text-accent hover:underline" to={`/recovery?mission=${activeRun.missionId}&run=${activeRun.id}`}>Recovery</Link>
                    <Link className="text-accent hover:underline" to={`/reports?mission=${activeRun.missionId}&run=${activeRun.id}`}>Report</Link>
                  </div>
                )}
              </div>
            )}
          </Card>

          {runs.data && runs.data.length > 0 && (
            <Card title="Runs of this mission">
              <ul className="divide-y divide-line text-sm">
                {runs.data.map((r) => (
                  <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <button type="button" className="flex items-center gap-2 text-left hover:text-accent" onClick={() => setActiveRunId(r.id)}>
                      <span>Run #{r.id}</span>
                      <StatusBadge status={r.status} />
                    </button>
                    <span className="text-xs text-muted">{fmtDateTime(r.startedAt ?? null)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card title="Recent runs (all missions)">
            {recent.isLoading && <LoadingState />}
            {recent.isError && <ErrorState error={recent.error} onRetry={() => void recent.refetch()} title="Could not load recent runs" />}
            {recent.data && recent.data.length === 0 && <p className="text-sm text-muted">No runs have been created yet.</p>}
            {recent.data && recent.data.length > 0 && (
              <ul className="divide-y divide-line text-sm">
                {recent.data.map((r) => (
                  <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <Link className="flex items-center gap-2 hover:text-accent" to={`/simulation?mission=${r.missionId}`} onClick={() => setActiveRunId(r.id)}>
                      <span>Run #{r.id}</span>
                      <span className="text-xs text-muted">mission #{r.missionId}</span>
                      <StatusBadge status={r.status} />
                    </Link>
                    <span className="num text-xs text-muted">{fmtDuration(r.landingTimeSeconds)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}
