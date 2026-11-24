import { describe, expect, it } from 'vitest'
import { fmtDuration, fmtNum, MISSING, wallClockDuration } from './format'

describe('format', () => {
  it('shows missing values as a dash, never as zero', () => {
    expect(fmtNum(null)).toBe(MISSING)
    expect(fmtNum(undefined)).toBe(MISSING)
    expect(fmtNum(Number.NaN)).toBe(MISSING)
    expect(fmtNum(0, 1)).toBe('0.0')
  })
  it('appends units', () => {
    expect(fmtNum(12.345, 1, 'm')).toBe('12.3 m')
  })
  it('formats durations', () => {
    expect(fmtDuration(null)).toBe(MISSING)
    expect(fmtDuration(42.04)).toBe('42.0 s')
    expect(fmtDuration(125)).toBe('2 min 05.0 s')
  })
  it('computes wall-clock duration only when both instants exist', () => {
    expect(wallClockDuration(null, '2026-01-01T00:00:10Z')).toBe(MISSING)
    expect(wallClockDuration('2026-01-01T00:00:00Z', '2026-01-01T00:00:10Z')).toBe('10.0 s')
  })
})
