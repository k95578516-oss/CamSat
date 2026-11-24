import { useMissions, useRuns } from '../../hooks/queries'
import { useRunSelection } from '../../hooks/useRunSelection'
import { SelectField } from '../ui/Field'
import { Notice } from '../ui/States'
import { statusLabel } from '../../utils/labels'
import { fmtDateTime } from '../../utils/format'

/** Mission → run selector shared by telemetry, 3D, events, recovery and report pages. */
export function RunPicker() {
  const { missionId, runId, setMission, setRun } = useRunSelection()
  const missions = useMissions()
  const runs = useRuns(missionId)

  if (missions.isError) return <Notice tone="crit">Could not load missions: {(missions.error as Error).message}</Notice>

  return (
    <div className="grid gap-3 sm:grid-cols-2 print:hidden">
      <SelectField
        label="Mission"
        value={missionId ?? ''}
        disabled={missions.isLoading}
        onChange={(e) => setMission(e.target.value ? Number(e.target.value) : null)}
      >
        <option value="">{missions.isLoading ? 'Loading missions…' : 'Select a mission'}</option>
        {missions.data?.map((m) => (
          <option key={m.id} value={m.id}>
            {m.name} (#{m.id})
          </option>
        ))}
      </SelectField>
      <SelectField
        label="Simulation run"
        value={runId ?? ''}
        disabled={missionId === null || runs.isLoading}
        onChange={(e) => setRun(e.target.value ? Number(e.target.value) : null)}
      >
        <option value="">
          {missionId === null ? 'Select a mission first' : runs.isLoading ? 'Loading runs…' : runs.data?.length ? 'Select a run' : 'No runs for this mission'}
        </option>
        {runs.data?.map((r) => (
          <option key={r.id} value={r.id}>
            Run #{r.id} · {statusLabel(r.status)} · {fmtDateTime(r.startedAt)}
          </option>
        ))}
      </SelectField>
    </div>
  )
}
