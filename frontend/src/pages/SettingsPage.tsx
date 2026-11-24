import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { LogOut, RefreshCw } from 'lucide-react'
import { dashboardApi } from '../api/dashboardApi'
import { env } from '../config/env'
import { useAuth } from '../features/auth/AuthContext'
import { useSettings } from '../hooks/useSettings'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { SelectField } from '../components/ui/Field'
import { Badge } from '../components/ui/StatusBadge'
import { fmtDateTime } from '../utils/format'
import { CHART_POINT_OPTIONS } from '../utils/settings'

export default function SettingsPage() {
  const { user, session, logout } = useAuth()
  const navigate = useNavigate()
  const { settings, update } = useSettings()
  const probe = useQuery({ queryKey: ['connectivity'], queryFn: () => dashboardApi.probe(), staleTime: 0 })

  const p = probe.data
  const connectivity = probe.isFetching ? <Badge tone="neutral">Checking…</Badge>
    : !p ? <Badge tone="neutral">Unknown</Badge>
    : !p.reachable ? <Badge tone="crit">Unreachable</Badge>
    : p.status === 401 || p.status === 403 ? <Badge tone="warn">Reachable, session rejected (HTTP {p.status})</Badge>
    : p.status && p.status >= 500 ? <Badge tone="crit">Reachable, backend error (HTTP {p.status})</Badge>
    : <Badge tone="ok">Connected (HTTP {p.status})</Badge>

  return (
    <>
      <PageHeader title="Settings and account" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Account" description="The backend has no profile endpoint; these values come from the sign-in response.">
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-3"><dt className="text-muted">Name</dt><dd>{user?.name}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Email</dt><dd>{user?.email}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Role</dt><dd>{user?.role}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Account created</dt><dd className="text-xs">{fmtDateTime(user?.createdAt)}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Session expires</dt><dd className="text-xs">{session ? new Date(session.expiresAt).toLocaleString() : '—'}</dd></div>
          </dl>
          <div className="mt-4">
            <Button variant="danger" icon={<LogOut className="h-4 w-4" aria-hidden />} onClick={() => { logout(); navigate('/login', { replace: true }) }}>Sign out</Button>
          </div>
        </Card>

        <Card title="Backend connection">
          <dl className="space-y-2 text-sm">
            <div className="flex items-center justify-between gap-3"><dt className="text-muted">Status</dt><dd>{connectivity}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">API base URL</dt><dd className="num break-all text-xs">{env.apiBaseUrl || '(same origin)'}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Response time</dt><dd className="num">{p ? `${p.latencyMs} ms` : '—'}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-muted">Frontend version</dt><dd className="num">{env.appVersion}</dd></div>
          </dl>
          <p className="mt-3 text-xs text-muted">The backend exposes no version or health endpoint, so its version is unknown. Reachability is checked with a request to the dashboard endpoint.</p>
          <div className="mt-3"><Button onClick={() => void probe.refetch()} loading={probe.isFetching} icon={<RefreshCw className="h-4 w-4" aria-hidden />}>Check again</Button></div>
        </Card>

        <Card title="Display">
          <SelectField label="Maximum points per chart" value={settings.maxChartPoints} onChange={(e) => update({ maxChartPoints: Number(e.target.value) })}>
            {CHART_POINT_OPTIONS.map((n) => <option key={n} value={n}>{n}</option>)}
          </SelectField>
          <p className="mt-2 text-xs text-muted">Long runs are thinned evenly for drawing only; tables and CSV exports keep every reading.</p>
          <div className="mt-4">
            <SelectField label="Theme" value="dark" disabled title="Only the dark mission-control theme is implemented">
              <option value="dark">Dark (mission control)</option>
            </SelectField>
            <p className="mt-2 text-xs text-muted">A light theme is not implemented.</p>
          </div>
        </Card>
      </div>
    </>
  )
}
