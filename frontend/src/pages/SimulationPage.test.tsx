import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SimulationPage from './SimulationPage'
import { ApiError } from '../api/errors'
import { missionApi } from '../api/missionApi'
import { simulationApi } from '../api/simulationApi'
import { fakeMission, fakeRun, fakeSession, renderWithProviders } from '../test/utils'
import type { SimulationRunResponse } from '../types/api'

vi.mock('../api/missionApi', () => ({
  missionApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/simulationApi', () => ({
  simulationApi: { createRun: vi.fn(), runsByMission: vi.fn(), getRun: vi.fn(), recentRuns: vi.fn(), start: vi.fn(), cancel: vi.fn(), pause: vi.fn(), resume: vi.fn() },
}))

const tree = (
  <Routes>
    <Route path="/simulation" element={<SimulationPage />} />
  </Routes>
)
const open = () => renderWithProviders(tree, { route: '/simulation?mission=1', session: fakeSession() })

let runs: SimulationRunResponse[]

beforeEach(() => {
  runs = []
  vi.mocked(missionApi.list).mockResolvedValue([fakeMission])
  vi.mocked(missionApi.get).mockResolvedValue(fakeMission)
  vi.mocked(simulationApi.runsByMission).mockImplementation(async () => runs)
  vi.mocked(simulationApi.recentRuns).mockResolvedValue([])
  vi.mocked(simulationApi.createRun).mockImplementation(async () => {
    runs = [fakeRun()]
    return runs[0]
  })
})

describe('SimulationPage', () => {
  it('creates and starts a run once, even on a double click, then shows the result', async () => {
    let finish!: (r: SimulationRunResponse) => void
    vi.mocked(simulationApi.start).mockImplementation(() => new Promise((res) => { finish = res }))
    open()
    const button = await screen.findByRole('button', { name: /create run and start/i })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.dblClick(button)

    expect(await screen.findByText(/is being simulated by the backend/i)).toBeInTheDocument()
    expect(simulationApi.createRun).toHaveBeenCalledTimes(1)
    expect(simulationApi.start).toHaveBeenCalledTimes(1)

    const done = fakeRun({ status: 'COMPLETED', landingTimeSeconds: 61.5, landingDriftM: 12.3, finalResult: 'Landed safely' })
    runs = [done]
    finish(done)
    expect((await screen.findAllByText('Completed')).length).toBeGreaterThan(0)
    expect(screen.getByText(/Landed safely/)).toBeInTheDocument()
    expect(screen.queryByText(/is being simulated by the backend/i)).not.toBeInTheDocument()
  })

  it('shows the failure and keeps the page usable when start fails', async () => {
    vi.mocked(simulationApi.start).mockRejectedValue(new ApiError('timeout', 'The request timed out before the backend answered.'))
    open()
    const button = await screen.findByRole('button', { name: /create run and start/i })
    await waitFor(() => expect(button).toBeEnabled())
    await userEvent.click(button)
    expect((await screen.findAllByText(/timed out/i)).length).toBeGreaterThan(0)
    expect(screen.getByText(/may still have processed the run/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create run and start/i })).toBeEnabled()
  })

  it('shows a failed run with its reason', async () => {
    runs = [fakeRun({ status: 'FAILED', finalResult: 'Battery depleted' })]
    open()
    expect(await screen.findByText(/Failure reason:/)).toBeInTheDocument()
    expect(screen.getByText(/Battery depleted/)).toBeInTheDocument()
  })

  it('enables Pause (not Resume) for a running run and calls the pause endpoint', async () => {
    runs = [fakeRun({ status: 'RUNNING', progressPercent: 40, elapsedSimTimeSeconds: 12 })]
    vi.mocked(simulationApi.pause).mockResolvedValue(fakeRun({ status: 'PAUSED' }))
    open()
    const pause = await screen.findByRole('button', { name: 'Pause' })
    expect(pause).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Resume' })).toBeDisabled()
    expect(screen.getByLabelText('Simulation progress')).toBeInTheDocument()
    await userEvent.click(pause)
    await waitFor(() => expect(simulationApi.pause).toHaveBeenCalledWith(5))
  })

  it('enables Resume (not Pause) for a paused run and calls the resume endpoint', async () => {
    runs = [fakeRun({ status: 'PAUSED' })]
    vi.mocked(simulationApi.resume).mockResolvedValue(fakeRun({ status: 'RUNNING' }))
    open()
    const resume = await screen.findByRole('button', { name: 'Resume' })
    expect(resume).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Pause' })).toBeDisabled()
    await userEvent.click(resume)
    await waitFor(() => expect(simulationApi.resume).toHaveBeenCalledWith(5))
  })

  it('disables Pause and Resume for runs that are not executing', async () => {
    runs = [fakeRun({ status: 'COMPLETED' })]
    open()
    expect(await screen.findByRole('button', { name: 'Pause' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Resume' })).toBeDisabled()
  })
})
