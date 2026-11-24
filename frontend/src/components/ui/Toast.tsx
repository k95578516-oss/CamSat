import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { CheckCircle2, Info, XCircle } from 'lucide-react'

type ToastKind = 'success' | 'error' | 'info'
interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}
interface ToastApi {
  success: (m: string) => void
  error: (m: string) => void
  info: (m: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)
let counter = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = ++counter
    setItems((prev) => [...prev.slice(-3), { id, kind, message }])
    window.setTimeout(() => setItems((prev) => prev.filter((t) => t.id !== id)), kind === 'error' ? 8000 : 4500)
  }, [])

  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push('success', m),
      error: (m) => push('error', m),
      info: (m) => push('info', m),
    }),
    [push],
  )

  const icon = { success: CheckCircle2, error: XCircle, info: Info }
  const color = { success: 'text-ok', error: 'text-crit', info: 'text-accent' }

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2 print:hidden">
        {items.map((t) => {
          const Icon = icon[t.kind]
          return (
            <div key={t.id} role={t.kind === 'error' ? 'alert' : 'status'} className="pointer-events-auto flex items-start gap-2 rounded-md border border-line bg-ink-800 px-3 py-2.5 text-sm shadow-lg">
              <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${color[t.kind]}`} aria-hidden />
              <span>{t.message}</span>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
