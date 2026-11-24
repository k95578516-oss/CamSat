import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MissionForm, missionSchema } from './MissionForm'
import { ApiError } from '../../api/errors'

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Mission name'), 'Alpha')
  await user.type(screen.getByLabelText(/payload mass/i), '0.35')
  await user.type(screen.getByLabelText(/release altitude/i), '600')
  await user.type(screen.getByLabelText(/parachute diameter/i), '0.5')
  await user.type(screen.getByLabelText(/drag coefficient/i), '1.2')
  await user.type(screen.getByLabelText(/wind speed/i), '3')
  await user.type(screen.getByLabelText(/wind direction/i), '90')
  await user.type(screen.getByLabelText(/initial battery/i), '100')
}

describe('MissionForm', () => {
  it('shows validation errors and does not submit an empty form', async () => {
    const onSubmit = vi.fn()
    render(<MissionForm submitLabel="Create mission" onSubmit={onSubmit} />)
    await userEvent.click(screen.getByRole('button', { name: 'Create mission' }))
    expect(await screen.findByText('Mission name is required')).toBeInTheDocument()
    expect(screen.getAllByText('Enter a number').length).toBeGreaterThan(0)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits numeric values matching the backend request shape', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<MissionForm submitLabel="Create mission" onSubmit={onSubmit} />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create mission' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit.mock.calls[0][0]).toEqual({
      name: 'Alpha', payloadMassKg: 0.35, releaseAltitudeM: 600, parachuteDiameterM: 0.5,
      dragCoefficient: 1.2, windSpeedMps: 3, windDirectionDeg: 90, initialBatteryPercent: 100,
    })
  })

  it('maps backend field errors onto the form and preserves input', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError('validation', 'Validation failed', 400, { payloadMassKg: 'Payload too heavy for this parachute' }))
    render(<MissionForm submitLabel="Create mission" onSubmit={onSubmit} />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create mission' }))
    expect(await screen.findByText('Payload too heavy for this parachute')).toBeInTheDocument()
    expect(screen.getByLabelText('Mission name')).toHaveValue('Alpha')
    expect(screen.getByLabelText(/payload mass/i)).toHaveValue(0.35)
  })

  it('shows a general error when the backend gives no field details', async () => {
    const user = userEvent.setup()
    render(<MissionForm submitLabel="Save" onSubmit={vi.fn().mockRejectedValue(new ApiError('network', 'Cannot reach the CamSat backend.'))} />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach/i)
  })
})

describe('missionSchema', () => {
  const ok = { name: 'A', payloadMassKg: 1, releaseAltitudeM: 100, parachuteDiameterM: 0.5, dragCoefficient: 1, windSpeedMps: 0, windDirectionDeg: 0, initialBatteryPercent: 50 }
  it('accepts boundary-valid values', () => {
    expect(missionSchema.safeParse(ok).success).toBe(true)
    expect(missionSchema.safeParse({ ...ok, windDirectionDeg: 359, initialBatteryPercent: 100 }).success).toBe(true)
  })
  it.each([
    ['windDirectionDeg', 360], ['windDirectionDeg', -1], ['initialBatteryPercent', 101],
    ['dragCoefficient', 2.5], ['payloadMassKg', 0], ['windSpeedMps', -1],
    ['payloadMassKg', 0.04], ['payloadMassKg', 51], ['releaseAltitudeM', 19], ['releaseAltitudeM', 10001],
    ['parachuteDiameterM', 3.1], ['windSpeedMps', 51], ['initialBatteryPercent', 0],
  ])('rejects %s = %s', (field, value) => {
    expect(missionSchema.safeParse({ ...ok, [field]: value }).success).toBe(false)
  })
})
