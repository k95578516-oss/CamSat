import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Client-side gate only (UX). The backend enforces real authorization on every request.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
