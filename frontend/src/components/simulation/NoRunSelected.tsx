import { Link } from 'react-router-dom'
import { EmptyState } from '../ui/States'

export function NoRunSelected({ missionSelected }: { missionSelected: boolean }) {
  return (
    <EmptyState
      title={missionSelected ? 'Select a simulation run' : 'Select a mission and a run'}
      description={
        missionSelected
          ? 'Choose a run above. If the mission has no runs yet, start one from the Simulation page.'
          : 'Pick a mission, then one of its simulation runs, to see recorded data.'
      }
      action={
        <Link to="/simulation" className="text-sm text-accent underline-offset-2 hover:underline">
          Go to Simulation
        </Link>
      }
    />
  )
}
