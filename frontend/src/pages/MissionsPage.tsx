import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Pencil, Plus, Rocket, Trash2 } from 'lucide-react'
import { useDeleteMission, useMissions } from '../hooks/queries'
import { toApiError } from '../api/errors'
import { Button } from '../components/ui/Button'
import { Card, PageHeader } from '../components/ui/Card'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { SelectField, TextField } from '../components/ui/Field'
import { EmptyState, ErrorState, LoadingState } from '../components/ui/States'
import { useToast } from '../components/ui/Toast'
import { fmtDateTime, fmtNum } from '../utils/format'
import type { MissionResponse } from '../types/api'

const PAGE_SIZE = 10
type SortKey = 'created-desc' | 'created-asc' | 'name' | 'altitude-desc' | 'mass-desc'

const SORTERS: Record<SortKey, (a: MissionResponse, b: MissionResponse) => number> = {
  'created-desc': (a, b) => b.createdAt.localeCompare(a.createdAt),
  'created-asc': (a, b) => a.createdAt.localeCompare(b.createdAt),
  name: (a, b) => a.name.localeCompare(b.name),
  'altitude-desc': (a, b) => b.releaseAltitudeM - a.releaseAltitudeM,
  'mass-desc': (a, b) => b.payloadMassKg - a.payloadMassKg,
}

export default function MissionsPage() {
  const [input, setInput] = useState('')
  const [search, setSearch] = useState('')
  const [sort, setSort] = useState<SortKey>('created-desc')
  const [page, setPage] = useState(0)
  const [toDelete, setToDelete] = useState<MissionResponse | null>(null)
  const toast = useToast()
  const missions = useMissions(search)
  const remove = useDeleteMission()

  // Search is applied by the backend (?search=), debounced.
  useEffect(() => {
    const id = window.setTimeout(() => {
      setSearch(input.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(id)
  }, [input])

  const sorted = useMemo(() => [...(missions.data ?? [])].sort(SORTERS[sort]), [missions.data, sort])
  const pages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const visible = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  const confirmDelete = async () => {
    if (!toDelete) return
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Deleted mission “${toDelete.name}”.`)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setToDelete(null)
    }
  }

  return (
    <>
      <PageHeader
        title="Missions"
        subtitle="Mission configurations. A mission that already has simulation runs cannot be edited or deleted."
        actions={
          <Link to="/missions/new" className="inline-flex items-center gap-2 rounded-md bg-accent px-3.5 py-2 text-sm font-medium text-ink-950 hover:bg-cyan-300">
            <Plus className="h-4 w-4" aria-hidden /> New mission
          </Link>
        }
      />
      <Card>
        <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_220px]">
          <TextField label="Search by name" type="search" value={input} onChange={(e) => setInput(e.target.value)} placeholder="e.g. Alpha" />
          <SelectField label="Sort by" value={sort} onChange={(e) => { setSort(e.target.value as SortKey); setPage(0) }}>
            <option value="created-desc">Newest first</option>
            <option value="created-asc">Oldest first</option>
            <option value="name">Name</option>
            <option value="altitude-desc">Release altitude (high → low)</option>
            <option value="mass-desc">Payload mass (high → low)</option>
          </SelectField>
        </div>

        {missions.isLoading && <LoadingState label="Loading missions…" />}
        {missions.isError && <ErrorState error={missions.error} onRetry={() => void missions.refetch()} title="Could not load missions" />}
        {missions.data && sorted.length === 0 && (
          <EmptyState
            title={search ? `No missions match “${search}”` : 'No missions yet'}
            description={search ? 'Try a different search term.' : 'Create your first mission to configure a CanSat descent.'}
            action={!search ? <Link to="/missions/new" className="text-sm text-accent hover:underline">Create a mission</Link> : undefined}
          />
        )}
        {visible.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="text-xs text-muted">
                  <tr>
                    <th scope="col" className="py-1.5 pr-3">Name</th>
                    <th scope="col" className="py-1.5 pr-3">Release altitude</th>
                    <th scope="col" className="py-1.5 pr-3">Payload</th>
                    <th scope="col" className="py-1.5 pr-3">Wind</th>
                    <th scope="col" className="py-1.5 pr-3">Created</th>
                    <th scope="col" className="py-1.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map((m) => (
                    <tr key={m.id} className="border-t border-line">
                      <td className="py-2 pr-3"><Link to={`/missions/${m.id}`} className="font-medium text-accent hover:underline">{m.name}</Link></td>
                      <td className="num py-2 pr-3">{fmtNum(m.releaseAltitudeM, 0, 'm')}</td>
                      <td className="num py-2 pr-3">{fmtNum(m.payloadMassKg, 2, 'kg')}</td>
                      <td className="num py-2 pr-3">{fmtNum(m.windSpeedMps, 1, 'm/s')} @ {m.windDirectionDeg}°</td>
                      <td className="py-2 pr-3 text-xs text-muted">{fmtDateTime(m.createdAt)}</td>
                      <td className="py-2">
                        <div className="flex justify-end gap-1">
                          <Link aria-label={`Start a simulation for ${m.name}`} title="Simulation" to={`/simulation?mission=${m.id}`} className="rounded p-2 text-muted hover:bg-ink-800 hover:text-accent"><Rocket className="h-4 w-4" /></Link>
                          <Link aria-label={`Edit ${m.name}`} title="Edit" to={`/missions/${m.id}/edit`} className="rounded p-2 text-muted hover:bg-ink-800 hover:text-fg"><Pencil className="h-4 w-4" /></Link>
                          <button type="button" aria-label={`Delete ${m.name}`} title="Delete" onClick={() => setToDelete(m)} className="rounded p-2 text-muted hover:bg-ink-800 hover:text-crit"><Trash2 className="h-4 w-4" /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted">
              <span>{sorted.length} mission{sorted.length === 1 ? '' : 's'}</span>
              <div className="flex items-center gap-2">
                <Button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>Previous</Button>
                <span>Page {page + 1} of {pages}</span>
                <Button onClick={() => setPage((p) => Math.min(pages - 1, p + 1))} disabled={page >= pages - 1}>Next</Button>
              </div>
            </div>
          </>
        )}
      </Card>
      <ConfirmDialog
        open={toDelete !== null}
        title="Delete mission?"
        message={`“${toDelete?.name ?? ''}” will be permanently deleted. The backend refuses this if the mission has simulation history.`}
        confirmLabel="Delete mission"
        loading={remove.isPending}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setToDelete(null)}
      />
    </>
  )
}
