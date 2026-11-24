/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { readFileSync } from 'node:fs'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as { version: string }

// The dev proxy is optional. It is only used when VITE_API_BASE_URL is empty
// (same-origin requests such as "/api/..."). See README for details.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.DEV_PROXY_TARGET || 'http://localhost:8080'
  return {
    plugins: [react(), tailwindcss()],
    define: { __APP_VERSION__: JSON.stringify(pkg.version) },
    server: {
      port: 5173,
      proxy: { '/api': { target: proxyTarget, changeOrigin: true } },
    },
    build: {
      sourcemap: false,
      chunkSizeWarningLimit: 900,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      restoreMocks: true,
    },
  }
})
