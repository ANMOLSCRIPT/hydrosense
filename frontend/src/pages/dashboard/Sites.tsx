import { ArrowRight, Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../components/Layout'
import { Sparkline } from '../../components/TrendChart'
import { CardSkeleton, DemoTag, EmptyState, ErrorState, StatusBadge, useLive } from '../../components/ui'
import { api, type Site } from '../../lib/api'
import { ppm, signed, timeAgo } from '../../lib/format'
import { useMode } from '../../lib/mode'
import { STATUS } from '../../lib/status'

function SiteCard({ site }: { site: Site }) {
  const series = useLive(['series', site.id, '7d'], () => api.series(site.id, '7d'))
  return (
    <Link to={`/dashboard/sites/${site.id}`} className="card group block p-5 transition duration-200 hover:-translate-y-0.5 hover:shadow-lift">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0"><h2 className="truncate text-base">{site.name}</h2><p className="truncate text-xs text-ink-muted">{site.id} · {site.locality}</p></div>
        <StatusBadge status={site.status} size="sm" />
      </div>
      <div className="mt-4 flex items-end justify-between gap-3">
        <div>
          <p className="tabular text-3xl font-extrabold text-navy-900">{ppm(site.latest_tds)}<span className="ml-1 text-sm font-semibold text-ink-muted">ppm</span></p>
          <p className="tabular text-xs text-ink-soft">{signed(site.deviation_percent, 1)}% vs baseline {ppm(site.baseline_tds)}</p>
        </div>
        <Sparkline points={series.data?.points} color={STATUS[site.status].hex} />
      </div>
      <div className="mt-4 flex items-center justify-between border-t border-line pt-3 text-xs text-ink-muted">
        <span>Score {site.anomaly_score}/100 · {site.active_alerts} active alert{site.active_alerts === 1 ? '' : 's'} · {timeAgo(site.latest_at)}</span>
        <ArrowRight className="size-4 text-aqua-700 transition group-hover:translate-x-0.5" aria-hidden />
      </div>
    </Link>
  )
}

export default function Sites() {
  const { mode } = useMode()
  const [q, setQ] = useState('')
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const list = (sites.data ?? []).filter((s) => `${s.name} ${s.id} ${s.locality}`.toLowerCase().includes(q.toLowerCase())).sort((a, b) => b.anomaly_score - a.anomaly_score)
  return (
    <>
      <PageTitle eyebrow="Sites" title="Monitoring sites" actions={
        <label className="relative block"><span className="sr-only">Search sites</span><Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted" aria-hidden />
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search sites" className="w-56 rounded-xl border border-line bg-white py-2 pr-3 pl-9 text-sm focus:border-aqua-400" /></label>}>
        Sorted by anomaly score. Open a site for full analytics. {mode === 'demo' && <DemoTag />}
      </PageTitle>
      {sites.isError ? <ErrorState error={sites.error} onRetry={() => sites.refetch()} /> : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {sites.isLoading ? [0, 1, 2].map((i) => <CardSkeleton key={i} lines={4} />) : list.length ? list.map((s) => <SiteCard key={s.id} site={s} />)
            : <div className="sm:col-span-2 xl:col-span-3"><EmptyState title="No sites found">Try a different search.</EmptyState></div>}
        </div>
      )}
    </>
  )
}
