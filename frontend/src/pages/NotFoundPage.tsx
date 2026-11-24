import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-2 p-8 text-center">
      <p className="num text-4xl font-semibold text-accent">404</p>
      <h1 className="text-lg font-semibold">Page not found</h1>
      <p className="text-sm text-muted">This address does not match any page in the ground station.</p>
      <Link to="/" className="mt-2 text-sm text-accent hover:underline">Back to overview</Link>
    </div>
  )
}
