import type { ReactNode } from 'react'
import type { Site } from '../lib/api'
import { Skeleton } from './ui'

export function Kpi({ label, value, unit, hint, tone = 'text-navy-900', loading }: { label: string; value: ReactNode; unit?: string; hint?: ReactNode; tone?: string; loading?: boolean }) {
  return (
    <div className="card p-4 sm:p-5">
      <p className="text-xs font-bold tracking-wide text-ink-muted uppercase">{label}</p>
      {loading ? <Skeleton className="mt-2 h-8 w-20" /> : (
        <p className={`mt-1.5 text-2xl font-extrabold sm:text-3xl ${tone}`}>{value}{unit && <span className="ml-1 text-sm font-semibold text-ink-muted">{unit}</span>}</p>
      )}
      {hint && <p className="mt-1 text-xs text-ink-soft">{hint}</p>}
    </div>
  )
}

/** 0-100 anomaly score with the state thresholds marked. */
export function ScoreMeter({ score }: { score: number }) {
  const tone = score >= 85 ? 'bg-danger' : score >= 45 ? 'bg-change' : score >= 20 ? 'bg-watch' : 'bg-ok'
  return (
    <div>
      <div className="relative h-2.5 rounded-full bg-idle-soft" role="meter" aria-valuenow={score} aria-valuemin={0} aria-valuemax={100} aria-label="HydroSense anomaly score">
        <div className={`h-full rounded-full transition-[width] duration-700 ${tone}`} style={{ width: `${Math.max(score, 2)}%` }} />
        {[20, 45, 85].map((t) => <span key={t} className="absolute top-[-3px] h-4 w-px bg-ink-muted/50" style={{ left: `${t}%` }} aria-hidden />)}
      </div>
      <div className="relative mt-1 h-4 text-[10px] font-semibold text-ink-muted" aria-hidden>
        <span className="absolute left-0">Stable</span><span className="absolute left-[20%]">Attention</span>
        <span className="absolute left-[45%]">Unusual</span><span className="absolute left-[85%]">Alert</span>
      </div>
    </div>
  )
}

export function SitePicker({ sites, value, onChange }: { sites: Site[] | undefined; value: string | undefined; onChange: (id: string) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
      <span>Site</span>
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value)} className="rounded-xl border border-line bg-white px-3 py-2 text-sm font-bold text-navy-900 hover:border-aqua-400">
        {(sites ?? []).map((s) => <option key={s.id} value={s.id}>{s.name} · {s.status_label}</option>)}
      </select>
    </label>
  )
}
