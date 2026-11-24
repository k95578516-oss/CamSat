import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useEventCount, useEvents } from '../hooks/queries'
import { useRunSelection } from '../hooks/useRunSelection'
import { RunPicker } from '../components/simulation/RunPicker'
import { NoRunSelected } from '../components/simulation/NoRunSelected'
import { Card, PageHeader } from '../components/ui/Card'
import { SelectField } from '../components/ui/Field'
import { EmptyState, ErrorState, LoadingState, Notice } from '../components/ui/States'
import { EventBadge } from '../components/ui/StatusBadge'
import { eventLabel, eventTone } from '../utils/labels'
import { fmtDateTime, fmtNum } from '../utils/format'

export default function EventsPage() {
  const { missionId, runId } = useRunSelection()
  const events = useEvents(runId)
  const count = useEventCount(runId)
  const [type, setType] = useState('')

  const types = useMemo(() => [...new Set((events.data ?? []).map((e) => e.eventType))], [events.data])
  const rows = useMemo(() => (events.data ?? []).filter((e) => !type || e.eventType === type), [events.data, type])
  const q = `mission=${missionId ?? ''}&run=${runId ?? ''}`

  return (
    <>
      <PageHeader title="Mission events" subtitle="Events written by the backend while a run executes, in order." />
      <Card className="mb-4"><RunPicker /></Card>
      {runId === null && <Card><NoRunSelected missionSelected={missionId !== null} /></Card>}
      {runId !== null && events.isLoading && <LoadingState label="Loading events…" />}
      {runId !== null && events.isError && <Card><ErrorState error={events.error} onRetry={() => void events.refetch()} title="Could not load events" /></Card>}
      {runId !== null && events.data && (
        <Card
          title={`Run #${runId}`}
          description={count.data !== undefined ? `Backend reports ${count.data} event${count.data === 1 ? '' : 's'} for this run.` : undefined}
          actions={
            <div className="flex gap-3 text-xs">
              <Link className="text-accent hover:underline" to={`/recovery?${q}`}>Recovery</Link>
              <Link className="text-accent hover:underline" to={`/reports?${q}`}>Report</Link>
              <Link className="text-accent hover:underline" to={`/telemetry?${q}`}>Telemetry</Link>
            </div>
          }
        >
          <div className="mb-3 max-w-xs">
            <SelectField label="Filter by event type" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">All types</option>
              {types.map((t) => <option key={t} value={t}>{eventLabel(t)}</option>)}
            </SelectField>
          </div>
          <div className="mb-3"><Notice>The backend does not provide an event severity. Badge colours are derived from the event type only.</Notice></div>
          {events.data.length === 0 ? (
            <EmptyState title="No events recorded for this run" description="Events are created when a run is started. A run that was only created has none." />
          ) : rows.length === 0 ? (
            <EmptyState title="No events of this type" />
          ) : (
            <ol className="divide-y divide-line">
              {rows.map((e) => (
                <li key={e.id} className={`flex flex-wrap items-start gap-x-3 gap-y-1 py-2.5 ${eventTone(e.eventType) === 'crit' ? 'border-l-2 border-crit bg-crit/5 pl-3' : eventTone(e.eventType) === 'warn' ? 'border-l-2 border-warn pl-3' : ''}`}>
                  <EventBadge type={e.eventType} />
                  <span className="num text-xs text-muted">t+{fmtNum(e.elapsedTimeSeconds, 1)} s</span>
                  <span className="min-w-0 flex-1 text-sm">{e.message}</span>
                  <time className="text-xs text-muted" dateTime={e.createdAt}>{fmtDateTime(e.createdAt)}</time>
                </li>
              ))}
            </ol>
          )}
        </Card>
      )}
    </>
  )
}
