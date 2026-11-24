import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { ApiError } from '../api/errors'
import { authApi } from '../api/authApi'
import { renderWithProviders } from '../test/utils'

vi.mock('../api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn() } }))

const tree = (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/" element={<div>Overview page</div>} />
  </Routes>
)

beforeEach(() => vi.mocked(authApi.login).mockReset())

describe('LoginPage', () => {
  it('validates the form before calling the API', async () => {
    renderWithProviders(tree, { route: '/login' })
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('signs in with the real response shape and opens the overview', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 3600, userId: 7,
      name: 'Ada', email: 'ada@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z',
    })
    renderWithProviders(tree, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'secret-pass')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText('Overview page')).toBeInTheDocument()
    expect(authApi.login).toHaveBeenCalledWith({ email: 'ada@example.com', password: 'secret-pass' })
    expect(JSON.parse(sessionStorage.getItem('camsat.session')!).token).toBe('jwt')
  })

  it('shows a clear message for wrong credentials and keeps the email', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError('unauthorized', 'raw', 401))
    renderWithProviders(tree, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com')
  })

  it('shows network failures', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError('network', 'Cannot reach the CamSat backend.'))
    renderWithProviders(tree, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/cannot reach/i))
  })
})
