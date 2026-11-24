import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'

const INPUT =
  'w-full rounded-md border border-line bg-ink-950 px-3 py-2 text-sm text-fg placeholder:text-muted/70 focus:border-accent disabled:opacity-50'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
  unit?: string
}

export const TextField = forwardRef<HTMLInputElement, FieldProps>(function TextField(
  { label, error, hint, unit, className = '', id, ...rest },
  ref,
) {
  const auto = useId()
  const inputId = id ?? auto
  const descId = `${inputId}-desc`
  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1 block text-sm font-medium">
        {label}
        {unit && <span className="ml-1 font-normal text-muted">({unit})</span>}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? descId : undefined}
        className={`${INPUT} ${error ? 'border-crit' : ''}`}
        {...rest}
      />
      {(error || hint) && (
        <p id={descId} className={`mt-1 text-xs ${error ? 'text-crit' : 'text-muted'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
})

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  children: ReactNode
}
export function SelectField({ label, children, className = '', id, ...rest }: SelectProps) {
  const auto = useId()
  const selectId = id ?? auto
  return (
    <div className={className}>
      <label htmlFor={selectId} className="mb-1 block text-sm font-medium">
        {label}
      </label>
      <select id={selectId} className={INPUT} {...rest}>
        {children}
      </select>
    </div>
  )
}
