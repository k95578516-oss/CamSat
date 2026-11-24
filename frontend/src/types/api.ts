/**
 * Types mirror the backend DTO records and enums in com.example.camsat.
 * They are intentionally isolated here: after backend remediation, update this file
 * (and the adapters in src/api) rather than the pages.
 * ISO-8601 instants are transported as strings.
 */
export type SimulationStatus = 'CREATED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type FlightState =
  | 'READY'
  | 'ASCENT'
  | 'DESCENT'
  | 'PARACHUTE_DEPLOYED'
  | 'LANDED'
  | 'RECOVERY'
  | 'ABORTED'

export type FlightEventType =
  | 'SIMULATION_STARTED'
  | 'DESCENT_STARTED'
  | 'PARACHUTE_DEPLOYED'
  | 'LOW_BATTERY'
  | 'LANDING_DETECTED'
  | 'RECOVERY_STARTED'
  | 'RECOVERY_COMPLETED'
  | 'SIMULATION_PAUSED'
  | 'SIMULATION_RESUMED'
  | 'SIMULATION_COMPLETED'
  | 'SIMULATION_FAILED'
  | 'SIMULATION_CANCELLED'

export interface RegisterRequest {
  name: string
  email: string
  password: string
}
export interface LoginRequest {
  email: string
  password: string
}
export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  userId: number
  name: string
  email: string
  role: string
  createdAt: string
}

/** Units: kg, m, m, (dimensionless), m/s, degrees, percent. */
export interface MissionRequest {
  name: string
  payloadMassKg: number
  releaseAltitudeM: number
  parachuteDiameterM: number
  dragCoefficient: number
  windSpeedMps: number
  windDirectionDeg: number
  initialBatteryPercent: number
}
export interface MissionResponse extends MissionRequest {
  id: number
  createdAt: string
  updatedAt: string
}

export interface SimulationRunResponse {
  id: number
  missionId: number
  status: SimulationStatus
  startedAt: string | null
  endedAt: string | null
  landingTimeSeconds: number | null
  landingDriftM: number | null
  finalResult: string | null
  /** Additive (backend with async runs): simulated seconds so far and estimated progress, 0-100. */
  elapsedSimTimeSeconds?: number | null
  progressPercent?: number | null
}

export interface TelemetryReadingResponse {
  id: number
  runId: number
  elapsedTimeSeconds: number
  altitudeMeters: number
  verticalVelocityMps: number
  temperatureCelsius: number | null
  batteryPercent: number | null
  latitude: number | null
  longitude: number | null
  flightState: FlightState
  recordedAt: string
}

export interface FlightEventResponse {
  id: number
  runId: number
  eventType: FlightEventType
  message: string
  elapsedTimeSeconds: number
  createdAt: string
}

export interface RecoveryResponse {
  runId: number
  missionId: number
  missionName: string
  simulationStatus: SimulationStatus
  /** Free-form string produced by the backend; unknown values are shown as-is. */
  recoveryStatus: string
  landingDetected: boolean
  recoveryStarted: boolean
  recoveryCompleted: boolean
  landingDriftM: number | null
  message: string
  lastEventAt: string | null
}

export interface DashboardRecentRun {
  runId: number
  missionId: number
  missionName: string
  status: SimulationStatus
  startedAt: string | null
  endedAt: string | null
  landingTimeSeconds: number | null
  landingDriftM: number | null
  finalResult: string | null
}
export interface DashboardResponse {
  totalMissions: number
  totalSimulationRuns: number
  completedRuns: number
  failedRuns: number
  activeRuns: number
  createdRuns: number
  totalTelemetryReadings: number
  runsByStatus: Partial<Record<SimulationStatus, number>>
  recentRuns: DashboardRecentRun[]
}

export interface AnalyticsResponse {
  totalRuns: number
  completedRuns: number
  failedRuns: number
  successRatePercent: number
  averageLandingTimeSeconds: number | null
  averageLandingDriftMeters: number | null
  averageMaximumDescentSpeedMps: number | null
}

export interface MissionReportEvent {
  id: number
  eventType: FlightEventType
  message: string
  elapsedTimeSeconds: number
  createdAt: string
}
export interface MissionReportResponse {
  runId: number
  missionId: number
  missionName: string
  status: SimulationStatus
  startedAt: string | null
  endedAt: string | null
  landingTimeSeconds: number | null
  landingDriftM: number | null
  finalResult: string | null
  releaseAltitudeM: number
  payloadMassKg: number
  parachuteDiameterM: number
  windSpeedMps: number
  telemetryReadingCount: number
  minimumBatteryPercent: number | null
  maximumAltitudeMeters: number | null
  maximumDescentSpeedMps: number | null
  finalFlightState: FlightState | null
  events: MissionReportEvent[]
}

/** Backend error body (GlobalExceptionHandler / ApiErrorResponse). */
export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  fieldErrors?: Record<string, string> | null
}
