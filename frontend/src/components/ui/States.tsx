import { AlertTriangle, Inbox, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { ApiError, toApiError } from '../../api/errors'
import { Button } from './Button'

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-10 text-sm text-muted">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <Inbox className="h-6 w-6 text-muted" aria-hidden />
      <p className="text-sm font-medium text-fg">{title}</p>
      {description && <p className="max-w-md text-sm text-muted">{description}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry, title = 'Could not load data' }: { error: unknown; onRetry?: () => void; title?: string }) {
  const e: ApiError = toApiError(error)
  return (
    <div role="alert" className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <AlertTriangle className="h-6 w-6 text-crit" aria-hidden />
      <p className="text-sm font-medium text-fg">{title}</p>
      <p className="max-w-md text-sm text-muted">{e.message}</p>
      {onRetry && (
        <Button onClick={onRetry} className="mt-1">
          Try again
        </Button>
      )}
    </div>
  )
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn' | 'crit'; children: ReactNode }) {
  const cls = {
    info: 'border-accent/30 bg-accent/5 text-fg',
    warn: 'border-warn/40 bg-warn/5 text-fg',
    crit: 'border-crit/40 bg-crit/5 text-fg',
  }[tone]
  return (
    <div role={tone === 'crit' ? 'alert' : 'note'} className={`rounded-md border px-3 py-2 text-sm ${cls}`}>
      {children}
    </div>
  )
}
