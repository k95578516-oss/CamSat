import { useEffect, useState } from 'react'
import type { TelemetryReadingResponse } from '../../types/api'
import { fmtNum } from '../../utils/format'
import { flightStateLabel } from '../../utils/labels'
import { Button } from '../ui/Button'

const PAGE_SIZE = 50

export function TelemetryTable({ rows, showPosition }: { rows: TelemetryReadingResponse[]; showPosition: boolean }) {
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE))

  useEffect(() => setPage(0), [rows])

  const slice = rows.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE)
  return (
    <div>
      <div className="overflow-x-auto rounded-md border border-line">
        <table className="w-full min-w-[640px] text-left text-sm">
          <caption className="sr-only">Recorded telemetry readings</caption>
          <thead className="bg-ink-800 text-xs text-muted">
            <tr>
              <th scope="col" className="px-3 py-2">Time (s)</th>
              <th scope="col" className="px-3 py-2">Altitude (m)</th>
              <th scope="col" className="px-3 py-2">Vertical velocity (m/s)</th>
              <th scope="col" className="px-3 py-2">Temperature (°C)</th>
              <th scope="col" className="px-3 py-2">Battery (%)</th>
              {showPosition && <th scope="col" className="px-3 py-2">Latitude / longitude (°)</th>}
              <th scope="col" className="px-3 py-2">Flight state</th>
            </tr>
          </thead>
          <tbody>
            {slice.map((r) => (
              <tr key={r.id} className="border-t border-line">
                <td className="num px-3 py-1.5">{fmtNum(r.elapsedTimeSeconds, 1)}</td>
                <td className="num px-3 py-1.5">{fmtNum(r.altitudeMeters, 2)}</td>
                <td className="num px-3 py-1.5">{fmtNum(r.verticalVelocityMps, 2)}</td>
                <td className="num px-3 py-1.5">{fmtNum(r.temperatureCelsius, 2)}</td>
                <td className="num px-3 py-1.5">{fmtNum(r.batteryPercent, 2)}</td>
                {showPosition && (
                  <td className="num px-3 py-1.5">
                    {r.latitude !== null && r.longitude !== null ? `${fmtNum(r.latitude, 5)}, ${fmtNum(r.longitude, 5)}` : '—'}
                  </td>
                )}
                <td className="px-3 py-1.5">{flightStateLabel(r.flightState)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center justify-between text-xs text-muted">
        <span>
          Rows {rows.length === 0 ? 0 : page * PAGE_SIZE + 1}–{Math.min(rows.length, (page + 1) * PAGE_SIZE)} of {rows.length}
        </span>
        <div className="flex items-center gap-2">
          <Button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
            Previous
          </Button>
          <span>
            Page {page + 1} of {pages}
          </span>
          <Button onClick={() => setPage((p) => Math.min(pages - 1, p + 1))} disabled={page >= pages - 1}>
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
