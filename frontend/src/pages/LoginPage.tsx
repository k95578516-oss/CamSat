import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { SatelliteDish } from 'lucide-react'
import { useAuth } from '../features/auth/AuthContext'
import { toApiError } from '../api/errors'
import { Button } from '../components/ui/Button'
import { TextField } from '../components/ui/Field'
import { Notice } from '../components/ui/States'

const schema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
})
type Values = z.infer<typeof schema>

export default function LoginPage() {
  const { login, isAuthenticated, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [error, setError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  if (isAuthenticated) return <Navigate to={from} replace />

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      await login(values)
      navigate(from, { replace: true })
    } catch (e) {
      const err = toApiError(e)
      setError(err.kind === 'unauthorized' ? 'Email or password is incorrect.' : err.message)
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-lg border border-line bg-ink-900 p-6">
        <div className="mb-5 flex items-center gap-2">
          <SatelliteDish className="h-6 w-6 text-accent" aria-hidden />
          <div>
            <h1 className="text-lg font-semibold leading-tight">CamSat ground station</h1>
            <p className="text-xs text-muted">Sign in to manage missions and review simulations</p>
          </div>
        </div>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {sessionExpired && !error && <Notice tone="warn">Your session has ended. Sign in again to continue.</Notice>}
          {error && <Notice tone="crit">{error}</Notice>}
          <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
          <TextField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
          <Button type="submit" variant="primary" loading={isSubmitting} className="w-full">
            Sign in
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-muted">
          No account yet?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}
