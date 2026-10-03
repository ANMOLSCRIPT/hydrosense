import { ArrowRight, Search, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import SiteMap from '../components/SiteMap'
import { Sparkline } from '../components/TrendChart'
import { DemoTag, Disclosure, ErrorState, Skeleton, StatusBadge, useLive } from '../components/ui'
import { api, type Site, type SiteStatus } from '../lib/api'
import { ppm, signed, timeAgo } from '../lib/format'
import { useMode } from '../lib/mode'
import { STATUS, STATUS_ORDER } from '../lib/status'

const LEGEND: SiteStatus[] = ['stable', 'attention', 'unusual', 'alert']

function SitePanel({ site, onClose }: { site: Site; onClose: () => void }) {
  const series = useLive(['series', site.id, '24h'], () => api.series(site.id, '24h'))
  return (
    <div className="animate-rise" role="region" aria-label={`${site.name} summary`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-xl">{site.name}</h2>
          <p className="text-sm text-ink-muted">{site.locality}</p>
        </div>
        <button onClick={onClose} aria-label="Close site summary" className="grid size-8 place-items-center rounded-full text-ink-muted hover:bg-mist hover:text-ink"><X className="size-4" /></button>
      </div>
      <div className={`mt-4 rounded-2xl p-4 ${STATUS[site.status].soft}`}>
        <StatusBadge status={site.status} label={site.headline} size="lg" />
        <p className="mt-2 text-sm text-ink">{site.summary}</p>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3">
        <div className="rounded-xl bg-mist p-3">
          <dt className="text-xs font-semibold text-ink-muted">Latest reading</dt>
          <dd className="tabular mt-0.5 text-xl font-extrabold text-navy-900">{ppm(site.latest_tds)} <span className="text-xs font-semibold text-ink-muted">ppm</span></dd>
        </div>
        <div className="rounded-xl bg-mist p-3">
          <dt className="text-xs font-semibold text-ink-muted">Last updated</dt>
          <dd className="mt-0.5 text-base font-bold text-navy-900">{timeAgo(site.latest_at)}</dd>
        </div>
      </dl>
      <Link to={`/explore/${site.id}`} className="btn-primary mt-4 w-full">Understand this site <ArrowRight className="size-4" aria-hidden /></Link>
      <Disclosure summary="See details" className="mt-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs text-ink-muted">Last 24 hours</p>
            <Sparkline points={series.data?.points} width={150} height={44} color={STATUS[site.status].hex} />
          </div>
          <dl className="space-y-1 text-right">
            <div><dt className="inline text-xs text-ink-muted">Usual level </dt><dd className="tabular inline font-bold text-navy-900">{ppm(site.baseline_tds)} ppm</dd></div>
            <div><dt className="inline text-xs text-ink-muted">Difference </dt><dd className="tabular inline font-bold text-navy-900">{signed(site.deviation_percent)}%</dd></div>
          </dl>
        </div>
        <p className="mt-3 text-xs text-ink-muted">ppm measures dissolved solids (TDS) in the water. It is one indicator and does not by itself say whether water is clean or safe.</p>
      </Disclosure>
    </div>
  )
}

export default function Explore() {
  const { mode } = useMode()
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<SiteStatus | 'all'>('all')
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const selectedId = params.get('site')
  const select = (id: string | null) => setParams(id ? { site: id } : {}, { replace: true })

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (sites.data ?? [])
      .filter((s) => (filter === 'all' || s.status === filter) && (!q || `${s.name} ${s.locality ?? ''}`.toLowerCase().includes(q)))
      .sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status))
  }, [sites.data, query, filter])
  const selected = (sites.data ?? []).find((s) => s.id === selectedId)

  return (
    <div className="relative md:grid md:h-[calc(100dvh-5.85rem)] md:grid-cols-[380px_1fr]">
      <h1 className="sr-only">Explore Water</h1>
      {/* Side panel (desktop) / bottom sheet (mobile) */}
      <aside className={`z-[500] border-line bg-white md:order-first md:overflow-y-auto md:border-r md:p-5 ${selected ? 'fixed inset-x-0 bottom-[4.2rem] max-h-[70dvh] overflow-y-auto rounded-t-3xl border-t p-5 shadow-lift md:static md:max-h-none md:rounded-none md:border-t-0 md:shadow-none' : 'hidden md:block'}`}>
        {selected ? <SitePanel key={selected.id} site={selected} onClose={() => select(null)} /> : (
          <>
            <h2 className="text-xl">Explore Water</h2>
            <p className="mt-1 text-sm text-ink-soft">Choose a place on the map to see how its water is doing.</p>
            <label className="relative mt-4 block">
              <span className="sr-only">Search sites</span>
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted" aria-hidden />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by name or area" type="search"
                className="w-full rounded-xl border border-line bg-mist py-2.5 pr-3 pl-9 text-sm placeholder:text-ink-muted focus:border-aqua-400 focus:bg-white" />
            </label>
            <div className="mt-3 flex flex-wrap gap-1.5" role="group" aria-label="Filter by state">
              <button onClick={() => setFilter('all')} aria-pressed={filter === 'all'} className={`rounded-full px-3 py-1 text-xs font-bold transition ${filter === 'all' ? 'bg-navy-900 text-white' : 'bg-mist text-ink-soft hover:bg-idle-soft'}`}>All</button>
              {LEGEND.map((s) => (
                <button key={s} onClick={() => setFilter(filter === s ? 'all' : s)} aria-pressed={filter === s}
                  className={`rounded-full px-3 py-1 text-xs font-bold transition ${filter === s ? `${STATUS[s].bg} text-white` : `${STATUS[s].soft} ${STATUS[s].text} hover:brightness-95`}`}>
                  {STATUS[s].label}
                </button>
              ))}
            </div>
            <ul className="mt-4 space-y-2">
              {sites.isLoading && [0, 1, 2, 3].map((i) => <li key={i}><Skeleton className="h-16 w-full" /></li>)}
              {visible.map((s) => (
                <li key={s.id}>
                  <button onClick={() => select(s.id)} className="flex w-full items-center justify-between gap-3 rounded-xl border border-line bg-white p-3 text-left transition hover:border-aqua-400 hover:shadow-card">
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-bold text-navy-900">{s.name}</span>
                      <span className="block truncate text-xs text-ink-muted">{s.locality}</span>
                    </span>
                    <StatusBadge status={s.status} size="sm" />
                  </button>
                </li>
              ))}
              {!sites.isLoading && !visible.length && !sites.isError && <li className="rounded-xl bg-mist p-4 text-sm text-ink-soft">No sites match. Try a different search or filter.</li>}
            </ul>
            {mode === 'demo' && <DemoTag className="mt-4" />}
          </>
        )}
      </aside>

      <div className="relative h-[calc(100dvh-10.3rem)] md:h-auto">
        {sites.isError ? <div className="p-6"><ErrorState error={sites.error} onRetry={() => sites.refetch()} /></div>
          : sites.isLoading ? <Skeleton className="size-full rounded-none" />
          : <SiteMap sites={visible} selectedId={selectedId} onSelect={(s) => select(s.id)} className="size-full" />}
        {/* Legend: colour + icon + label */}
        <div className={`absolute top-3 right-3 z-[400] rounded-xl border border-line bg-white/95 p-3 shadow-card backdrop-blur ${selected ? 'hidden md:block' : ''}`}>
          <p className="text-[11px] font-bold tracking-wide text-ink-muted uppercase">Monitoring states</p>
          <ul className="mt-1.5 space-y-1">
            {LEGEND.map((s) => {
              const Icon = STATUS[s].icon
              return <li key={s} className="flex items-center gap-2 text-xs font-semibold text-ink"><span className={`grid size-5 place-items-center rounded-full text-white ${STATUS[s].bg}`}><Icon className="size-3" aria-hidden /></span>{STATUS[s].label}</li>
            })}
          </ul>
          <p className="mt-2 max-w-[11rem] text-[10px] leading-snug text-ink-muted">HydroSense monitoring states, not certified water-safety classes.</p>
        </div>
        {!selected && <p className="pointer-events-none absolute inset-x-0 bottom-4 z-[400] mx-auto w-fit rounded-full bg-navy-900/90 px-4 py-2 text-xs font-bold text-white shadow-lift md:hidden">Tap a marker to see a site</p>}
      </div>
    </div>
  )
}
