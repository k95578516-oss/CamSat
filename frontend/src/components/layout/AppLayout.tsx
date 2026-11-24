import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  Activity,
  AlertTriangle,
  Box,
  FileText,
  LayoutDashboard,
  LifeBuoy,
  LogOut,
  Menu,
  Rocket,
  Settings,
  SatelliteDish,
  X,
} from 'lucide-react'
import { useAuth } from '../../features/auth/AuthContext'
import { Button } from '../ui/Button'
import { ErrorBoundary } from '../ErrorBoundary'

const NAV = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/missions', label: 'Missions', icon: SatelliteDish },
  { to: '/simulation', label: 'Simulation', icon: Rocket },
  { to: '/telemetry', label: 'Telemetry', icon: Activity },
  { to: '/visualization', label: '3D view', icon: Box },
  { to: '/events', label: 'Events', icon: AlertTriangle },
  { to: '/recovery', label: 'Recovery', icon: LifeBuoy },
  { to: '/reports', label: 'Reports', icon: FileText },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const nav = (
    <nav aria-label="Main" className="flex flex-col gap-1 p-3">
      {NAV.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors ${
              isActive ? 'bg-accent/10 text-accent' : 'text-muted hover:bg-ink-800 hover:text-fg'
            }`
          }
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="flex min-h-screen">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-screen w-56 shrink-0 flex-col border-r border-line bg-ink-900 md:flex print:hidden">
        <div className="flex items-center gap-2 border-b border-line px-4 py-4">
          <SatelliteDish className="h-5 w-5 text-accent" aria-hidden />
          <span className="font-semibold tracking-tight">CamSat</span>
          <span className="text-xs text-muted">Ground station</span>
        </div>
        {nav}
      </aside>

      {/* Mobile drawer */}
      {open && (
        <div className="fixed inset-0 z-40 md:hidden print:hidden">
          <div className="absolute inset-0 bg-black/60" onClick={() => setOpen(false)} aria-hidden />
          <aside className="absolute left-0 top-0 h-full w-60 border-r border-line bg-ink-900">
            <div className="flex items-center justify-between border-b border-line px-4 py-3">
              <span className="font-semibold">CamSat</span>
              <button type="button" aria-label="Close navigation" onClick={() => setOpen(false)} className="rounded p-1 text-muted hover:text-fg">
                <X className="h-5 w-5" />
              </button>
            </div>
            {nav}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex items-center justify-between gap-3 border-b border-line bg-ink-950/90 px-4 py-2.5 backdrop-blur print:hidden">
          <button type="button" aria-label="Open navigation" onClick={() => setOpen(true)} className="rounded p-1.5 text-muted hover:text-fg md:hidden">
            <Menu className="h-5 w-5" />
          </button>
          <div className="hidden text-xs text-muted md:block">Recorded simulation data · not live telemetry</div>
          <div className="flex items-center gap-3">
            <div className="text-right text-xs leading-tight">
              <div className="font-medium text-fg">{user?.name}</div>
              <div className="text-muted">{user?.email}</div>
            </div>
            <Button
              variant="ghost"
              icon={<LogOut className="h-4 w-4" aria-hidden />}
              onClick={() => {
                logout()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </Button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 p-4 md:p-6">
          <ErrorBoundary inline>
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  )
}
