import { Loader2 } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const VARIANT: Record<Variant, string> = {
  primary: 'bg-accent text-ink-950 hover:bg-cyan-300 disabled:hover:bg-accent',
  secondary: 'bg-ink-700 text-fg border border-line hover:bg-ink-800',
  danger: 'bg-crit/90 text-ink-950 hover:bg-crit',
  ghost: 'text-muted hover:text-fg hover:bg-ink-800',
}

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  loading?: boolean
  icon?: ReactNode
}

export function Button({ variant = 'secondary', loading, icon, children, disabled, className = '', ...rest }: Props) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT[variant]} ${className}`}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  )
}
