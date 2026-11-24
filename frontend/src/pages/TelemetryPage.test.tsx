import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TelemetryPage from './TelemetryPage'
import { ApiError } from '../api/errors'
import { missionApi } from '../api/missionApi'
import { simulationApi } from '../api/simulationApi'
import { telemetryApi } from '../api/telemetryApi'
import { fakeMission, fakeRun, fakeSession, renderWithProviders } from '../test/utils'
import type { TelemetryReadingResponse } from '../types/api'

vi.mock('../api/missionApi', () => ({ missionApi: { list: vi.fn(), get: vi.fn() } }))
vi.mock('../api/simulationApi', () => ({ simulationApi: { runsByMission: vi.fn() } }))
vi.mock('../api/telemetryApi', () => ({ telemetryApi: { list: vi.fn(), latestReading: vi.fn() } }))

const tree = (
  <Routes>
    <Route path="/telemetry" element={<TelemetryPage />} />
  </Routes>
)
const open = (route = '/telemetry?mission=1&run=5') => renderWithProviders(tree, { route, session: fakeSession() })

const sample = (t: number, alt: number, state: TelemetryReadingResponse['flightState'] = 'DESCENT'): TelemetryReadingResponse => ({
  id: t + 1, runId: 5, elapsedTimeSeconds: t, altitudeMeters: alt, verticalVelocityMps: -8,
  temperatureCelsius: 20, batteryPercent: null, latitude: null, longitude: null,
  flightState: state, recordedAt: '2026-01-01T00:00:00Z',
})

beforeEach(() => {
  vi.mocked(missionApi.list).mockResolvedValue([fakeMission])
  vi.mocked(simulationApi.runsByMission).mockResolvedValue([fakeRun({ status: 'COMPLETED' })])
})

describe('TelemetryPage', () => {
  it('asks the user to choose a run first', async () => {
    open('/telemetry')
    expect(await screen.findByText(/select a mission and a run/i)).toBeInTheDocument()
    expect(telemetryApi.list).not.toHaveBeenCalled()
  })

  it('shows a loading state, then charts only for sensors that have data', async () => {
    vi.mocked(telemetryApi.list).mockResolvedValue([sample(0, 600), sample(10, 400, 'PARACHUTE_DEPLOYED'), sample(20, 0, 'LANDED')])
    open()
    expect(screen.getByRole('status')).toHaveTextContent(/loading telemetry/i)
    expect(await screen.findByRole('figure', { name: /altitude versus elapsed time/i })).toBeInTheDocument()
    expect(screen.getByRole('figure', { name: /temperature versus elapsed time/i })).toBeInTheDocument()
    // battery is null in every sample: no chart and no toggle for it
    expect(screen.queryByRole('figure', { name: /battery/i })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Battery')).not.toBeInTheDocument()
    expect(screen.getByText(/3 recorded readings/i)).toBeInTheDocument()
    expect(screen.getByText(/latitude and longitude are not recorded/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /export csv \(3\)/i })).toBeEnabled()
  })

  it('shows an empty state when the run has no telemetry', async () => {
    vi.mocked(telemetryApi.list).mockResolvedValue([])
    open()
    expect(await screen.findByText(/no telemetry recorded for this run/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /export csv/i })).toBeDisabled()
  })

  it('shows an error state with retry when the API fails', async () => {
    vi.mocked(telemetryApi.list).mockRejectedValue(new ApiError('server', 'The backend reported an internal error. Try again later.', 500))
    open()
    expect(await screen.findByText(/could not load telemetry/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})
