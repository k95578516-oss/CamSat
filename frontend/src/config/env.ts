function num(value: string | undefined, fallback: number): number {
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : fallback
}

export const env = {
  /** Empty string means same-origin requests (Vite proxy in dev, reverse proxy in production). */
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, ''),
  apiTimeoutMs: num(import.meta.env.VITE_API_TIMEOUT_MS, 15_000),
  /** Only relevant for legacy backends: the current backend answers POST /api/runs/{id}/start immediately with 202. */
  simulationStartTimeoutMs: num(import.meta.env.VITE_SIMULATION_START_TIMEOUT_MS, 180_000),
  appVersion: typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev',
}
