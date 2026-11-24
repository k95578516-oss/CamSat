import type { Tone } from '../../utils/labels'
import { eventLabel, eventTone, flightStateLabel, statusLabel, statusTone } from '../../utils/labels'

const TONE: Record<Tone, string> = {
  ok: 'border-ok/40 bg-ok/10 text-ok',
  warn: 'border-warn/40 bg-warn/10 text-warn',
  crit: 'border-crit/50 bg-crit/10 text-crit',
  info: 'border-accent/40 bg-accent/10 text-accent',
  neutral: 'border-line bg-ink-800 text-muted',
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium ${TONE[tone]}`}>
      {children}
    </span>
  )
}

export const StatusBadge = ({ status }: { status: string }) => (
  <Badge tone={statusTone(status)}>{statusLabel(status)}</Badge>
)
export const EventBadge = ({ type }: { type: string }) => <Badge tone={eventTone(type)}>{eventLabel(type)}</Badge>
export const FlightStateBadge = ({ state }: { state: string }) => (
  <Badge tone={state === 'ABORTED' ? 'crit' : state === 'LANDED' ? 'ok' : 'info'}>{flightStateLabel(state)}</Badge>
)
