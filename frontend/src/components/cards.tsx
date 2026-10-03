import { Activity, ArrowRight, Cloud, Leaf, Sparkles, Trash2, TrendingDown, TrendingUp, Wind, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Alert, Assessment, Observation } from '../lib/api'
import { dateTime, ppm, signed, timeAgo } from '../lib/format'
import { RISK, SEVERITY, STATUS } from '../lib/status'
import { DemoTag, Disclosure, StatusBadge, StatusDot } from './ui'

/** What a citizen reported, as friendly chips. */
export function observationChips(o: Pick<Observation, 'water_clarity' | 'algae' | 'waste' | 'odor' | 'aquatic_life'>) {
  const chips: { emoji: string; text: string; tone: 'calm' | 'note' }[] = []
  if (o.water_clarity === 'clear') chips.push({ emoji: '💧', text: 'Clear water', tone: 'calm' })
  if (o.water_clarity === 'slightly_cloudy') chips.push({ emoji: '🌫', text: 'Slightly cloudy', tone: 'note' })
  if (o.water_clarity === 'very_cloudy') chips.push({ emoji: '🌁', text: 'Very cloudy', tone: 'note' })
  if (o.algae === 'yes') chips.push({ emoji: '🌿', text: 'Algae reported', tone: 'note' })
  if (o.waste === 'yes') chips.push({ emoji: '🗑', text: 'Floating waste reported', tone: 'note' })
  if (o.odor === 'mild') chips.push({ emoji: '👃', text: 'Mild smell', tone: 'note' })
  if (o.odor === 'strong') chips.push({ emoji: '👃', text: 'Strong smell', tone: 'note' })
  if (o.aquatic_life === 'yes') chips.push({ emoji: '🐟', text: 'Aquatic life observed', tone: 'calm' })
  if (!chips.length) chips.push({ emoji: '📝', text: 'Observation shared', tone: 'calm' })
  return chips
}

export function ObservationCard({ o, showSite = true }: { o: Observation; showSite?: boolean }) {
  return (
    <article className="card flex h-full flex-col p-4 transition duration-200 hover:-translate-y-0.5 hover:shadow-lift">
      <header className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          {showSite && <Link to={`/explore/${o.site_id}`} className="block truncate text-sm font-bold text-navy-900 hover:text-aqua-700">{o.site_name}</Link>}
          <time dateTime={o.timestamp} className="text-xs text-ink-muted">{timeAgo(o.timestamp)}</time>
        </div>
        {o.is_demo ? <DemoTag /> : <span className="rounded-full bg-ok-soft px-2 py-0.5 text-[11px] font-bold text-ok">Community</span>}
      </header>
      {o.image_url && <img src={o.image_url} alt={`Photo shared by a citizen at ${o.site_name}`} loading="lazy" className="mt-3 h-36 w-full rounded-xl object-cover" />}
      <ul className="mt-3 flex flex-wrap gap-1.5">
        {observationChips(o).map((c) => (
          <li key={c.text} className={`rounded-full px-2.5 py-1 text-xs font-semibold ${c.tone === 'note' ? 'bg-watch-soft text-ink' : 'bg-aqua-50 text-ink'}`}>
            <span aria-hidden>{c.emoji}</span> {c.text}
          </li>
        ))}
      </ul>
      {o.comment && <p className="mt-3 text-sm text-ink-soft">“{o.comment}”</p>}
    </article>
  )
}

export function AlertCard({ alert }: { alert: Alert }) {
  const status = alert.status === 'resolved' ? 'stable' : SEVERITY[alert.severity]
  const e = alert.evidence
  return (
    <article className={`card overflow-hidden transition duration-200 hover:shadow-lift ${alert.status === 'active' ? '' : 'opacity-80'}`}>
      <div className="flex gap-4 p-5">
        <StatusDot status={status} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={status} size="sm" label={alert.status === 'resolved' ? 'Resolved' : STATUS[status].label} />
            {alert.is_demo && <DemoTag />}
            <time dateTime={alert.timestamp} className="text-xs text-ink-muted">{timeAgo(alert.timestamp)}</time>
          </div>
          <h3 className="mt-2 text-base sm:text-lg">{alert.title}</h3>
          <p className="mt-1 text-sm text-ink-soft">{alert.message}</p>
          {alert.status === 'resolved' && alert.resolution_note && (
            <p className="mt-2 text-sm font-medium text-ok">{alert.resolution_note} <span className="font-normal text-ink-muted">· {timeAgo(alert.resolved_at)}</span></p>
          )}
        </div>
      </div>
      <div className="border-t border-line bg-mist/60 px-5 py-3">
        <Disclosure summary="Understand what changed" className="border-0 bg-transparent [&>div]:px-0 [&>summary]:px-0 [&>summary]:py-1">
          <div className="space-y-3">
            {alert.detail && <p><strong className="text-navy-900">Why? </strong>{alert.detail} HydroSense compares each reading with what is normal for this particular site.</p>}
            {alert.recommendation && <p><strong className="text-navy-900">What should I do? </strong>{alert.recommendation}</p>}
            <Disclosure summary="Show data">
              <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
                <div><dt className="text-xs text-ink-muted">Reading</dt><dd className="tabular font-bold text-navy-900">{ppm(e.current_tds as number)} ppm</dd></div>
                <div><dt className="text-xs text-ink-muted">Usual level</dt><dd className="tabular font-bold text-navy-900">{ppm(e.baseline_tds as number)} ppm</dd></div>
                <div><dt className="text-xs text-ink-muted">Difference</dt><dd className="tabular font-bold text-navy-900">{signed(e.deviation_percent as number)}%</dd></div>
                <div><dt className="text-xs text-ink-muted">First noticed</dt><dd className="font-bold text-navy-900">{dateTime(alert.timestamp)}</dd></div>
              </dl>
            </Disclosure>
            <Link to={`/explore/${alert.site_id}`} className="inline-flex items-center gap-1.5 text-sm font-bold text-aqua-700 hover:underline">
              See {alert.site_name} <ArrowRight className="size-4" aria-hidden />
            </Link>
          </div>
        </Disclosure>
      </div>
    </article>
  )
}

