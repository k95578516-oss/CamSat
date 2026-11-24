import { useEffect, useRef } from 'react'
import { Button } from './Button'

interface Props {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ open, title, message, confirmLabel, loading, onConfirm, onCancel }: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    cancelRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !loading) onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, loading, onCancel])

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-msg" className="w-full max-w-md rounded-lg border border-line bg-ink-900 p-5 shadow-xl">
        <h2 id="confirm-title" className="text-base font-semibold">{title}</h2>
        <p id="confirm-msg" className="mt-2 text-sm text-muted">{message}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button ref={cancelRef} type="button" onClick={onCancel} disabled={loading} className="rounded-md border border-line bg-ink-700 px-3.5 py-2 text-sm font-medium hover:bg-ink-800 disabled:opacity-50">
            Cancel
          </button>
          <Button variant="danger" onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
