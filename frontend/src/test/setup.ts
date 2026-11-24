import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
// Recharts' ResponsiveContainer needs ResizeObserver, which jsdom lacks.
;(globalThis as unknown as { ResizeObserver: typeof ResizeObserverStub }).ResizeObserver = ResizeObserverStub

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
})
