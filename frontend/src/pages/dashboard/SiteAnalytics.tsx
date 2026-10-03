import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { observationChips } from '../../components/cards'
import { Kpi, ScoreMeter, SitePicker } from '../../components/dash'
import { PageTitle } from '../../components/Layout'
import TrendChart from '../../components/TrendChart'
import { DemoTag, Disclaimer, Disclosure, EmptyState, ErrorState, Segmented, StatusBadge, useLive } from '../../components/ui'
import { api, type RangeKey } from '../../lib/api'
import { dateTime, ppm, signed, timeAgo } from '../../lib/format'
import { useMode } from '../../lib/mode'
import { SEVERITY, STATUS } from '../../lib/status'

const PARTS: Record<string, { label: string; max: number }> = {
  magnitude: { label: 'Magnitude of deviation', max: 55 }, significance: { label: 'Statistical significance', max: 15 },
  persistence: { label: 'Persistence', max: 20 }, dynamics: { label: 'Speed of change', max: 10 },
}

export default function SiteAnalytics() {
  const { mode } = useMode()
  const { id } = useParams()
  const navigate = useNavigate()
  const [range, setRange] = useState<RangeKey>('7d')
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const ranked = [...(sites.data ?? [])].sort((a, b) => b.anomaly_score - a.anomaly_score)
  const siteId = id ?? ranked[0]?.id
  const enabled = !!siteId
  const site = useLive(['site', siteId], () => api.site(siteId!), { enabled })
  const a = useLive(['analytics', siteId], () => api.analytics(siteId!), { enabled })
  const series = useLive(['series', siteId, range], () => api.series(siteId!, range), { enabled })
  const observations = useLive(['observations', 'site', siteId], () => api.observations(mode, siteId, 8), { enabled })
  const alerts = useLive(['alerts', mode, siteId], () => api.alerts(mode, siteId), { enabled })

  if (site.isError) return <ErrorState error={site.error} onRetry={() => site.refetch()} />
  if (!sites.isLoading && !siteId) return <EmptyState title="No sites to analyse yet">Sites appear here once they are registered.</EmptyState>
  const s = site.data, d = a.data
  const loading = !s || !d

  return (
    <>
      <PageTitle eyebrow="Site analytics" title={s?.name ?? 'Loading site…'}
        actions={<SitePicker sites={ranked} value={siteId} onChange={(v) => navigate(`/dashboard/sites/${v}`)} />}>
        {s ? <>{s.id} · {s.locality} {s.is_demo && <DemoTag className="ml-1" />}</> : null}
      </PageTitle>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-6">
        <Kpi label="Current" value={ppm(d?.current_tds)} unit="ppm" hint={`Updated ${timeAgo(d?.latest_at)}`} loading={loading} />
        <Kpi label="Baseline" value={ppm(d?.baseline_tds)} unit="ppm" hint={`${d?.baseline_points ?? 0} points · ±${d?.spread ?? '—'} spread`} loading={loading} />
        <Kpi label="Deviation" value={`${signed(d?.deviation_percent, 1)}%`} hint={`z = ${d?.z_score ?? '—'}`} loading={loading} tone={d ? STATUS[d.state].text : undefined} />
        <Kpi label="Trend" value={<span className="capitalize">{d?.trend ?? '—'}</span>} hint={d?.trend_percent_per_day != null ? `${signed(d.trend_percent_per_day, 2)}% per day` : 'Short history'} loading={loading} />
        <Kpi label="Rate of change" value={signed(d?.rate_of_change_ppm_per_hour, 1)} unit="ppm/h" hint="Last 3 hours" loading={loading} />
        <Kpi label="24h rolling" value={ppm(d?.rolling_mean_24h)} unit="ppm" hint={`σ ${d?.rolling_std_24h ?? '—'}`} loading={loading} />
      </div>

      <section className="card mt-4 p-5 sm:p-6" aria-labelledby="an-trend">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3"><h2 id="an-trend" className="text-lg">Measurements</h2>{d && <StatusBadge status={d.state} />}</div>
          <Segmented value={range} onChange={setRange} label="Date range" options={[{ value: '24h', label: '24 hours' }, { value: '7d', label: '7 days' }, { value: '30d', label: '30 days' }]} />
        </div>
        <div className="mt-4"><TrendChart points={series.data?.points} range={range} baseline={d?.baseline_tds} anomalies={d?.anomalies} loading={series.isLoading} height={320} /></div>
        <Disclosure summary="View measurements as a table" className="mt-4">
          <div className="max-h-72 overflow-auto">
            <table className="tabular w-full text-left text-xs">
              <thead className="sticky top-0 bg-white text-ink-muted"><tr><th className="py-1.5">Time</th><th>Mean (ppm)</th><th>Min</th><th>Max</th><th>Readings</th></tr></thead>
              <tbody>{[...(series.data?.points ?? [])].reverse().map((p) => (
                <tr key={p.t} className="border-t border-line/70"><td className="py-1.5">{dateTime(p.t)}</td><td className="font-semibold text-navy-900">{p.tds}</td><td>{p.min}</td><td>{p.max}</td><td>{p.n}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </Disclosure>
      </section>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <section className="card p-5" aria-labelledby="an-score">
          <h2 id="an-score" className="text-lg">Anomaly score</h2>
          <p className="tabular mt-1 text-4xl font-extrabold text-navy-900">{d?.anomaly_score ?? '—'}<span className="text-base font-semibold text-ink-muted"> / 100</span></p>
          <div className="mt-2"><ScoreMeter score={d?.anomaly_score ?? 0} /></div>
          <ul className="mt-5 space-y-3">
            {Object.entries(PARTS).map(([k, p]) => {
              const v = d?.score_parts?.[k] ?? 0
              return (
                <li key={k}>
                  <div className="flex justify-between text-xs"><span className="font-semibold text-ink-soft">{p.label}</span><span className="tabular font-bold text-navy-900">{v.toFixed(1)} / {p.max}</span></div>
                  <div className="mt-1 h-1.5 rounded-full bg-aqua-100"><div className="h-full rounded-full bg-aqua-600 transition-[width] duration-700" style={{ width: `${(v / p.max) * 100}%` }} /></div>
                </li>
              )
            })}
          </ul>
          <p className="mt-4 text-xs text-ink-muted">{d?.methodology}</p>
        </section>

        <section className="card p-5" aria-labelledby="an-alerts">
          <h2 id="an-alerts" className="text-lg">Alerts at this site</h2>
          <ul className="mt-3 space-y-2">
            {alerts.data?.length ? alerts.data.map((al) => (
              <li key={al.id} className="rounded-xl border border-line p-3">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-bold text-navy-900">{al.title}</p>
                  <StatusBadge status={al.status === 'resolved' ? 'stable' : SEVERITY[al.severity]} size="sm" label={al.status === 'resolved' ? 'Resolved' : `${al.severity} severity`} />
                </div>
                <p className="mt-1 text-xs text-ink-muted">{dateTime(al.timestamp)}{al.resolved_at ? ` → resolved ${dateTime(al.resolved_at)}` : ''}</p>
              </li>
            )) : <li className="rounded-xl bg-mist p-4 text-sm text-ink-soft">No alerts recorded for this site.</li>}
          </ul>
          <h2 className="mt-6 text-lg">Device information</h2>
          <ul className="mt-3 space-y-2">
            {s?.devices.length ? s.devices.map((dv) => (
              <li key={dv.device_id} className="flex items-center justify-between gap-3 rounded-xl border border-line p-3 text-sm">
                <div><Link to={`/dashboard/devices/test?device=${dv.device_id}`} className="font-bold text-navy-900 hover:text-aqua-700">{dv.device_id}</Link><p className="text-xs text-ink-muted">Firmware {dv.firmware_version ?? '—'} · last seen {timeAgo(dv.last_seen)}</p></div>
                <span className={`inline-flex items-center gap-1.5 text-xs font-bold ${dv.status === 'online' ? 'text-ok' : 'text-idle'}`}><span className={`size-2 rounded-full ${dv.status === 'online' ? 'bg-ok' : 'bg-idle'}`} />{dv.status === 'online' ? 'Online' : 'Offline'}</span>
              </li>
            )) : <li className="rounded-xl bg-mist p-4 text-sm text-ink-soft">No device has reported from this site yet.</li>}
          </ul>
        </section>
      </div>

      <section className="card mt-4 p-5" aria-labelledby="an-obs">
        <h2 id="an-obs" className="text-lg">Citizen observations</h2>
        <div className="mt-3 overflow-x-auto">
          {observations.data?.length ? (
            <table className="w-full min-w-[30rem] text-left text-sm">
              <thead className="text-xs font-bold tracking-wide text-ink-muted uppercase"><tr><th className="py-2">When</th><th>Reported</th><th>Comment</th><th>Photo</th></tr></thead>
              <tbody>{observations.data.map((o) => (
                <tr key={o.id} className="border-t border-line align-top">
                  <td className="py-2 pr-3 whitespace-nowrap text-ink-soft">{dateTime(o.timestamp)}</td>
                  <td className="pr-3">{observationChips(o).map((c) => c.text).join(', ')}</td>
                  <td className="pr-3 text-ink-soft">{o.comment ?? '—'}</td>
                  <td>{o.image_url ? <a href={o.image_url} target="_blank" rel="noreferrer" className="font-bold text-aqua-700 hover:underline">View</a> : '—'}</td>
                </tr>
              ))}</tbody>
            </table>
          ) : <p className="rounded-xl bg-mist p-4 text-sm text-ink-soft">No citizen observations for this site yet.</p>}
        </div>
      </section>
      <div className="mt-4"><Disclaimer compact /></div>
    </>
  )
}
