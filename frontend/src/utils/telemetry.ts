import type { FlightState, TelemetryReadingResponse } from '../types/api'

/** Keeps at most `max` points (evenly strided) and always keeps the first and last sample. */
export function downsample<T>(points: T[], max: number): T[] {
  if (max < 2 || points.length <= max) return points
  const out: T[] = []
  const step = (points.length - 1) / (max - 1)
  let last = -1
  for (let i = 0; i < max; i++) {
    const idx = Math.round(i * step)
    if (idx !== last) out.push(points[idx])
    last = idx
  }
  return out
}

export function filterByTime<T extends { elapsedTimeSeconds: number }>(
  points: T[],
  from: number | null,
  to: number | null,
): T[] {
  return points.filter(
    (p) => (from === null || p.elapsedTimeSeconds >= from) && (to === null || p.elapsedTimeSeconds <= to),
  )
}

export type SeriesKey = 'altitudeMeters' | 'verticalVelocityMps' | 'temperatureCelsius' | 'batteryPercent'

export interface SeriesInfo {
  key: SeriesKey
  label: string
  unit: string
  color: string
}

/** Only the sensors that really exist in the backend telemetry DTO. */
export const SERIES: SeriesInfo[] = [
  { key: 'altitudeMeters', label: 'Altitude', unit: 'm', color: '#22d3ee' },
  { key: 'verticalVelocityMps', label: 'Vertical velocity', unit: 'm/s', color: '#a78bfa' },
  { key: 'temperatureCelsius', label: 'Temperature', unit: '°C', color: '#fbbf24' },
  { key: 'batteryPercent', label: 'Battery', unit: '%', color: '#4ade80' },
]

/** A series is "available" only if at least one sample has a finite value for it. */
export function availableSeries(points: TelemetryReadingResponse[]): SeriesKey[] {
  return SERIES.filter((s) => points.some((p) => Number.isFinite(p[s.key] as number))).map((s) => s.key)
}

export function hasPosition(points: TelemetryReadingResponse[]): boolean {
  return points.some((p) => p.latitude !== null && p.longitude !== null)
}

export interface Sample {
  altitudeMeters: number
  verticalVelocityMps: number
  flightState: FlightState
  elapsedTimeSeconds: number
  batteryPercent: number | null
  parachuteDeployed: boolean
}

/**
 * State of the recorded flight at elapsed time `t` (seconds). Altitude and velocity are linearly
 * interpolated between the two surrounding recorded samples; discrete fields come from the earlier one.
 * Assumes `points` is sorted by elapsedTimeSeconds (the backend returns them in order).
 */
export function sampleAt(points: TelemetryReadingResponse[], t: number): Sample | null {
  if (points.length === 0) return null
  let lo = 0
  let hi = points.length - 1
  if (t <= points[0].elapsedTimeSeconds) lo = hi = 0
  else if (t >= points[hi].elapsedTimeSeconds) lo = hi
  else {
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1
      if (points[mid].elapsedTimeSeconds <= t) lo = mid
      else hi = mid
    }
  }
  const a = points[lo]
  const b = points[Math.min(lo + 1, points.length - 1)]
  const span = b.elapsedTimeSeconds - a.elapsedTimeSeconds
  const f = span > 0 ? Math.min(1, Math.max(0, (t - a.elapsedTimeSeconds) / span)) : 0
  const lerp = (x: number, y: number) => x + (y - x) * f
  const deployedBy = points.slice(0, lo + 1).some((p) => p.flightState === 'PARACHUTE_DEPLOYED')
  return {
    altitudeMeters: lerp(a.altitudeMeters, b.altitudeMeters),
    verticalVelocityMps: lerp(a.verticalVelocityMps, b.verticalVelocityMps),
    flightState: a.flightState,
    elapsedTimeSeconds: Math.min(Math.max(t, points[0].elapsedTimeSeconds), points[points.length - 1].elapsedTimeSeconds),
    batteryPercent: a.batteryPercent,
    parachuteDeployed: deployedBy || a.flightState === 'PARACHUTE_DEPLOYED',
  }
}
