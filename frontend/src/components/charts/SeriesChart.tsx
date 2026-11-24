import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SeriesInfo } from '../../utils/telemetry'
import type { TelemetryReadingResponse } from '../../types/api'
import { fmtNum } from '../../utils/format'

export interface ChartMarker {
  x: number
  label: string
}

interface Props {
  info: SeriesInfo
  data: TelemetryReadingResponse[]
  markers?: ChartMarker[]
  height?: number
}

/** One chart per sensor so each keeps its own unit and scale. Plots only recorded samples. */
export function SeriesChart({ info, data, markers = [], height = 220 }: Props) {
  return (
    <figure aria-label={`${info.label} versus elapsed time`} className="m-0">
      <figcaption className="mb-1 flex items-baseline justify-between text-sm">
        <span className="font-medium">{info.label}</span>
        <span className="text-xs text-muted">{info.unit}</span>
      </figcaption>
      <div style={{ width: '100%', height }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 0 }}>
            <CartesianGrid stroke="#22304a" strokeDasharray="3 3" />
            <XAxis
              dataKey="elapsedTimeSeconds"
              type="number"
              domain={['dataMin', 'dataMax']}
              tick={{ fill: '#8fa0bc', fontSize: 11 }}
              stroke="#22304a"
              tickFormatter={(v: number) => `${Math.round(v)}`}
              label={{ value: 'Elapsed time (s)', position: 'insideBottom', offset: -2, fill: '#8fa0bc', fontSize: 11 }}
              height={36}
            />
            <YAxis tick={{ fill: '#8fa0bc', fontSize: 11 }} stroke="#22304a" width={52} domain={['auto', 'auto']} />
            <Tooltip
              contentStyle={{ background: '#101a2e', border: '1px solid #22304a', borderRadius: 6, fontSize: 12 }}
              labelFormatter={(l) => `t = ${fmtNum(Number(l), 1)} s`}
              formatter={(v) => [`${fmtNum(Number(v), 2)} ${info.unit}`, info.label]}
            />
            {markers.map((m) => (
              <ReferenceLine key={m.label} x={m.x} stroke="#fbbf24" strokeDasharray="4 3" label={{ value: m.label, fill: '#fbbf24', fontSize: 11, position: 'insideTopRight' }} />
            ))}
            <Line
              type="monotone"
              dataKey={info.key}
              stroke={info.color}
              strokeWidth={2}
              dot={false}
              connectNulls={false}
              isAnimationActive={false}
              name={info.label}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}
