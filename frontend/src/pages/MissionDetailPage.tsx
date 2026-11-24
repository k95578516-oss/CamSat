import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Pencil, Rocket, Trash2 } from 'lucide-react'
import { useDeleteMission, useMission, useRuns } from '../hooks/queries'
import { toApiError } from '../api/errors'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { StatusBadge } from '../components/ui/StatusBadge'
import { useToast } from '../components/ui/Toast'
import { fmtDateTime, fmtDuration, fmtNum } from '../utils/format'

export function parseRouteId(v: string | undefined): number | null {
  return v && /^\d+$/.test(v) && Number.isSafeInteger(Number(v)) ? Number(v) : null
}

export default function MissionDetailPage() {
  const id = parseRouteId(useParams().missionId)
  const navigate = useNavigate()
  const toast = useToast()
  const mission = useMission(id)
  const runs = useRuns(id)
  const remove = useDeleteMission()
  const [confirm, setConfirm] = useState(false)

  if (id === null) return <ErrorState error={new Error('invalid')} title="Invalid mission address" />
  if (mission.isLoading) return <LoadingState label="Loading mission…" />
  if (mission.isError) return <Card><ErrorState error={mission.error} onRetry={() => void mission.refetch()} title="Could not load the mission" /></Card>
  const m = mission.data!
  const hasRuns = (runs.data?.length ?? 0) > 0
  const lockReason = hasRuns ? 'This mission already has simulation runs; the backend does not allow editing or deleting it.' : undefined

  const doDelete = async () => {
    try {
      await remove.mutateAsync(m.id)
      toast.success(`Deleted mission “${m.name}”.`)
      navigate('/missions', { replace: true })
    } catch (e) {
      toast.error(toApiError(e).message)
      setConfirm(false)
    }
  }

  const params: [string, string][] = [
    ['Payload mass', fmtNum(m.payloadMassKg, 2, 'kg')],
    ['Release altitude', fmtNum(m.releaseAltitudeM, 0, 'm')],
    ['Parachute diameter', fmtNum(m.parachuteDiameterM, 2, 'm')],
    ['Drag coefficient', fmtNum(m.dragCoefficient, 2)],
    ['Wind speed', fmtNum(m.windSpeedMps, 1, 'm/s')],
    ['Wind direction', `${m.windDirectionDeg}°`],
    ['Initial battery', `${m.initialBatteryPercent}%`],
  ]

  return (
    <>
      <PageHeader
        title={m.name}
        subtitle={`Mission #${m.id} · created ${fmtDateTime(m.createdAt)} · updated ${fmtDateTime(m.updatedAt)}`}
        actions={
          <>
            <Link to={`/simulation?mission=${m.id}`} className="inline-flex items-center gap-2 rounded-md bg-accent px-3.5 py-2 text-sm font-medium text-ink-950 hover:bg-cyan-300">
              <Rocket className="h-4 w-4" aria-hidden /> Run simulation
            </Link>
            {hasRuns || runs.isLoading ? (
              <Button disabled title={lockReason ?? 'Checking run history…'} icon={<Pencil className="h-4 w-4" aria-hidden />}>Edit</Button>
            ) : (
              <Link to={`/missions/${m.id}/edit`} className="inline-flex items-center gap-2 rounded-md border border-line bg-ink-700 px-3.5 py-2 text-sm font-medium hover:bg-ink-800">
                <Pencil className="h-4 w-4" aria-hidden /> Edit
              </Link>
            )}
            <Button variant="danger" disabled={hasRuns || runs.isLoading} title={lockReason ?? 'Checking run history…'} onClick={() => setConfirm(true)} icon={<Trash2 className="h-4 w-4" aria-hidden />}>
              Delete
            </Button>
          </>
        }
      />
      {hasRuns && <div className="mb-4"><Notice tone="info">{lockReason}</Notice></div>}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Configuration">
          <dl className="space-y-2 text-sm">
            {params.map(([k, v]) => (
              <div key={k} className="flex justify-between gap-3">
                <dt className="text-muted">{k}</dt>
                <dd className="num">{v}</dd>
              </div>
            ))}
          </dl>
        </Card>
        <Card title="Simulation run history" className="lg:col-span-2">
          {runs.isLoading && <LoadingState />}
          {runs.isError && <ErrorState error={runs.error} onRetry={() => void runs.refetch()} title="Could not load runs" />}
          {runs.data && runs.data.length === 0 && (
            <EmptyState title="No runs yet" description="Start a simulation to record telemetry for this mission." action={<Link to={`/simulation?mission=${m.id}`} className="text-sm text-accent hover:underline">Open Simulation</Link>} />
          )}
          {runs.data && runs.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px] text-left text-sm">
                <thead className="text-xs text-muted">
                  <tr>
                    <th scope="col" className="py-1.5 pr-3">Run</th>
                    <th scope="col" className="py-1.5 pr-3">Status</th>
                    <th scope="col" className="py-1.5 pr-3">Started</th>
                    <th scope="col" className="py-1.5 pr-3">Landing time</th>
                    <th scope="col" className="py-1.5 pr-3">Drift</th>
                    <th scope="col" className="py-1.5">Data</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.data.map((r) => (
                    <tr key={r.id} className="border-t border-line">
                      <td className="py-2 pr-3">#{r.id}</td>
                      <td className="py-2 pr-3"><StatusBadge status={r.status} /></td>
                      <td className="py-2 pr-3 text-xs text-muted">{fmtDateTime(r.startedAt)}</td>
                      <td className="num py-2 pr-3">{fmtDuration(r.landingTimeSeconds)}</td>
                      <td className="num py-2 pr-3">{fmtNum(r.landingDriftM, 1, 'm')}</td>
                      <td className="py-2 text-xs">
                        <Link className="mr-3 text-accent hover:underline" to={`/telemetry?mission=${m.id}&run=${r.id}`}>Telemetry</Link>
                        <Link className="text-accent hover:underline" to={`/reports?mission=${m.id}&run=${r.id}`}>Report</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
      <ConfirmDialog open={confirm} title="Delete mission?" message={`“${m.name}” will be permanently deleted.`} confirmLabel="Delete mission" loading={remove.isPending} onConfirm={() => void doDelete()} onCancel={() => setConfirm(false)} />
    </>
  )
}
