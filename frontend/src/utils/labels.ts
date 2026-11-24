import type { FlightEventType, FlightState, SimulationStatus } from '../types/api'

export type Tone = 'ok' | 'warn' | 'crit' | 'info' | 'neutral'

export const STATUS_LABEL: Record<SimulationStatus, string> = {
  CREATED: 'Created',
  RUNNING: 'Running',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
}
export const STATUS_TONE: Record<SimulationStatus, Tone> = {
  CREATED: 'neutral',
  RUNNING: 'info',
  PAUSED: 'warn',
  COMPLETED: 'ok',
  FAILED: 'crit',
  CANCELLED: 'neutral',
}

export const FLIGHT_STATE_LABEL: Record<FlightState, string> = {
  READY: 'Ready',
  ASCENT: 'Ascent',
  DESCENT: 'Descent',
  PARACHUTE_DEPLOYED: 'Parachute deployed',
  LANDED: 'Landed',
  RECOVERY: 'Recovery',
  ABORTED: 'Aborted',
}

export const EVENT_LABEL: Record<FlightEventType, string> = {
  SIMULATION_STARTED: 'Simulation started',
  DESCENT_STARTED: 'Descent started',
  PARACHUTE_DEPLOYED: 'Parachute deployed',
  LOW_BATTERY: 'Low battery',
  LANDING_DETECTED: 'Landing detected',
  RECOVERY_STARTED: 'Recovery started',
  RECOVERY_COMPLETED: 'Recovery completed',
  SIMULATION_PAUSED: 'Simulation paused',
  SIMULATION_RESUMED: 'Simulation resumed',
  SIMULATION_COMPLETED: 'Simulation completed',
  SIMULATION_FAILED: 'Simulation failed',
  SIMULATION_CANCELLED: 'Simulation cancelled',
}

/**
 * The backend events carry no severity field. This is a UI-side classification of the
 * event TYPE only (it never creates or changes events).
 */
export const EVENT_TONE: Record<FlightEventType, Tone> = {
  SIMULATION_STARTED: 'info',
  DESCENT_STARTED: 'info',
  PARACHUTE_DEPLOYED: 'info',
  LOW_BATTERY: 'warn',
  LANDING_DETECTED: 'ok',
  RECOVERY_STARTED: 'info',
  RECOVERY_COMPLETED: 'ok',
  SIMULATION_PAUSED: 'warn',
  SIMULATION_RESUMED: 'info',
  SIMULATION_COMPLETED: 'ok',
  SIMULATION_FAILED: 'crit',
  SIMULATION_CANCELLED: 'neutral',
}

export function statusLabel(s: string): string {
  return STATUS_LABEL[s as SimulationStatus] ?? s
}
export function statusTone(s: string): Tone {
  return STATUS_TONE[s as SimulationStatus] ?? 'neutral'
}
export function flightStateLabel(s: string): string {
  return FLIGHT_STATE_LABEL[s as FlightState] ?? s
}
export function eventLabel(t: string): string {
  return EVENT_LABEL[t as FlightEventType] ?? t
}
export function eventTone(t: string): Tone {
  return EVENT_TONE[t as FlightEventType] ?? 'neutral'
}

/** Runs that have a terminal status and therefore data worth reading. */
export function isTerminal(s: SimulationStatus): boolean {
  return s === 'COMPLETED' || s === 'FAILED' || s === 'CANCELLED'
}
