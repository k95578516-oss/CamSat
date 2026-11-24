import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { SatelliteDish } from 'lucide-react'
import { useAuth } from '../features/auth/AuthContext'
import { toApiError } from '../api/errors'
import { Button } from '../components/ui/Button'
import { TextField } from '../components/ui/Field'
import { Notice } from '../components/ui/States'

/** Constraints mirror the backend RegisterRequest. */
const schema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters'),
    email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address').max(190, 'Email is too long'),
    password: z.string().min(8, 'Password must be between 8 and 72 characters').max(72, 'Password must be between 8 and 72 characters'),
    confirm: z.string().min(1, 'Repeat the password'),
  })
  .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })
type Values = z.infer<typeof schema>

export default function RegisterPage() {
  const { register: signUp, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', email: '', password: '', confirm: '' } })

  if (isAuthenticated) return <Navigate to="/" replace />

  const onSubmit = handleSubmit(async ({ name, email, password }) => {
    setError(null)
    try {
      await signUp({ name, email, password })
      navigate('/', { replace: true })
    } catch (e) {
      const err = toApiError(e)
      let mapped = false
      for (const [field, message] of Object.entries(err.fieldErrors)) {
        if (field === 'name' || field === 'email' || field === 'password') {
          setFieldError(field, { type: 'server', message })
          mapped = true
        }
      }
      setError(err.kind === 'conflict' ? 'An account with this email already exists.' : mapped ? 'Fix the highlighted fields and try again.' : err.message)
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-lg border border-line bg-ink-900 p-6">
        <div className="mb-5 flex items-center gap-2">
          <SatelliteDish className="h-6 w-6 text-accent" aria-hidden />
          <h1 className="text-lg font-semibold">Create an account</h1>
        </div>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {error && <Notice tone="crit">{error}</Notice>}
          <TextField label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
          <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
          <TextField label="Password" type="password" autoComplete="new-password" hint="8 to 72 characters" error={errors.password?.message} {...register('password')} />
          <TextField label="Repeat password" type="password" autoComplete="new-password" error={errors.confirm?.message} {...register('confirm')} />
          <Button type="submit" variant="primary" loading={isSubmitting} className="w-full">
            Create account
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-muted">
          Already registered?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
