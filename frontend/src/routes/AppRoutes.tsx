import { Suspense, lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { LoadingState } from '../components/ui/States'
import { ProtectedRoute } from './ProtectedRoute'

const LoginPage = lazy(() => import('../pages/LoginPage'))
const RegisterPage = lazy(() => import('../pages/RegisterPage'))
const DashboardPage = lazy(() => import('../pages/DashboardPage'))
const MissionsPage = lazy(() => import('../pages/MissionsPage'))
const MissionDetailPage = lazy(() => import('../pages/MissionDetailPage'))
const CreateMissionPage = lazy(() => import('../pages/MissionFormPage').then((m) => ({ default: m.CreateMissionPage })))
const EditMissionPage = lazy(() => import('../pages/MissionFormPage').then((m) => ({ default: m.EditMissionPage })))
const SimulationPage = lazy(() => import('../pages/SimulationPage'))
const TelemetryPage = lazy(() => import('../pages/TelemetryPage'))
const VisualizationPage = lazy(() => import('../pages/VisualizationPage'))
const EventsPage = lazy(() => import('../pages/EventsPage'))
const RecoveryPage = lazy(() => import('../pages/RecoveryPage'))
const ReportsPage = lazy(() => import('../pages/ReportsPage'))
const SettingsPage = lazy(() => import('../pages/SettingsPage'))
const ForbiddenPage = lazy(() => import('../pages/ForbiddenPage'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'))

export function AppRoutes() {
  return (
    <Suspense fallback={<LoadingState label="Loading…" />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="missions" element={<MissionsPage />} />
            <Route path="missions/new" element={<CreateMissionPage />} />
            <Route path="missions/:missionId" element={<MissionDetailPage />} />
            <Route path="missions/:missionId/edit" element={<EditMissionPage />} />
            <Route path="simulation" element={<SimulationPage />} />
            <Route path="telemetry" element={<TelemetryPage />} />
            <Route path="visualization" element={<VisualizationPage />} />
            <Route path="events" element={<EventsPage />} />
            <Route path="recovery" element={<RecoveryPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="forbidden" element={<ForbiddenPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  )
}
