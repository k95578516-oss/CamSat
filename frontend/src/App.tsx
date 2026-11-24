import { BrowserRouter } from 'react-router-dom'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ToastProvider } from './components/ui/Toast'
import { AuthProvider } from './features/auth/AuthContext'
import { AppRoutes } from './routes/AppRoutes'

export default function App() {
  return (
    <ErrorBoundary>
      <ToastProvider>
        <AuthProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </ErrorBoundary>
  )
}
