import { Link } from 'react-router-dom'

export default function ForbiddenPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-2 p-8 text-center">
      <p className="num text-4xl font-semibold text-warn">403</p>
      <h1 className="text-lg font-semibold">Access denied</h1>
      <p className="max-w-md text-sm text-muted">Your account is not allowed to open this resource. If you expected access, contact the administrator of the backend.</p>
      <Link to="/" className="mt-2 text-sm text-accent hover:underline">Back to overview</Link>
    </div>
  )
}
