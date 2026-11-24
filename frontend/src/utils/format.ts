export const MISSING = '—'

/** Formats a number with fixed decimals; null/undefined/NaN are shown as an em dash, never as 0. */
export function fmtNum(value: number | null | undefined, digits = 1, unit?: string): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return MISSING
  const s = value.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  return unit ? `${s} ${unit}` : s
}

export function fmtInt(value: number | null | undefined): string {
  return fmtNum(value, 0)
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return MISSING
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return MISSING
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' })
}

/** Seconds -> "1 min 05.0 s" style duration. */
export function fmtDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds) || seconds < 0) return MISSING
  if (seconds < 60) return `${seconds.toFixed(1)} s`
  const m = Math.floor(seconds / 60)
  const s = seconds - m * 60
  return `${m} min ${s.toFixed(1).padStart(4, '0')} s`
}

/** Duration between two ISO instants, or MISSING when either is absent. */
export function wallClockDuration(start: string | null, end: string | null): string {
  if (!start || !end) return MISSING
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (!Number.isFinite(ms) || ms < 0) return MISSING
  return fmtDuration(ms / 1000)
}
