import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ProtectedRoute } from './ProtectedRoute'
import { fakeSession, renderWithProviders } from '../test/utils'

const tree = (
  <Routes>
    <Route path="/login" element={<div>Login screen</div>} />
    <Route element={<ProtectedRoute />}>
      <Route path="/secret" element={<div>Secret content</div>} />
    </Route>
  </Routes>
)

describe('ProtectedRoute', () => {
  it('redirects anonymous visitors to the login page', () => {
    renderWithProviders(tree, { route: '/secret' })
    expect(screen.getByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument()
  })

  it('renders the page for a signed-in user', () => {
    renderWithProviders(tree, { route: '/secret', session: fakeSession() })
    expect(screen.getByText('Secret content')).toBeInTheDocument()
  })

  it('treats an expired stored session as signed out', () => {
    renderWithProviders(tree, { route: '/secret', session: fakeSession({ expiresAt: Date.now() - 1000 }) })
    expect(screen.getByText('Login screen')).toBeInTheDocument()
  })
})
