import { Download, Printer } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAnalytics, useReport, useRuns } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { RunPicker } from '../components/simulation/RunPicker'
import { NoRunSelected } from '../components/simulation/NoRunSelected'
import { Button } from '../components/ui/Button'
import { Card, PageHeader, Stat } from '../components/ui/Card'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { EventBadge, FlightStateBadge, StatusBadge } from '../components/ui/StatusBadge'
import { downloadCsv, toCsv } from '../utils/csv'
import { fmtDateTime, fmtDuration, fmtInt, fmtNum, wallClockDuration } from '../utils/format'
import type { MissionReportResponse } from '../types/api'

function reportCsv(r: MissionReportResponse): string {
  const summary: [string, string | number | null][] = [
    ['runId', r.runId], ['missionId', r.missionId], ['missionName', r.missionName], ['status', r.status],
    ['startedAt', r.startedAt], ['endedAt', r.endedAt], ['landingTimeSeconds', r.landingTimeSeconds],
    ['landingDriftM', r.landingDriftM], ['finalResult', r.finalResult], ['releaseAltitudeM', r.releaseAltitudeM],
    ['payloadMassKg', r.payloadMassKg], ['parachuteDiameterM', r.parachuteDiameterM], ['windSpeedMps', r.windSpeedMps],
    ['telemetryReadingCount', r.telemetryReadingCount], ['minimumBatteryPercent', r.minimumBatteryPercent],
    ['maximumAltitudeMeters', r.maximumAltitudeMeters], ['maximumDescentSpeedMps', r.maximumDescentSpeedMps],
    ['finalFlightState', r.finalFlightState],
  ]
  const a = toCsv(['field', 'value'], summary)
  const b = toCsv(['eventType', 'elapsedTimeSeconds', 'createdAt', 'message'], r.events.map((e) => [e.eventType, e.elapsedTimeSeconds, e.createdAt, e.message]))
  return `${a}\r\n${b}`
}

