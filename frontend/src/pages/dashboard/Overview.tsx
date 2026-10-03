import { ArrowRight } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { observationChips } from '../../components/cards'
import { Kpi, ScoreMeter, SitePicker } from '../../components/dash'
import { PageTitle } from '../../components/Layout'
import TrendChart, { Sparkline } from '../../components/TrendChart'
import { CardSkeleton, EmptyState, ErrorState, Segmented, StatusBadge, useLive } from '../../components/ui'
import { api, type RangeKey, type Site } from '../../lib/api'
import { ppm, signed, timeAgo } from '../../lib/format'
import { useMode } from '../../lib/mode'
import { SEVERITY, STATUS } from '../../lib/status'

function SiteRow({ site, onPick, picked }: { site: Site; onPick: () => void; picked: boolean }) {
  const series = useLive(['series', site.id, '7d'], () => api.series(site.id, '7d'))
  return (
    <tr className={`border-t border-line transition hover:bg-mist ${picked ? 'bg-aqua-50/60' : ''}`}>
      <td className="py-3 pr-3 pl-5"><button onClick={onPick} className="text-left font-bold text-navy-900 hover:text-aqua-700">{site.name}</button><p className="text-xs text-ink-muted">{site.locality}</p></td>
      <td className="px-3"><StatusBadge status={site.status} size="sm" /></td>
      <td className="tabular px-3 text-right font-semibold">{ppm(site.latest_tds)}</td>
      <td className="tabular hidden px-3 text-right text-ink-soft sm:table-cell">{ppm(site.baseline_tds)}</td>
      <td className="tabular px-3 text-right font-semibold">{signed(site.deviation_percent, 1)}%</td>
      <td className="hidden px-3 md:table-cell"><Sparkline points={series.data?.points} color={STATUS[site.status].hex} /></td>
      <td className="pr-5 pl-3 text-right"><Link to={`/dashboard/sites/${site.id}`} className="inline-flex items-center gap-1 text-xs font-bold text-aqua-700 hover:underline">Analytics <ArrowRight className="size-3" aria-hidden /></Link></td>
    </tr>
  )
}