const FACTOR_ICONS: Record<string, LucideIcon> = {
  'trending-up': TrendingUp, 'trending-down': TrendingDown, activity: Activity, leaf: Leaf, trash: Trash2, cloud: Cloud, wind: Wind,
}

export function ConfidenceBar({ value }: { value: number }) {
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <span className="text-xs font-bold tracking-wide text-ink-muted uppercase">Confidence</span>
        <span className="tabular text-2xl font-extrabold text-navy-900">{value}%</span>
      </div>
      <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-aqua-100" role="meter" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100} aria-label="Confidence in this assessment">
        <div className="h-full rounded-full bg-aqua-600 transition-[width] duration-700" style={{ width: `${value}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-ink-muted">How well the available evidence supports this assessment. It is not a probability of pollution.</p>
    </div>
  )
}

export function FactorList({ factors }: { factors: Assessment['contributing_factors'] }) {
  if (!factors.length) return <p className="text-sm text-ink-soft">Nothing unusual stands out in the sensor readings or recent observations.</p>
  return (
    <ul className="grid gap-2 sm:grid-cols-2">
      {factors.map((f) => {
        const Icon = FACTOR_ICONS[f.icon] ?? Activity
        return (
          <li key={f.key} className="flex items-start gap-3 rounded-xl border border-line bg-white p-3">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-aqua-50 text-aqua-700"><Icon className="size-4.5" aria-hidden /></span>
            <span><span className="block text-sm font-bold text-navy-900">{f.label}</span><span className="block text-xs text-ink-soft">{f.detail}</span></span>
          </li>
        )
      })}
    </ul>
  )
}

const EVIDENCE_LABELS: Record<string, string> = {
  current_tds: 'Current reading (ppm)', baseline_tds: 'Historical baseline (ppm)', deviation_percent: 'Deviation from baseline (%)',
  trend: 'Weekly trend', rate_of_change_ppm_per_hour: 'Rate of change (ppm/hour)', persistence: 'Persistence (share of recent readings)',
  anomaly_score: 'Anomaly score (0-100)', sensor_state: 'Sensor monitoring state', observations_considered: 'Citizen observations considered',
  algae: 'Algae reported', waste: 'Floating waste reported', cloudiness: 'Cloudiness', odor: 'Smell', aquatic_life_seen: 'Aquatic life seen',
  sensor_points: 'Evidence points from sensor', citizen_points: 'Evidence points from citizens', stress_points: 'Total evidence points (0-100)',
}

export function EvidenceTable({ evidence }: { evidence: Assessment['evidence'] }) {
  return (
    <dl className="grid gap-x-8 gap-y-1.5 sm:grid-cols-2">
      {Object.entries(evidence).map(([k, v]) => (
        <div key={k} className="flex items-baseline justify-between gap-4 border-b border-line/70 py-1">
          <dt className="text-xs text-ink-muted">{EVIDENCE_LABELS[k] ?? k}</dt>
          <dd className="tabular text-sm font-semibold text-navy-900">{v == null ? '—' : typeof v === 'boolean' ? (v ? 'Yes' : 'No') : String(v).replace(/_/g, ' ')}</dd>
        </div>
      ))}
    </dl>
  )
}

/** Short AI explanation used on the citizen site page. */
export function ExplanationCard({ a }: { a: Assessment }) {
  return (
    <section className="card p-5 sm:p-6" aria-labelledby="ai-explanation">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="ai-explanation" className="flex items-center gap-2 text-lg"><Sparkles className="size-5 text-aqua-600" aria-hidden /> What HydroSense thinks</h2>
        <StatusBadge status={RISK[a.risk_level].status} label={a.assessment} />
      </div>
      <p className="mt-3 text-[15px] leading-relaxed text-ink">{a.explanation}</p>
      <p className="mt-2 text-xs text-ink-muted">
        {a.source === 'llm' ? 'Written by an AI model from HydroSense evidence. ' : 'Generated by HydroSense from sensor readings and citizen observations. '}
        A potential anomaly is not confirmed pollution.
      </p>
    </section>
  )
}
