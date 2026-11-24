import { describe, expect, it } from 'vitest'
import { availableSeries, downsample, filterByTime, hasPosition, sampleAt } from './telemetry'
import type { TelemetryReadingResponse } from '../types/api'

function reading(t: number, alt: number, extra: Partial<TelemetryReadingResponse> = {}): TelemetryReadingResponse {
  return {
    id: t + 1,
    runId: 1,
    elapsedTimeSeconds: t,
    altitudeMeters: alt,
    verticalVelocityMps: -5,
    temperatureCelsius: null,
    batteryPercent: null,
    latitude: null,
    longitude: null,
    flightState: 'DESCENT',
    recordedAt: '2026-01-01T00:00:00Z',
    ...extra,
  }
}

describe('downsample', () => {
  it('returns the input when under the limit', () => {
    const pts = [1, 2, 3]
    expect(downsample(pts, 10)).toBe(pts)
  })
  it('limits length and keeps first and last points', () => {
    const pts = Array.from({ length: 5000 }, (_, i) => i)
    const out = downsample(pts, 100)
    expect(out.length).toBeLessThanOrEqual(100)
    expect(out[0]).toBe(0)
    expect(out[out.length - 1]).toBe(4999)
  })
})

describe('filterByTime', () => {
  const pts = [reading(0, 100), reading(10, 50), reading(20, 0)]
  it('applies inclusive bounds', () => {
    expect(filterByTime(pts, 10, 20).map((p) => p.elapsedTimeSeconds)).toEqual([10, 20])
  })
  it('treats null as unbounded', () => {
    expect(filterByTime(pts, null, null)).toHaveLength(3)
  })
})

describe('availableSeries', () => {
  it('lists only sensors that have data', () => {
    const pts = [reading(0, 100, { batteryPercent: 90 }), reading(1, 90, { batteryPercent: 89.9 })]
    expect(availableSeries(pts)).toEqual(['altitudeMeters', 'verticalVelocityMps', 'batteryPercent'])
  })
  it('reports missing position data', () => {
    expect(hasPosition([reading(0, 1)])).toBe(false)
    expect(hasPosition([reading(0, 1, { latitude: 1, longitude: 2 })])).toBe(true)
  })
})

describe('sampleAt', () => {
  const pts = [
    reading(0, 100),
    reading(10, 50, { flightState: 'PARACHUTE_DEPLOYED' }),
    reading(20, 0, { flightState: 'LANDED' }),
  ]
  it('returns null without data', () => {
    expect(sampleAt([], 5)).toBeNull()
  })
  it('interpolates altitude linearly between samples', () => {
    expect(sampleAt(pts, 5)?.altitudeMeters).toBeCloseTo(75)
    expect(sampleAt(pts, 15)?.altitudeMeters).toBeCloseTo(25)
  })
  it('clamps outside the recorded range', () => {
    expect(sampleAt(pts, -5)?.altitudeMeters).toBe(100)
    expect(sampleAt(pts, 99)?.altitudeMeters).toBe(0)
    expect(sampleAt(pts, 99)?.elapsedTimeSeconds).toBe(20)
  })
  it('only shows the parachute once the recorded state says so', () => {
    expect(sampleAt(pts, 5)?.parachuteDeployed).toBe(false)
    expect(sampleAt(pts, 12)?.parachuteDeployed).toBe(true)
    expect(sampleAt(pts, 20)?.parachuteDeployed).toBe(true)
  })
})
