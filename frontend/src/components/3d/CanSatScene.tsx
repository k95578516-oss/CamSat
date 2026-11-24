import { useEffect } from 'react'
import { Canvas, useThree } from '@react-three/fiber'
import { Grid, Line, OrbitControls } from '@react-three/drei'
import * as THREE from 'three'
import type { Sample } from '../../utils/telemetry'

export type CameraView = 'orbit' | 'side' | 'top'

/** Scene units: the altitude axis is scaled so the highest recorded altitude is SCENE_HEIGHT units tall. */
const SCENE_HEIGHT = 20
const CENTER_Y = SCENE_HEIGHT / 2

const VIEWS: Record<CameraView, [number, number, number]> = {
  orbit: [9, 11, 15],
  side: [0, CENTER_Y, 26],
  top: [0.001, 38, 0.001],
}

interface ControlsLike {
  target: THREE.Vector3
  update: () => void
}

function CameraRig({ view }: { view: CameraView }) {
  const { camera, controls, invalidate } = useThree()
  useEffect(() => {
    const [x, y, z] = VIEWS[view]
    camera.position.set(x, y, z)
    const c = controls as unknown as ControlsLike | null
    if (c) {
      c.target.set(0, CENTER_Y, 0)
      c.update()
    } else {
      camera.lookAt(0, CENTER_Y, 0)
    }
    invalidate()
  }, [view, camera, controls, invalidate])
  return null
}

function CanSatModel({ deployed }: { deployed: boolean }) {
  const rim = 1.7
  const lineTop: [number, number, number] = [0, 0.75, 0]
  const rimPoints = [0, 1, 2, 3].map((i) => {
    const a = (i * Math.PI) / 2
    return [Math.cos(a) * rim, 3.0, Math.sin(a) * rim] as [number, number, number]
  })
  return (
    <group>
      {/* body */}
      <mesh castShadow>
        <cylinderGeometry args={[0.35, 0.35, 1.1, 32]} />
        <meshStandardMaterial color="#cbd5e1" metalness={0.5} roughness={0.35} />
      </mesh>
      <mesh position={[0, 0.2, 0]}>
        <cylinderGeometry args={[0.36, 0.36, 0.12, 32]} />
        <meshStandardMaterial color="#22d3ee" emissive="#0e7490" emissiveIntensity={0.4} />
      </mesh>
      <mesh position={[0, 0.58, 0]}>
        <cylinderGeometry args={[0.28, 0.35, 0.08, 32]} />
        <meshStandardMaterial color="#94a3b8" metalness={0.6} roughness={0.4} />
      </mesh>
      {/* antenna */}
      <mesh position={[0, 0.95, 0]}>
        <cylinderGeometry args={[0.02, 0.02, 0.7, 8]} />
        <meshStandardMaterial color="#e2e8f0" />
      </mesh>
      <mesh position={[0, 1.32, 0]}>
        <sphereGeometry args={[0.05, 12, 12]} />
        <meshStandardMaterial color="#f87171" emissive="#f87171" emissiveIntensity={0.6} />
      </mesh>
      {/* parachute, drawn only when the recorded flight state says it is deployed */}
      {deployed && (
        <group>
          <mesh position={[0, 3.0, 0]}>
            <sphereGeometry args={[rim, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2]} />
            <meshStandardMaterial color="#22d3ee" side={THREE.DoubleSide} transparent opacity={0.75} />
          </mesh>
          {rimPoints.map((p, i) => (
            <Line key={i} points={[lineTop, p]} color="#8fa0bc" lineWidth={1} />
          ))}
        </group>
      )}
    </group>
  )
}

interface Props {
  sample: Sample | null
  maxAltitude: number
  view: CameraView
}

export default function CanSatScene({ sample, maxAltitude, view }: Props) {
  const alt = sample ? sample.altitudeMeters : maxAltitude
  const y = maxAltitude > 0 ? (Math.max(0, alt) / maxAltitude) * SCENE_HEIGHT : 0
  const deployed = sample?.parachuteDeployed ?? false

  return (
    <Canvas frameloop="demand" dpr={[1, 2]} camera={{ fov: 45, near: 0.1, far: 300, position: VIEWS.orbit }} aria-label="3D altitude visualization of the CanSat">
      <color attach="background" args={['#060a13']} />
      <fog attach="fog" args={['#060a13', 40, 110]} />
      <ambientLight intensity={0.7} />
      <directionalLight position={[10, 20, 10]} intensity={1.4} />
      <Grid args={[80, 80]} position={[0, 0, 0]} cellSize={2} sectionSize={10} cellColor="#18253f" sectionColor="#0e7490" fadeDistance={70} infiniteGrid />

      {/* altitude ruler: 0 .. max recorded altitude */}
      <Line points={[[-4, 0, -4], [-4, SCENE_HEIGHT, -4]]} color="#22d3ee" lineWidth={1} />
      {[0, 0.25, 0.5, 0.75, 1].map((f) => (
        <Line key={f} points={[[-4, f * SCENE_HEIGHT, -4], [-3.4, f * SCENE_HEIGHT, -4]]} color="#22d3ee" lineWidth={1} />
      ))}

      <group position={[0, y + 0.55, 0]}>
        <CanSatModel deployed={deployed} />
      </group>

      <CameraRig view={view} />
      <OrbitControls makeDefault enablePan={false} minDistance={6} maxDistance={70} maxPolarAngle={Math.PI / 2 - 0.02} />
    </Canvas>
  )
}
