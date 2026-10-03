import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCheck, LoaderCircle } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../components/Layout'
import { CardSkeleton, Disclosure, EmptyState, ErrorState, Segmented, StatusBadge, useLive } from '../../components/ui'
import { api, type Alert } from '../../lib/api'
import { dateTime, timeAgo } from '../../lib/format'
import { useMode } from '../../lib/mode'
import { SEVERITY } from '../../lib/status'
import { useToast } from '../../lib/toast'

const TYPE_LABELS: Record<Alert['type'], string> = { unusual_change: 'Unusual change', significant_change: 'Significant change', gradual_trend: 'Gradual trend' }

function Row({ alert }: { alert: Alert }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [note, setNote] = useState('')
  const resolve = useMutation({
    mutationFn: () => api.resolveAlert(alert.id, note),
    onSuccess: () => { toast(`Alert at ${alert.site_name} marked as resolved.`, 'success'); qc.invalidateQueries({ queryKey: ['alerts'] }); qc.invalidateQueries({ queryKey: ['sites'] }); qc.invalidateQueries({ queryKey: ['stats'] }) },
    onError: (e) => toast((e as Error).message, 'error'),
  })
  const active = alert.status === 'active'
  return (
    <article className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={active ? SEVERITY[alert.severity] : 'stable'} size="sm" label={active ? `${alert.severity} severity` : 'Resolved'} />
            <span className="rounded-full bg-idle-soft px-2 py-0.5 text-xs font-bold text-ink-soft">{TYPE_LABELS[alert.type]}</span>
            <span className="text-xs text-ink-muted">{dateTime(alert.timestamp)} · {timeAgo(alert.timestamp)}</span>
          </div>
          <h2 className="mt-2 text-base">{alert.title}</h2>
          <p className="mt-0.5 text-sm text-ink-soft">{alert.message}</p>
        </div>
        <Link to={`/dashboard/sites/${alert.site_id}`} className="text-xs font-bold text-aqua-700 hover:underline">{alert.site_name} analytics</Link>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-5">
        {[['Reading', `${alert.evidence.current_tds ?? '—'} ppm`], ['Baseline', `${alert.evidence.baseline_tds ?? '—'} ppm`], ['Deviation', `${alert.evidence.deviation_percent ?? '—'}%`],
          ['Anomaly score', `${alert.evidence.anomaly_score ?? '—'}/100`], ['Persistence', alert.evidence.persistence != null ? `${Math.round(Number(alert.evidence.persistence) * 100)}%` : '—']].map(([k, v]) => (
          <div key={k} className="rounded-xl bg-mist p-3"><dt className="text-xs text-ink-muted">{k}</dt><dd className="tabular font-bold text-navy-900">{v}</dd></div>
        ))}
      </dl>
      {alert.recommendation && <p className="mt-3 text-sm text-ink"><strong>Recommendation: </strong>{alert.recommendation}</p>}
      {active ? (
        <Disclosure summary="Resolve this alert" className="mt-4">
          <label htmlFor={`note-${alert.id}`} className="text-xs font-bold text-ink-soft">Resolution note (optional)</label>
          <div className="mt-1.5 flex flex-wrap gap-2">
            <input id={`note-${alert.id}`} value={note} onChange={(e) => setNote(e.target.value.slice(0, 300))} placeholder="For example: field check completed, source identified"
              className="min-w-56 flex-1 rounded-xl border border-line bg-white px-3 py-2 text-sm focus:border-aqua-400" />
            <button onClick={() => resolve.mutate()} disabled={resolve.isPending} className="btn-dark px-4 py-2">
              {resolve.isPending ? <LoaderCircle className="size-4 animate-spin" aria-hidden /> : <CheckCheck className="size-4" aria-hidden />} Mark resolved
            </button>
          </div>
        </Disclosure>
      ) : (
        <p className="mt-3 rounded-xl bg-ok-soft p-3 text-sm text-ink"><strong className="text-ok">Resolved {dateTime(alert.resolved_at)}.</strong> {alert.resolution_note}</p>
      )}
    </article>
  )
}

export default function AlertsAdmin() {
  const { mode } = useMode()
  const [tab, setTab] = useState<'active' | 'resolved'>('active')
  const [severity, setSeverity] = useState<'all' | Alert['severity']>('all')
  const alerts = useLive(['alerts', mode], () => api.alerts(mode))
  const all = alerts.data ?? []
  const list = all.filter((a) => a.status === tab && (severity === 'all' || a.severity === severity))
  return (
    <>
      <PageTitle eyebrow="Alerts" title="Alert management" actions={
        <div className="flex flex-wrap items-center gap-2">
          <Segmented value={tab} onChange={setTab} label="Alert status" options={[{ value: 'active', label: `Active (${all.filter((a) => a.status === 'active').length})` }, { value: 'resolved', label: `Resolved (${all.filter((a) => a.status === 'resolved').length})` }]} />
          <label className="sr-only" htmlFor="sev">Severity</label>
          <select id="sev" value={severity} onChange={(e) => setSeverity(e.target.value as typeof severity)} className="rounded-xl border border-line bg-white px-3 py-2 text-xs font-bold text-navy-900">
            <option value="all">All severities</option><option value="high">High</option><option value="medium">Medium</option><option value="low">Low</option>
          </select>
        </div>}>
        Alerts are raised only for sustained changes and describe potential anomalies, not confirmed pollution. They resolve automatically when readings return to the usual range.
      </PageTitle>
      <div className="space-y-4">
        {alerts.isLoading ? <><CardSkeleton lines={4} /><CardSkeleton lines={4} /></> : alerts.isError ? <ErrorState error={alerts.error} onRetry={() => alerts.refetch()} />
          : list.length ? list.map((a) => <Row key={a.id} alert={a} />) : <EmptyState title={`No ${tab} alerts`}>{tab === 'active' ? 'Every site is within its usual range.' : 'Resolved alerts will be listed here.'}</EmptyState>}
      </div>
    </>
  )
}
