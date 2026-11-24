import { CheckCircle2, Circle } from 'lucide-react'
import { useRecovery } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { RunPicker } from '../components/simulation/RunPicker'
import { NoRunSelected } from '../components/simulation/NoRunSelected'
import { Card, PageHeader } from '../components/ui/Card'
import { ErrorState, LoadingState, Notice } from '../components/ui/States'
import { StatusBadge } from '../components/ui/StatusBadge'
import { fmtDateTime, fmtNum } from '../utils/format'

/** The backend's recoveryStatus is a free-form string; known values get a friendlier label, others are shown verbatim. */
const KNOWN: Record<string, string> = {
  PENDING: 'Pending',
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

export default function RecoveryPage() {
  const { missionId, runId } = useRunSelection()
  const recovery = useRecovery(runId)
  const r = recovery.data

  const steps = r
    ? [
        { label: 'Landing detected', done: r.landingDetected },
        { label: 'Recovery started', done: r.recoveryStarted },
        { label: 'Recovery completed', done: r.recoveryCompleted },
      ]
    : []

  return (
    <>
      <PageHeader title="Recovery and landing" subtitle="Landing and recovery state as reported by the backend for a run." />
      <Card className="mb-4"><RunPicker /></Card>
      {runId === null && <Card><NoRunSelected missionSelected={missionId !== null} /></Card>}
      {runId !== null && recovery.isLoading && <LoadingState label="Loading recovery status…" />}
      {runId !== null && recovery.isError && <Card><ErrorState error={recovery.error} onRetry={() => void recovery.refetch()} title="Could not load recovery status" /></Card>}
      {r && (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card title="Status">
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-3"><dt className="text-muted">Mission</dt><dd>{r.missionName}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Run status</dt><dd><StatusBadge status={r.simulationStatus} /></dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Recovery status</dt><dd>{KNOWN[r.recoveryStatus] ?? r.recoveryStatus}{KNOWN[r.recoveryStatus] ? '' : ' (raw backend value)'}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Landing drift</dt><dd className="num">{fmtNum(r.landingDriftM, 1, 'm')}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Last event</dt><dd className="text-xs">{fmtDateTime(r.lastEventAt)}</dd></div>
            </dl>
            {r.message && <div className="mt-4"><Notice>{r.message}</Notice></div>}
          </Card>
          <Card title="Progress">
            <ol className="space-y-3">
              {steps.map((s) => (
                <li key={s.label} className="flex items-center gap-3 text-sm">
                  {s.done ? <CheckCircle2 className="h-5 w-5 text-ok" aria-hidden /> : <Circle className="h-5 w-5 text-muted" aria-hidden />}
                  <span>{s.label}</span>
                  <span className="text-xs text-muted">{s.done ? 'reached' : 'not reached'}</span>
                </li>
              ))}
            </ol>
            <p className="mt-4 text-xs text-muted">
              These flags come straight from the backend; the page does not infer or fill in missing steps.</p>
          </Card>
        </div>
      )}
    </>
  )
}
