/// <reference types="vite/client" />
declare const __APP_VERSION__: string
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_API_TIMEOUT_MS?: string
  readonly VITE_SIMULATION_START_TIMEOUT_MS?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
