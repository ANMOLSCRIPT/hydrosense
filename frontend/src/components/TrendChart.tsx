import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ReferenceArea, ReferenceDot, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { RangeKey, SeriesPoint } from '../lib/api'
import { Skeleton } from './ui'

const SERIES = '#0d7c96'
const INK_MUTED = '#6b7c8c'

interface Props {
  points: SeriesPoint[] | undefined
  range: RangeKey
  baseline?: number | null
  /** Citizen view: shade the usual range instead of drawing technical markers. */
  usualBand?: boolean
  anomalies?: { t: string; tds: number }[]
  height?: number
  loading?: boolean
}

function tickLabel(ts: number, range: RangeKey) {
  const d = new Date(ts)
  return range === '24h'
    ? d.toLocaleTimeString(undefined, { hour: 'numeric' })
    : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

function Tip({ active, payload, baseline }: { active?: boolean; payload?: { payload: { ts: number; tds: number } }[]; baseline?: number | null }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  const diff = baseline ? ((p.tds - baseline) / baseline) * 100 : null
  return (
    <div className="rounded-xl border border-line bg-white px-3 py-2 shadow-lift">
      <p className="tabular text-base font-extrabold text-navy-900">{Math.round(p.tds)} <span className="text-xs font-semibold text-ink-muted">ppm</span></p>
      {diff != null && Math.abs(diff) >= 3 && (
        <p className="text-xs font-semibold text-ink-soft">{Math.abs(diff).toFixed(0)}% {diff > 0 ? 'above' : 'below'} usual</p>
      )}
      <p className="mt-0.5 text-xs text-ink-muted">{new Date(p.ts).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}</p>
    </div>
  )
}

export default function TrendChart({ points, range, baseline, usualBand, anomalies, height = 260, loading }: Props) {
  const data = useMemo(() => (points ?? []).map((p) => ({ ts: new Date(p.t).getTime(), tds: p.tds })), [points])
  const domain = useMemo(() => {
    const values = data.map((d) => d.tds)
    if (baseline) values.push(baseline * 0.9, baseline * 1.1)
    if (!values.length) return [0, 100]
    const lo = Math.min(...values), hi = Math.max(...values)
    const pad = Math.max((hi - lo) * 0.15, 8)
    return [Math.max(0, Math.floor((lo - pad) / 10) * 10), Math.ceil((hi + pad) / 10) * 10]
  }, [data, baseline])

  if (loading) return <Skeleton className="w-full" />
  if (data.length < 2) {
    return (
      <div className="grid place-items-center rounded-xl bg-mist px-6 text-center text-sm text-ink-muted" style={{ height }}>
        Not enough readings in this period yet. They will appear here as the sensor reports.
      </div>
    )
  }
  const from = data[0].ts, to = data[data.length - 1].ts
  const marks = (anomalies ?? []).map((a) => ({ ts: new Date(a.t).getTime(), tds: a.tds })).filter((a) => a.ts >= from && a.ts <= to).slice(-80)

  return (
    <figure className="m-0">
      <div style={{ height }} role="img" aria-label={`Chart of dissolved solids over the last ${range === '24h' ? '24 hours' : range === '7d' ? '7 days' : '30 days'}. Latest value ${Math.round(data[data.length - 1].tds)} ppm${baseline ? `, usual level ${Math.round(baseline)} ppm` : ''}.`}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="#e6edf2" />
            <XAxis dataKey="ts" type="number" scale="time" domain={[from, to]} tickFormatter={(t) => tickLabel(t, range)}
              tick={{ fontSize: 11, fill: INK_MUTED }} tickLine={false} axisLine={{ stroke: '#c9d6df' }} minTickGap={48} />
            <YAxis domain={domain} width={44} tick={{ fontSize: 11, fill: INK_MUTED }} tickLine={false} axisLine={false} unit="" />
            {usualBand && baseline && (
              <ReferenceArea y1={baseline * 0.9} y2={baseline * 1.1} fill="#1c8f5f" fillOpacity={0.09} stroke="none"
                label={{ value: 'Usual range', position: 'insideTopLeft', fontSize: 11, fill: '#1c8f5f', fontWeight: 700 }} />
            )}
            {!usualBand && baseline && (
              <ReferenceLine y={baseline} stroke={INK_MUTED} strokeDasharray="5 4"
                label={{ value: `Baseline ${Math.round(baseline)}`, position: 'insideTopLeft', fontSize: 11, fill: INK_MUTED, fontWeight: 600 }} />
            )}
            <Tooltip content={<Tip baseline={baseline} />} cursor={{ stroke: '#8fa5b5', strokeWidth: 1 }} />
            <Area type="monotone" dataKey="tds" stroke={SERIES} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"
              fill={SERIES} fillOpacity={0.1} dot={false} activeDot={{ r: 5, stroke: '#fff', strokeWidth: 2, fill: SERIES }} animationDuration={600} />
            {marks.map((m) => <ReferenceDot key={m.ts} x={m.ts} y={m.tds} r={4} fill="#d9601f" stroke="#fff" strokeWidth={2} />)}
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-muted">
        <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 rounded bg-aqua-600" /> Dissolved solids (TDS, ppm)</span>
        {usualBand && baseline && <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-ok/15" /> What's usual here</span>}
        {!usualBand && baseline && <span className="inline-flex items-center gap-1.5"><span className="w-4 border-t-2 border-dashed border-ink-muted" /> Historical baseline</span>}
        {marks.length > 0 && <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-change ring-2 ring-white" /> Outside usual range</span>}
      </figcaption>
    </figure>
  )
}

/** Tiny trend line for cards and tables: no axes, just the shape. */
export function Sparkline({ points, width = 120, height = 36, color = SERIES }: { points: SeriesPoint[] | undefined; width?: number; height?: number; color?: string }) {
  if (!points || points.length < 2) return <div style={{ width, height }} className="rounded bg-mist" aria-hidden />
  const ys = points.map((p) => p.tds)
  const lo = Math.min(...ys), hi = Math.max(...ys), span = hi - lo || 1
  const d = ys.map((y, i) => `${i ? 'L' : 'M'}${((i / (ys.length - 1)) * (width - 6) + 3).toFixed(1)},${(height - 4 - ((y - lo) / span) * (height - 8)).toFixed(1)}`).join(' ')
  const lastY = height - 4 - ((ys[ys.length - 1] - lo) / span) * (height - 8)
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Recent trend">
      <path d={d} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={width - 3} cy={lastY} r="3" fill={color} stroke="#fff" strokeWidth="1.5" />
    </svg>
  )
}
