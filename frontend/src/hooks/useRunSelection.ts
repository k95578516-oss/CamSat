import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

function parseId(v: string | null): number | null {
  if (!v || !/^\d+$/.test(v)) return null
  const n = Number(v)
  return Number.isSafeInteger(n) ? n : null
}

/** Mission and run selection lives in the URL (?mission=1&run=2) so views are linkable. */
export function useRunSelection() {
  const [params, setParams] = useSearchParams()
  const missionId = parseId(params.get('mission'))
  const runId = parseId(params.get('run'))

  const setMission = useCallback(
    (id: number | null) =>
      setParams((p) => {
        const next = new URLSearchParams(p)
        if (id === null) next.delete('mission')
        else next.set('mission', String(id))
        next.delete('run')
        return next
      }),
    [setParams],
  )
  const setRun = useCallback(
    (id: number | null) =>
      setParams((p) => {
        const next = new URLSearchParams(p)
        if (id === null) next.delete('run')
        else next.set('run', String(id))
        return next
      }),
    [setParams],
  )
  return { missionId, runId, setMission, setRun }
}