export default function Overview() {
  const { mode } = useMode()
  const [picked, setPicked] = useState<string>()
  const [range, setRange] = useState<RangeKey>('7d')
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const devices = useLive(['devices', mode], () => api.devices(mode))
  const alerts = useLive(['alerts', mode], () => api.alerts(mode))
  const observations = useLive(['observations', mode, 'dash'], () => api.observations(mode, undefined, 5))

  const ranked = [...(sites.data ?? [])].sort((a, b) => b.anomaly_score - a.anomaly_score)
  const focus = ranked.find((s) => s.id === picked) ?? ranked[0]
  const series = useLive(['series', focus?.id, range], () => api.series(focus!.id, range), { enabled: !!focus })
  const analytics = useLive(['analytics', focus?.id], () => api.analytics(focus!.id), { enabled: !!focus })
  const active = (alerts.data ?? []).filter((a) => a.status === 'active')
  const online = (devices.data ?? []).filter((d) => d.status === 'online').length

  if (sites.isError) return <ErrorState error={sites.error} onRetry={() => sites.refetch()} />
  return (
    <>
      <PageTitle eyebrow="Monitoring dashboard" title="Network overview">
        {mode === 'demo' ? 'Simulated sensor network for demonstration.' : 'Live readings from connected HydroSense devices. Updates every few seconds.'}
      </PageTitle>

      {/* Top: KPIs for the focused site and the network */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Kpi label="Current TDS" value={ppm(focus?.latest_tds)} unit="ppm" hint={focus ? `${focus.name} · ${timeAgo(focus.latest_at)}` : undefined} loading={sites.isLoading} />
        <Kpi label="Baseline" value={ppm(focus?.baseline_tds)} unit="ppm" hint="Median of site history" loading={sites.isLoading} />
        <Kpi label="Deviation" value={`${signed(focus?.deviation_percent, 1)}%`} hint="(current − baseline) ÷ baseline" loading={sites.isLoading}
          tone={focus ? STATUS[focus.status].text : undefined} />
        <Kpi label="Active alerts" value={active.length} hint={`${sites.data?.length ?? 0} sites monitored`} loading={alerts.isLoading} tone={active.length ? 'text-change' : 'text-ok'} />
        <Kpi label="Devices online" value={`${online}/${devices.data?.length ?? 0}`} hint={<Link to="/dashboard/devices" className="font-bold text-aqua-700 hover:underline">Device status</Link>} loading={devices.isLoading} />
      </div>

      {/* Middle: trend */}
      <section className="card mt-4 p-5 sm:p-6" aria-labelledby="dash-trend">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <h2 id="dash-trend" className="text-lg">TDS trend</h2>
            {focus && <StatusBadge status={focus.status} />}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <SitePicker sites={ranked} value={focus?.id} onChange={setPicked} />
            <Segmented value={range} onChange={setRange} label="Date range" options={[{ value: '24h', label: '24h' }, { value: '7d', label: '7d' }, { value: '30d', label: '30d' }]} />
          </div>
        </div>
        <div className="mt-4 grid gap-6 lg:grid-cols-[1fr_260px]">
          <TrendChart points={series.data?.points} range={range} baseline={analytics.data?.baseline_tds} anomalies={analytics.data?.anomalies} loading={series.isLoading || !focus} height={280} />
          <div className="space-y-4">
            <div>
              <p className="text-xs font-bold tracking-wide text-ink-muted uppercase">Anomaly score</p>
              <p className="tabular text-4xl font-extrabold text-navy-900">{analytics.data?.anomaly_score ?? '—'}<span className="text-base font-semibold text-ink-muted"> / 100</span></p>
              <div className="mt-2"><ScoreMeter score={analytics.data?.anomaly_score ?? 0} /></div>
            </div>
            <dl className="space-y-1.5 text-sm">
              <div className="flex justify-between"><dt className="text-ink-muted">Trend (7 d)</dt><dd className="font-bold capitalize">{analytics.data?.trend ?? '—'}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Rate of change</dt><dd className="tabular font-bold">{signed(analytics.data?.rate_of_change_ppm_per_hour, 1)} ppm/h</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Persistence</dt><dd className="tabular font-bold">{analytics.data ? `${Math.round(analytics.data.persistence * 100)}%` : '—'}</dd></div>
            </dl>
            {focus && <Link to={`/dashboard/sites/${focus.id}`} className="btn-ghost w-full">Full site analytics <ArrowRight className="size-4" aria-hidden /></Link>}
          </div>
        </div>
      </section>

      {/* Bottom: detail */}
      <section className="card mt-4 overflow-hidden" aria-labelledby="dash-sites">
        <h2 id="dash-sites" className="px-5 pt-5 text-lg">All sites</h2>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[34rem] text-sm">
            <thead><tr className="text-left text-xs font-bold tracking-wide text-ink-muted uppercase">
              <th className="py-2 pr-3 pl-5">Site</th><th className="px-3">State</th><th className="px-3 text-right">TDS (ppm)</th><th className="hidden px-3 text-right sm:table-cell">Baseline</th><th className="px-3 text-right">Deviation</th><th className="hidden px-3 md:table-cell">7-day trend</th><th className="pr-5 pl-3" />
            </tr></thead>
            <tbody>{ranked.map((s) => <SiteRow key={s.id} site={s} picked={s.id === focus?.id} onPick={() => setPicked(s.id)} />)}</tbody>
          </table>
        </div>
      </section>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <section className="card p-5" aria-labelledby="dash-alerts">
          <div className="flex items-center justify-between"><h2 id="dash-alerts" className="text-lg">Active alerts</h2><Link to="/dashboard/alerts" className="text-xs font-bold text-aqua-700 hover:underline">Manage alerts</Link></div>
          <ul className="mt-3 space-y-2">
            {alerts.isLoading ? <CardSkeleton lines={2} /> : active.length ? active.map((a) => (
              <li key={a.id} className="flex items-start justify-between gap-3 rounded-xl border border-line p-3">
                <div className="min-w-0"><p className="truncate text-sm font-bold text-navy-900">{a.title}</p><p className="text-xs text-ink-muted">{a.type.replace(/_/g, ' ')} · {timeAgo(a.timestamp)}</p></div>
                <StatusBadge status={SEVERITY[a.severity]} size="sm" label={`${a.severity} severity`} />
              </li>
            )) : <EmptyState title="No active alerts">All sites are within their usual range.</EmptyState>}
          </ul>
        </section>
        <section className="card p-5" aria-labelledby="dash-obs">
          <h2 id="dash-obs" className="text-lg">Recent citizen observations</h2>
          <ul className="mt-3 space-y-2">
            {observations.isLoading ? <CardSkeleton lines={2} /> : observations.data?.length ? observations.data.map((o) => (
              <li key={o.id} className="rounded-xl border border-line p-3">
                <div className="flex justify-between gap-2 text-sm"><span className="font-bold text-navy-900">{o.site_name}</span><span className="text-xs text-ink-muted">{timeAgo(o.timestamp)}</span></div>
                <p className="mt-1 text-xs text-ink-soft">{observationChips(o).map((c) => c.text).join(' · ')}</p>
              </li>
            )) : <EmptyState title="No observations yet">Citizen reports will appear here.</EmptyState>}
          </ul>
        </section>
      </div>
    </>
  )
}
