import { useMemo, useState } from 'react'
import { Download, RefreshCw } from 'lucide-react'
import { useTelemetry } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { useSettings } from '../hooks/useSettings'
import { RunPicker } from '../components/simulation/RunPicker'
import { NoRunSelected } from '../components/simulation/NoRunSelected'
import { SeriesChart, type ChartMarker } from '../components/charts/SeriesChart'
import { TelemetryTable } from '../components/telemetry/TelemetryTable'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { TextField } from '../components/ui/Field'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { downloadCsv, toCsv } from '../utils/csv'
import { fmtNum } from '../utils/format'
import { SERIES, availableSeries, downsample, filterByTime, hasPosition, type SeriesKey } from '../utils/telemetry'

function parseOpt(v: string): number | null {
  if (v.trim() === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

export default function TelemetryPage() {
  const { missionId, runId } = useRunSelection()
  const { settings } = useSettings()
  const telemetry = useTelemetry(runId)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [hidden, setHidden] = useState<Set<SeriesKey>>(new Set())

  const all = useMemo(() => telemetry.data ?? [], [telemetry.data])
  const rows = useMemo(() => filterByTime(all, parseOpt(from), parseOpt(to)), [all, from, to])
  const chartRows = useMemo(() => downsample(rows, settings.maxChartPoints), [rows, settings.maxChartPoints])
  const available = useMemo(() => availableSeries(all), [all])
  const position = useMemo(() => hasPosition(all), [all])
  const markers = useMemo<ChartMarker[]>(() => {
    const p = all.find((r) => r.flightState === 'PARACHUTE_DEPLOYED')
    return p ? [{ x: p.elapsedTimeSeconds, label: 'Parachute deployed' }] : []
  }, [all])

  const toggle = (k: SeriesKey) =>
    setHidden((prev) => {
      const next = new Set(prev)
      if (next.has(k)) next.delete(k)
      else next.add(k)
      return next
    })

  const exportCsv = () => {
    const csv = toCsv(
      ['elapsedTimeSeconds', 'altitudeMeters', 'verticalVelocityMps', 'temperatureCelsius', 'batteryPercent', 'latitude', 'longitude', 'flightState', 'recordedAt'],
      rows.map((r) => [r.elapsedTimeSeconds, r.altitudeMeters, r.verticalVelocityMps, r.temperatureCelsius, r.batteryPercent, r.latitude, r.longitude, r.flightState, r.recordedAt]),
    )
    downloadCsv(`camsat-run-${runId}-telemetry.csv`, csv)
  }

  return (
    <>
      <PageHeader
        title="Telemetry explorer"
        subtitle="Recorded simulation output for one run. This is stored data, not a live stream."
        actions={
          runId !== null && (
            <>
              <Button onClick={() => void telemetry.refetch()} loading={telemetry.isFetching} icon={<RefreshCw className="h-4 w-4" aria-hidden />}>Refresh</Button>
              <Button variant="primary" disabled={rows.length === 0} onClick={exportCsv} icon={<Download className="h-4 w-4" aria-hidden />}>Export CSV ({rows.length})</Button>
            </>
          )
        }
      />
      <Card className="mb-4"><RunPicker /></Card>

      {runId === null && <Card><NoRunSelected missionSelected={missionId !== null} /></Card>}
      {runId !== null && telemetry.isLoading && <LoadingState label="Loading telemetry…" />}
      {runId !== null && telemetry.isError && <Card><ErrorState error={telemetry.error} onRetry={() => void telemetry.refetch()} title="Could not load telemetry" /></Card>}
      {runId !== null && telemetry.data && all.length === 0 && (
        <Card><EmptyState title="No telemetry recorded for this run" description="Telemetry is written while a simulation executes. Runs that were never started, or that failed early, have none." /></Card>
      )}

      {all.length > 0 && (
        <>
          <Card className="mb-4" title="Filters">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <TextField label="From" unit="s" type="number" inputMode="decimal" value={from} onChange={(e) => setFrom(e.target.value)} placeholder={fmtNum(all[0].elapsedTimeSeconds, 0)} />
              <TextField label="To" unit="s" type="number" inputMode="decimal" value={to} onChange={(e) => setTo(e.target.value)} placeholder={fmtNum(all[all.length - 1].elapsedTimeSeconds, 0)} />
              <fieldset className="sm:col-span-2">
                <legend className="mb-1 text-sm font-medium">Charts to show</legend>
                <div className="flex flex-wrap gap-x-4 gap-y-1">
                  {SERIES.filter((s) => available.includes(s.key)).map((s) => (
                    <label key={s.key} className="flex items-center gap-2 text-sm">
                      <input type="checkbox" checked={!hidden.has(s.key)} onChange={() => toggle(s.key)} className="accent-cyan-400" />
                      {s.label}
                    </label>
                  ))}
                </div>
              </fieldset>
            </div>
            <p className="mt-3 text-xs text-muted">
              {all.length} recorded readings, {rows.length} in range; charts show {chartRows.length} points (limit {settings.maxChartPoints}, change in Settings).
            </p>
          </Card>

          <div className="mb-4 space-y-2">
            {!position && <Notice>Latitude and longitude are not recorded for this run, so no ground track is available.</Notice>}
            <Notice>The backend does not provide acceleration or pressure; only the sensors listed above exist in the data.</Notice>
          </div>

          {rows.length === 0 ? (
            <Card><EmptyState title="No readings in this time range" description="Widen or clear the From / To filters." /></Card>
          ) : (
            <div className="mb-4 grid gap-4 lg:grid-cols-2">
              {SERIES.filter((s) => available.includes(s.key) && !hidden.has(s.key)).map((s) => (
                <Card key={s.key}><SeriesChart info={s} data={chartRows} markers={markers.filter((m) => rows.length > 0 && m.x >= rows[0].elapsedTimeSeconds && m.x <= rows[rows.length - 1].elapsedTimeSeconds)} /></Card>
              ))}
            </div>
          )}

          <Card title="Readings">
            <TelemetryTable rows={rows} showPosition={position} />
          </Card>
        </>
      )}
    </>
  )
}
