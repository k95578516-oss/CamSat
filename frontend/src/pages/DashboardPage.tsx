import { Link } from 'react-router-dom'
import { Plus, Rocket, SatelliteDish } from 'lucide-react'
import { useAnalytics, useDashboard, useLatestEvents, useLatestReading } from '../hooks/queries'
import { Card, PageHeader, Stat } from '../components/ui/Card'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'
import { EventBadge, FlightStateBadge, StatusBadge } from '../components/ui/StatusBadge'
import { fmtDateTime, fmtDuration, fmtInt, fmtNum } from '../utils/format'
import { STATUS_LABEL } from '../utils/labels'
import type { SimulationStatus } from '../types/api'

export default function DashboardPage() {
  const dash = useDashboard()
  const analytics = useAnalytics()
  const latestRun = dash.data?.recentRuns[0]
  const latestRunId = latestRun?.runId ?? null
  const reading = useLatestReading(latestRunId)
  const events = useLatestEvents(latestRunId)

  return (
    <>
      <PageHeader
        title="Mission overview"
        subtitle="Figures below come from the backend. Telemetry is recorded simulation output, not a live feed."
        actions={
          <>
            <Link to="/missions/new" className="inline-flex items-center gap-2 rounded-md bg-accent px-3.5 py-2 text-sm font-medium text-ink-950 hover:bg-cyan-300">
              <Plus className="h-4 w-4" aria-hidden /> New mission
            </Link>
            <Link to="/missions" className="inline-flex items-center gap-2 rounded-md border border-line bg-ink-700 px-3.5 py-2 text-sm font-medium hover:bg-ink-800">
              <SatelliteDish className="h-4 w-4" aria-hidden /> Open missions
            </Link>
            <Link to="/simulation" className="inline-flex items-center gap-2 rounded-md border border-line bg-ink-700 px-3.5 py-2 text-sm font-medium hover:bg-ink-800">
              <Rocket className="h-4 w-4" aria-hidden /> Start simulation
            </Link>
          </>
        }
      />

      {dash.isLoading && <LoadingState label="Loading overview…" />}
      {dash.isError && <Card><ErrorState error={dash.error} onRetry={() => void dash.refetch()} title="Could not load the overview" /></Card>}

      {dash.data && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <Stat label="Missions" value={fmtInt(dash.data.totalMissions)} />
            <Stat label="Simulation runs" value={fmtInt(dash.data.totalSimulationRuns)} />
            <Stat label="Completed" value={fmtInt(dash.data.completedRuns)} />
            <Stat label="Failed" value={fmtInt(dash.data.failedRuns)} />
            <Stat label="Active" value={fmtInt(dash.data.activeRuns)} />
            <Stat label="Not started" value={fmtInt(dash.data.createdRuns)} />
            <Stat label="Telemetry readings" value={fmtInt(dash.data.totalTelemetryReadings)} />
            <Stat
              label="Success rate"
              value={analytics.data ? (analytics.data.totalRuns > 0 ? `${fmtNum(analytics.data.successRatePercent, 1)}%` : '—') : analytics.isError ? 'n/a' : '…'}
              hint={analytics.isError ? 'Analytics unavailable' : analytics.data && analytics.data.totalRuns === 0 ? 'No runs yet' : undefined}
            />
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            <Card title="Recent simulation runs" className="lg:col-span-2">
              {dash.data.recentRuns.length === 0 ? (
                <EmptyState
                  title="No simulation runs yet"
                  description="Create a mission, then start a simulation to see results here."
                  action={<Link to="/simulation" className="text-sm text-accent hover:underline">Open Simulation</Link>}
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[560px] text-left text-sm">
                    <thead className="text-xs text-muted">
                      <tr>
                        <th scope="col" className="py-1.5 pr-3">Run</th>
                        <th scope="col" className="py-1.5 pr-3">Mission</th>
                        <th scope="col" className="py-1.5 pr-3">Status</th>
                        <th scope="col" className="py-1.5 pr-3">Landing time</th>
                        <th scope="col" className="py-1.5 pr-3">Drift</th>
                        <th scope="col" className="py-1.5">Started</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dash.data.recentRuns.map((r) => (
                        <tr key={r.runId} className="border-t border-line">
                          <td className="py-2 pr-3">
                            <Link className="text-accent hover:underline" to={`/reports?mission=${r.missionId}&run=${r.runId}`}>#{r.runId}</Link>
                          </td>
                          <td className="py-2 pr-3">
                            <Link className="hover:underline" to={`/missions/${r.missionId}`}>{r.missionName}</Link>
                          </td>
                          <td className="py-2 pr-3"><StatusBadge status={r.status} /></td>
                          <td className="num py-2 pr-3">{fmtDuration(r.landingTimeSeconds)}</td>
                          <td className="num py-2 pr-3">{fmtNum(r.landingDriftM, 1, 'm')}</td>
                          <td className="py-2 text-xs text-muted">{fmtDateTime(r.startedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            <Card title="Runs by status">
              {Object.keys(dash.data.runsByStatus).length === 0 ? (
                <p className="text-sm text-muted">No runs to count yet.</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {Object.entries(dash.data.runsByStatus).map(([status, count]) => (
                    <li key={status} className="flex items-center justify-between">
                      <span>{STATUS_LABEL[status as SimulationStatus] ?? status}</span>
                      <span className="num text-muted">{fmtInt(count)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card
              title={latestRun ? `Latest telemetry · run #${latestRun.runId}` : 'Latest telemetry'}
              description={latestRun ? `${latestRun.missionName}` : undefined}
              actions={latestRun && <Link className="text-xs text-accent hover:underline" to={`/telemetry?mission=${latestRun.missionId}&run=${latestRun.runId}`}>Open telemetry</Link>}
            >
              {!latestRun ? (
                <p className="text-sm text-muted">Appears once a simulation run exists.</p>
              ) : reading.isLoading ? (
                <LoadingState />
              ) : reading.isError ? (
                reading.error && (reading.error as { kind?: string }).kind === 'not_found' ? (
                  <p className="text-sm text-muted">The latest run has no recorded telemetry yet.</p>
                ) : (
                  <ErrorState error={reading.error} onRetry={() => void reading.refetch()} title="Could not load the latest reading" />
                )
              ) : reading.data ? (
                <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
                  <dt className="text-muted">Altitude</dt><dd className="num text-right">{fmtNum(reading.data.altitudeMeters, 1, 'm')}</dd>
                  <dt className="text-muted">Vertical velocity</dt><dd className="num text-right">{fmtNum(reading.data.verticalVelocityMps, 2, 'm/s')}</dd>
                  <dt className="text-muted">Temperature</dt><dd className="num text-right">{fmtNum(reading.data.temperatureCelsius, 1, '°C')}</dd>
                  <dt className="text-muted">Battery</dt><dd className="num text-right">{fmtNum(reading.data.batteryPercent, 1, '%')}</dd>
                  <dt className="text-muted">Elapsed</dt><dd className="num text-right">{fmtDuration(reading.data.elapsedTimeSeconds)}</dd>
                  <dt className="text-muted">Flight state</dt><dd className="text-right"><FlightStateBadge state={reading.data.flightState} /></dd>
                </dl>
              ) : null}
            </Card>

            <Card
              title={latestRun ? `Recent events · run #${latestRun.runId}` : 'Recent events'}
              className="lg:col-span-2"
              actions={latestRun && <Link className="text-xs text-accent hover:underline" to={`/events?mission=${latestRun.missionId}&run=${latestRun.runId}`}>All events</Link>}
            >
              {!latestRun ? (
                <p className="text-sm text-muted">Appears once a simulation run exists.</p>
              ) : events.isLoading ? (
                <LoadingState />
              ) : events.isError ? (
                <ErrorState error={events.error} onRetry={() => void events.refetch()} title="Could not load events" />
              ) : events.data && events.data.length > 0 ? (
                <ul className="space-y-2 text-sm">
                  {events.data.map((e) => (
                    <li key={e.id} className="flex flex-wrap items-center gap-2">
                      <EventBadge type={e.eventType} />
                      <span className="num text-xs text-muted">t+{fmtNum(e.elapsedTimeSeconds, 1)} s</span>
                      <span className="text-muted">{e.message}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted">The latest run has no recorded events.</p>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  )
}
