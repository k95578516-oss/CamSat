import { Suspense, lazy, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Pause, Play, RotateCcw } from 'lucide-react'
import { useTelemetry } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { usePlayback } from '../hooks/usePlayback'
import { RunPicker } from '../components/simulation/RunPicker'
import { NoRunSelected } from '../components/simulation/NoRunSelected'
import { SeriesChart } from '../components/charts/SeriesChart'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { FlightStateBadge } from '../components/ui/StatusBadge'
import { fmtNum } from '../utils/format'
import { SERIES, downsample, sampleAt } from '../utils/telemetry'
import type { CameraView } from '../components/3d/CanSatScene'

// Three.js is large: load it only when this page is opened.
const CanSatScene = lazy(() => import('../components/3d/CanSatScene'))

export default function VisualizationPage() {
  const { missionId, runId } = useRunSelection()
  const telemetry = useTelemetry(runId)
  const [view, setView] = useState<CameraView>('orbit')
  const points = useMemo(() => telemetry.data ?? [], [telemetry.data])
  const start = points.length ? points[0].elapsedTimeSeconds : 0
  const end = points.length ? points[points.length - 1].elapsedTimeSeconds : 0
  const maxAltitude = useMemo(() => points.reduce((m, p) => Math.max(m, p.altitudeMeters), 0), [points])
  const { t, playing, speed, setSpeed, toggle, seek, restart } = usePlayback(start, end)
  const sample = useMemo(() => sampleAt(points, t), [points, t])
  const chartData = useMemo(() => downsample(points, 600), [points])

  return (
    <>
      <PageHeader title="3D flight view" subtitle="Replay of recorded altitude data. Playback is driven by stored telemetry, not by a live feed." />
      <Card className="mb-4"><RunPicker /></Card>

      {runId === null && <Card><NoRunSelected missionSelected={missionId !== null} /></Card>}
      {runId !== null && telemetry.isLoading && <LoadingState label="Loading telemetry…" />}
      {runId !== null && telemetry.isError && <Card><ErrorState error={telemetry.error} onRetry={() => void telemetry.refetch()} title="Could not load telemetry" /></Card>}
      {runId !== null && telemetry.data && points.length === 0 && (
        <Card><EmptyState title="No telemetry to replay" description="This run has no recorded readings." /></Card>
      )}

      {points.length > 0 && sample && (
        <div className="space-y-4">
          <Notice tone="warn">
            Altitude-only illustrative view. The CanSat model and the ground are schematic; only height (and the parachute state) come from recorded data. The backend does not record a horizontal position, so no drift or ground track is drawn.
          </Notice>

          <div className="relative overflow-hidden rounded-lg border border-line bg-ink-950" style={{ height: 460 }}>
            <ErrorBoundary inline label="The 3D view could not start (WebGL may be unavailable). Use the charts and table on the Telemetry page.">
              <Suspense fallback={<LoadingState label="Loading 3D scene…" />}>
                <CanSatScene sample={sample} maxAltitude={maxAltitude} view={view} />
              </Suspense>
            </ErrorBoundary>
            <dl className="pointer-events-none absolute left-3 top-3 grid grid-cols-2 gap-x-4 gap-y-1 rounded-md border border-line bg-ink-900/85 px-3 py-2 text-xs">
              <dt className="text-muted">Altitude</dt><dd className="num text-right">{fmtNum(sample.altitudeMeters, 1, 'm')}</dd>
              <dt className="text-muted">Velocity</dt><dd className="num text-right">{fmtNum(sample.verticalVelocityMps, 2, 'm/s')}</dd>
              <dt className="text-muted">Elapsed</dt><dd className="num text-right">{fmtNum(sample.elapsedTimeSeconds, 1, 's')}</dd>
              <dt className="text-muted">Battery</dt><dd className="num text-right">{fmtNum(sample.batteryPercent, 1, '%')}</dd>
              <dt className="text-muted">State</dt><dd className="text-right"><FlightStateBadge state={sample.flightState} /></dd>
              <dt className="text-muted">Parachute</dt><dd className="text-right">{sample.parachuteDeployed ? 'Deployed' : 'Stowed'}</dd>
            </dl>
          </div>

          <Card>
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="primary" onClick={toggle} icon={playing ? <Pause className="h-4 w-4" aria-hidden /> : <Play className="h-4 w-4" aria-hidden />}>
                {playing ? 'Pause' : 'Play'}
              </Button>
              <Button onClick={restart} icon={<RotateCcw className="h-4 w-4" aria-hidden />}>Restart</Button>
              <label className="flex items-center gap-2 text-sm">
                Speed
                <select value={speed} onChange={(e) => setSpeed(Number(e.target.value))} className="rounded-md border border-line bg-ink-950 px-2 py-1.5 text-sm">
                  {[1, 2, 5, 10].map((s) => <option key={s} value={s}>{s}×</option>)}
                </select>
              </label>
              <div className="ml-auto flex gap-1" role="group" aria-label="Camera view">
                {(['orbit', 'side', 'top'] as CameraView[]).map((v) => (
                  <button key={v} type="button" aria-pressed={view === v} onClick={() => setView(v)} className={`rounded-md border px-3 py-1.5 text-sm ${view === v ? 'border-accent bg-accent/10 text-accent' : 'border-line text-muted hover:text-fg'}`}>
                    {v === 'orbit' ? 'Orbit' : v === 'side' ? 'Side' : 'Top'}
                  </button>
                ))}
              </div>
            </div>
            <div className="mt-4">
              <label htmlFor="timeline" className="mb-1 block text-xs text-muted">Timeline · {fmtNum(t, 1)} s of {fmtNum(end, 1)} s</label>
              <input id="timeline" type="range" min={start} max={end} step={Math.max((end - start) / 1000, 0.01)} value={t} onChange={(e) => seek(Number(e.target.value))} className="w-full accent-cyan-400" />
            </div>
          </Card>

          <Card title="Altitude (recorded)" actions={<Link className="text-xs text-accent hover:underline" to={`/telemetry?mission=${missionId ?? ''}&run=${runId}`}>Open telemetry explorer</Link>}>
            <SeriesChart info={SERIES[0]} data={chartData} height={200} />
          </Card>
        </div>
      )}
    </>
  )
}
