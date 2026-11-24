const KEY = 'camsat.settings'
export interface UiSettings {
  maxChartPoints: number
}
const DEFAULTS: UiSettings = { maxChartPoints: 1000 }
export const CHART_POINT_OPTIONS = [250, 500, 1000, 2000]

export function loadSettings(): UiSettings {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return DEFAULTS
    const parsed = JSON.parse(raw) as Partial<UiSettings>
    const max = CHART_POINT_OPTIONS.includes(parsed.maxChartPoints as number)
      ? (parsed.maxChartPoints as number)
      : DEFAULTS.maxChartPoints
    return { maxChartPoints: max }
  } catch {
    return DEFAULTS
  }
}
export function saveSettings(s: UiSettings): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* ignore */
  }
}
