import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toApiError } from '../../api/errors'
import type { MissionRequest } from '../../types/api'
import { Button } from '../ui/Button'
import { TextField } from '../ui/Field'
import { Notice } from '../ui/States'

const num = (message = 'Enter a number') => z.number({ invalid_type_error: message, required_error: message })

/** Constraints mirror the backend MissionRequest validation annotations. */
export const missionSchema = z.object({
  name: z.string().trim().min(1, 'Mission name is required').max(100, 'Mission name cannot exceed 100 characters'),
  payloadMassKg: num().min(0.05, 'Payload mass must be at least 0.05 kg').max(50, 'Payload mass cannot exceed 50 kg'),
  releaseAltitudeM: num().min(20, 'Release altitude must be at least 20 m').max(10000, 'Release altitude cannot exceed 10000 m'),
  parachuteDiameterM: num().min(0.1, 'Parachute diameter must be at least 0.1 m').max(3, 'Parachute diameter cannot exceed 3 m'),
  dragCoefficient: num().min(0.1, 'Drag coefficient must be at least 0.1').max(2, 'Drag coefficient cannot exceed 2.0'),
  windSpeedMps: num().min(0, 'Wind speed cannot be negative').max(50, 'Wind speed cannot exceed 50 m/s'),
  windDirectionDeg: num().int('Use a whole number').min(0, 'Wind direction must be between 0 and 359').max(359, 'Wind direction must be between 0 and 359'),
  initialBatteryPercent: num().int('Use a whole number').min(1, 'Battery must be at least 1 %').max(100, 'Battery cannot exceed 100'),
})

export type MissionFormValues = z.infer<typeof missionSchema>

const NUMERIC_FIELDS: { name: Exclude<keyof MissionFormValues, 'name'>; label: string; unit: string; step: string; hint?: string }[] = [
  { name: 'payloadMassKg', label: 'Payload mass', unit: 'kg', step: '0.01' },
  { name: 'releaseAltitudeM', label: 'Release altitude', unit: 'm', step: '1' },
  { name: 'parachuteDiameterM', label: 'Parachute diameter', unit: 'm', step: '0.1' },
  { name: 'dragCoefficient', label: 'Drag coefficient', unit: '0.1 – 2.0', step: '0.05' },
  { name: 'windSpeedMps', label: 'Wind speed', unit: 'm/s', step: '0.1' },
  { name: 'windDirectionDeg', label: 'Wind direction', unit: '° 0–359', step: '1' },
  { name: 'initialBatteryPercent', label: 'Initial battery', unit: '%', step: '1' },
]

interface Props {
  defaultValues?: Partial<MissionRequest>
  submitLabel: string
  onSubmit: (values: MissionRequest) => Promise<unknown>
  onCancel?: () => void
}

export function MissionForm({ defaultValues, submitLabel, onSubmit, onCancel }: Props) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<MissionFormValues>({
    resolver: zodResolver(missionSchema),
    defaultValues: defaultValues as MissionFormValues | undefined,
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(values)
    } catch (e) {
      const err = toApiError(e)
      const known = new Set<string>(['name', ...NUMERIC_FIELDS.map((f) => f.name)])
      let mapped = false
      for (const [field, message] of Object.entries(err.fieldErrors)) {
        if (known.has(field)) {
          setError(field as keyof MissionFormValues, { type: 'server', message })
          mapped = true
        }
      }
      // Input is preserved: react-hook-form keeps every value after a failed submit.
      setFormError(mapped ? 'The backend rejected some values. Fix the highlighted fields and try again.' : err.message)
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {formError && <Notice tone="crit">{formError}</Notice>}
      <TextField label="Mission name" autoComplete="off" error={errors.name?.message} {...register('name')} />
      <div className="grid gap-4 sm:grid-cols-2">
        {NUMERIC_FIELDS.map((f) => (
          <TextField
            key={f.name}
            label={f.label}
            unit={f.unit}
            type="number"
            inputMode="decimal"
            step={f.step}
            error={errors[f.name]?.message}
            {...register(f.name, { valueAsNumber: true })}
          />
        ))}
      </div>
      <div className="flex justify-end gap-2 pt-2">
        {onCancel && (
          <Button variant="ghost" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
        )}
        <Button type="submit" variant="primary" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
