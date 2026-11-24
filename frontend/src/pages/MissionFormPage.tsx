import { Link, useNavigate, useParams } from 'react-router-dom'
import { useCreateMission, useMission, useRuns, useUpdateMission } from '../hooks/queries'
import { MissionForm } from '../components/missions/MissionForm'
import { Card, PageHeader } from '../components/ui/Card'
import { ErrorState, LoadingState, Notice } from '../components/ui/States'
import { useToast } from '../components/ui/Toast'
import { parseRouteId } from './MissionDetailPage'
import type { MissionRequest } from '../types/api'

export function CreateMissionPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const create = useCreateMission()
  const submit = async (values: MissionRequest) => {
    const created = await create.mutateAsync(values)
    toast.success(`Created mission “${created.name}”.`)
    navigate(`/missions/${created.id}`)
  }
  return (
    <>
      <PageHeader title="New mission" subtitle="Configure the CanSat release and descent parameters. All values use SI units." />
      <Card className="max-w-3xl">
        <MissionForm submitLabel="Create mission" onSubmit={submit} onCancel={() => navigate('/missions')} />
      </Card>
    </>
  )
}

export function EditMissionPage() {
  const id = parseRouteId(useParams().missionId)
  const navigate = useNavigate()
  const toast = useToast()
  const mission = useMission(id)
  const runs = useRuns(id)
  const update = useUpdateMission(id ?? -1)

  if (id === null) return <ErrorState error={new Error('invalid')} title="Invalid mission address" />
  if (mission.isLoading || runs.isLoading) return <LoadingState label="Loading mission…" />
  if (mission.isError) return <Card><ErrorState error={mission.error} onRetry={() => void mission.refetch()} title="Could not load the mission" /></Card>
  if (runs.isError) return <Card><ErrorState error={runs.error} onRetry={() => void runs.refetch()} title="Could not check the run history" /></Card>
  const m = mission.data!

  if ((runs.data?.length ?? 0) > 0) {
    return (
      <>
        <PageHeader title={`Edit ${m.name}`} />
        <Notice tone="warn">
          This mission already has simulation runs, so the backend does not allow changing its configuration. Create a new mission with the changed values instead.{' '}
          <Link to={`/missions/${m.id}`} className="text-accent hover:underline">Back to the mission</Link>
        </Notice>
      </>
    )
  }

  const submit = async (values: MissionRequest) => {
    await update.mutateAsync(values)
    toast.success('Mission updated.')
    navigate(`/missions/${m.id}`)
  }
  return (
    <>
      <PageHeader title={`Edit ${m.name}`} subtitle={`Mission #${m.id}`} />
      <Card className="max-w-3xl">
        <MissionForm
          defaultValues={{
            name: m.name,
            payloadMassKg: m.payloadMassKg,
            releaseAltitudeM: m.releaseAltitudeM,
            parachuteDiameterM: m.parachuteDiameterM,
            dragCoefficient: m.dragCoefficient,
            windSpeedMps: m.windSpeedMps,
            windDirectionDeg: m.windDirectionDeg,
            initialBatteryPercent: m.initialBatteryPercent,
          }}
          submitLabel="Save changes"
          onSubmit={submit}
          onCancel={() => navigate(`/missions/${m.id}`)}
        />
      </Card>
    </>
  )
}