export default function ReportsPage() {
  const { missionId, runId } = useRunSelection()
  const report = useReport(runId)
  const runs = useRuns(missionId)
  const analytics = useAnalytics()
  const r = report.data

  const comparable = (runs.data ?? []).filter((x) => x.status === 'COMPLETED' && x.landingTimeSeconds !== null).map((x) => ({
    run: `#${x.id}`,
    landingTimeSeconds: x.landingTimeSeconds,
    landingDriftM: x.landingDriftM,
  }))

  return (
    <>
      <PageHeader
        title="Reports and analytics"
        subtitle="Run summary assembled in the browser from the backend report endpoint. It is not a separately generated backend document."
        actions={
          r && (
            <>
              <Button onClick={() => downloadCsv(`camsat-run-${r.runId}-report.csv`, reportCsv(r))} icon={<Download className="h-4 w-4" aria-hidden />}>Export CSV</Button>
              <Button variant="primary" onClick={() => window.print()} icon={<Printer className="h-4 w-4" aria-hidden />}>Print report</Button>
            </>
          )
        }
      />
      <Card className="mb-4"><RunPicker /></Card>

      {runId === null && <Card><NoRunSelected missionSelected={missionId !== null} /></Card>}
      {runId !== null && report.isLoading && <LoadingState label="Loading report…" />}
      {runId !== null && report.isError && <Card><ErrorState error={report.error} onRetry={() => void report.refetch()} title="Could not load the report" /></Card>}

      {r && (
        <div className="print-area space-y-4">
          <Card title={`${r.missionName} · run #${r.runId}`} actions={<StatusBadge status={r.status} />}>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Stat label="Landing time (simulated)" value={fmtDuration(r.landingTimeSeconds)} />
              <Stat label="Landing drift" value={fmtNum(r.landingDriftM, 1, 'm')} />
              <Stat label="Max altitude" value={fmtNum(r.maximumAltitudeMeters, 1, 'm')} />
              <Stat label="Max descent speed" value={fmtNum(r.maximumDescentSpeedMps, 2, 'm/s')} />
              <Stat label="Min battery" value={fmtNum(r.minimumBatteryPercent, 1, '%')} />
              <Stat label="Telemetry readings" value={fmtInt(r.telemetryReadingCount)} />
              <Stat label="Final flight state" value={r.finalFlightState ? <FlightStateBadge state={r.finalFlightState} /> : '—'} />
              <Stat label="Wall-clock duration" value={wallClockDuration(r.startedAt, r.endedAt)} />
            </div>
            {r.finalResult && <div className="mt-3"><Notice tone={r.status === 'FAILED' ? 'crit' : 'info'}>Result: {r.finalResult}</Notice></div>}
            <p className="mt-3 text-xs text-muted">Started {fmtDateTime(r.startedAt)} · ended {fmtDateTime(r.endedAt)}. A dash means the backend returned no value; it does not mean zero.</p>
          </Card>

          <Card title="Mission parameters">
            <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm md:grid-cols-4">
              <div><dt className="text-xs text-muted">Release altitude</dt><dd className="num">{fmtNum(r.releaseAltitudeM, 0, 'm')}</dd></div>
              <div><dt className="text-xs text-muted">Payload mass</dt><dd className="num">{fmtNum(r.payloadMassKg, 2, 'kg')}</dd></div>
              <div><dt className="text-xs text-muted">Parachute diameter</dt><dd className="num">{fmtNum(r.parachuteDiameterM, 2, 'm')}</dd></div>
              <div><dt className="text-xs text-muted">Wind speed</dt><dd className="num">{fmtNum(r.windSpeedMps, 1, 'm/s')}</dd></div>
            </dl>
          </Card>

          <Card title={`Events (${r.events.length})`}>
            {r.events.length === 0 ? <EmptyState title="No events in this report" /> : (
              <ol className="divide-y divide-line text-sm">
                {r.events.map((e) => (
                  <li key={e.id} className="flex flex-wrap items-center gap-3 py-2">
                    <EventBadge type={e.eventType} />
                    <span className="num text-xs text-muted">t+{fmtNum(e.elapsedTimeSeconds, 1)} s</span>
                    <span className="flex-1">{e.message}</span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      )}

      {missionId !== null && (
        <Card className="mt-4" title="Run comparison for this mission" description="Completed runs only. Values are the backend's landing results.">
          {runs.isLoading && <LoadingState />}
          {runs.isError && <ErrorState error={runs.error} onRetry={() => void runs.refetch()} title="Could not load runs" />}
          {runs.data && comparable.length < 2 && <EmptyState title="Not enough completed runs to compare" description="At least two completed runs of the same mission are needed." />}
          {comparable.length >= 2 && (
            <div className="grid gap-6 md:grid-cols-2">
              {([['landingTimeSeconds', 'Landing time (s)', '#22d3ee'], ['landingDriftM', 'Landing drift (m)', '#a78bfa']] as const).map(([key, title, color]) => (
                <figure key={key} className="m-0" aria-label={title}>
                  <figcaption className="mb-1 text-sm font-medium">{title}</figcaption>
                  <div style={{ height: 220 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={comparable} margin={{ top: 8, right: 8, bottom: 4, left: 0 }}>
                        <CartesianGrid stroke="#22304a" strokeDasharray="3 3" />
                        <XAxis dataKey="run" tick={{ fill: '#8fa0bc', fontSize: 11 }} stroke="#22304a" />
                        <YAxis tick={{ fill: '#8fa0bc', fontSize: 11 }} stroke="#22304a" width={48} />
                        <Tooltip contentStyle={{ background: '#101a2e', border: '1px solid #22304a', borderRadius: 6, fontSize: 12 }} formatter={(v) => [fmtNum(Number(v), 2), title]} />
                        <Bar dataKey={key} fill={color} isAnimationActive={false} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </figure>
              ))}
            </div>
          )}
        </Card>
      )}

      <Card className="mt-4 print:hidden" title="Backend analytics" description="Aggregates reported by the analytics endpoint.">
        {analytics.isLoading && <LoadingState />}
        {analytics.isError && <ErrorState error={analytics.error} onRetry={() => void analytics.refetch()} title="Analytics unavailable" />}
        {analytics.data && (analytics.data.totalRuns === 0 ? (
          <EmptyState title="No runs to analyse yet" />
        ) : (
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <Stat label="Runs" value={fmtInt(analytics.data.totalRuns)} />
            <Stat label="Success rate" value={`${fmtNum(analytics.data.successRatePercent, 1)}%`} hint={`${analytics.data.completedRuns} completed · ${analytics.data.failedRuns} failed`} />
            <Stat label="Avg landing time" value={fmtDuration(analytics.data.averageLandingTimeSeconds)} />
            <Stat label="Avg landing drift" value={fmtNum(analytics.data.averageLandingDriftMeters, 1, 'm')} />
            <Stat label="Avg max descent speed" value={fmtNum(analytics.data.averageMaximumDescentSpeedMps, 2, 'm/s')} />
          </div>
        ))}
      </Card>
    </>
  )
}
