import type { ReactNode } from 'react'

interface CardProps {
  title?: string
  description?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}

export function Card({ title, description, actions, children, className = '' }: CardProps) {
  return (
    <section className={`rounded-lg border border-line bg-ink-900 ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-2 border-b border-line px-4 py-3">
          <div>
            {title && <h2 className="text-sm font-semibold text-fg">{title}</h2>}
            {description && <p className="mt-0.5 text-xs text-muted">{description}</p>}
          </div>
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2 print:hidden">{actions}</div>}
    </div>
  )
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-lg border border-line bg-ink-900 px-4 py-3">
      <div className="text-xs text-muted">{label}</div>
      <div className="num mt-1 text-2xl font-semibold text-fg">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
    </div>
  )
}
