import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui/Toast'
import { AuthProvider } from '../features/auth/AuthContext'
import { saveSession, type StoredSession } from '../api/tokenStore'
import type { MissionResponse, SimulationRunResponse } from '../types/api'

export function fakeSession(overrides: Partial<StoredSession> = {}): StoredSession {
  return {
    token: 'test-token',
    tokenType: 'Bearer',
    expiresAt: Date.now() + 60 * 60 * 1000,
    user: { id: 1, name: 'Ada Tester', email: 'ada@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' },
    ...overrides,
  }
}

export const fakeMission: MissionResponse = {
  id: 1,
  name: 'Alpha',
  payloadMassKg: 0.35,
  releaseAltitudeM: 600,
  parachuteDiameterM: 0.5,
  dragCoefficient: 1.2,
  windSpeedMps: 3,
  windDirectionDeg: 90,
  initialBatteryPercent: 100,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export function fakeRun(overrides: Partial<SimulationRunResponse> = {}): SimulationRunResponse {
  return {
    id: 5,
    missionId: 1,
    status: 'CREATED',
    startedAt: null,
    endedAt: null,
    landingTimeSeconds: null,
    landingDriftM: null,
    finalResult: null,
    ...overrides,
  }
}

/** Renders inside Query, Toast, Auth and Router providers. `children` normally contains <Routes>. */
export function renderWithProviders(children: ReactElement, opts: { route?: string; session?: StoredSession | null } = {}) {
  if (opts.session) saveSession(opts.session)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={[opts.route ?? '/']}>{children}</MemoryRouter>
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>,
  )
}
