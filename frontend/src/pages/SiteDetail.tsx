import { ArrowLeft, Clock, Droplet, MapPin, PenLine, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ExplanationCard, ObservationCard } from '../components/cards'
import TrendChart from '../components/TrendChart'
import { CardSkeleton, DemoTag, Disclaimer, Disclosure, EmptyState, ErrorState, Segmented, Skeleton, StatusDot, useLive } from '../components/ui'
import { api, type RangeKey } from '../lib/api'
import { ppm, signed, timeAgo } from '../lib/format'
import { STATUS } from '../lib/status'

const RANGES: { value: RangeKey; label: string }[] = [
  { value: '24h', label: 'Last 24 hours' }, { value: '7d', label: '7 days' }, { value: '30d', label: '30 days' },
]
const TREND_WORDS: Record<string, string> = { increasing: 'Rising', decreasing: 'Falling', stable: 'Steady', unknown: 'Not enough data' }

export default function SiteDetail() {
  const { siteId = '' } = useParams()
  const [range, setRange] = useState<RangeKey>('7d')
  const site = useLive(['site', siteId], () => api.site(siteId))
  const series = useLive(['series', siteId, range], () => api.series(siteId, range))
  const observations = useLive(['observations', 'site', siteId], () => api.observations('demo', siteId, 9))
  const assessment = useLive(['assessment', siteId], () => api.assessment(siteId))

  if (site.isError) return <div className="container-page py-10"><ErrorState error={site.error} onRetry={() => site.refetch()} /><Link to="/explore" className="btn-ghost mt-4"><ArrowLeft className="size-4" aria-hidden /> Back to the map</Link></div>
  const s = site.data
  const style = s ? STATUS[s.status] : null

  return (
    <div className="container-page py-6 sm:py-10">
      <Link to="/explore" className="inline-flex items-center gap-1.5 text-sm font-bold text-aqua-700 hover:underline"><ArrowLeft className="size-4" aria-hidden /> Back to the map</Link>

      {!s || !style ? <div className="mt-6 space-y-4"><Skeleton className="h-10 w-64" /><CardSkeleton lines={4} /></div> : (
        <>
          <header className="mt-4 flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 className="text-3xl sm:text-4xl">{s.name}</h1>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-ink-soft"><MapPin className="size-4" aria-hidden />{s.locality}{s.description ? ` · ${s.description}` : ''}</p>
            </div>
            {s.is_demo && <DemoTag />}
          </header>

          {/* How is this site doing? */}
          <section aria-labelledby="doing" className={`mt-6 animate-rise rounded-3xl p-6 sm:p-8 ${style.soft}`}>
            <h2 id="doing" className="text-sm font-bold tracking-wide text-ink-soft uppercase">How is this site doing?</h2>
            <div className="mt-3 flex items-start gap-4">
              <StatusDot status={s.status} />
              <div>
                <p className={`text-2xl font-extrabold sm:text-3xl ${style.text}`}>{s.citizen.headline}</p>
                <p className="mt-1 max-w-xl text-base text-ink sm:text-lg">{s.citizen.summary}</p>
              </div>
            </div>
            <div className="mt-5 grid gap-2 sm:grid-cols-2">
              <Disclosure summary="Why?">
                <p>{s.citizen.why}</p>
                {s.citizen.detail && <Disclosure summary="Show data" className="mt-3"><p className="tabular font-semibold text-navy-900">{s.citizen.detail}</p><p className="mt-1 text-xs">ppm measures dissolved solids (TDS). It is one indicator among many.</p></Disclosure>}
              </Disclosure>
              <Disclosure summary="What should I do?"><p>{s.citizen.action}</p></Disclosure>
            </div>
          </section>

          {/* What we're seeing */}
          <section aria-labelledby="seeing" className="mt-10">
            <h2 id="seeing" className="text-xl sm:text-2xl">What we're seeing</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              <div className="card p-5">
                <Droplet className="size-5 text-aqua-600" aria-hidden />
                <h3 className="mt-3 text-sm font-bold text-ink-soft">Water measurements</h3>
                <p className="tabular mt-1 text-2xl font-extrabold text-navy-900">{ppm(s.latest_tds)} <span className="text-sm font-semibold text-ink-muted">ppm</span></p>
                <p className="mt-1 flex items-center gap-1 text-xs text-ink-muted"><Clock className="size-3" aria-hidden /> Updated {timeAgo(s.latest_at)}</p>
              </div>
              <div className="card p-5">
                <Users className="size-5 text-aqua-600" aria-hidden />
                <h3 className="mt-3 text-sm font-bold text-ink-soft">Recent observations</h3>
                <p className="tabular mt-1 text-2xl font-extrabold text-navy-900">{s.observation_count}</p>
                <p className="mt-1 text-xs text-ink-muted">shared by people near this site</p>
              </div>
              <div className="card p-5">
                <style.icon className={`size-5 ${style.text}`} aria-hidden />
                <h3 className="mt-3 text-sm font-bold text-ink-soft">Recent changes</h3>
                <p className="mt-1 text-2xl font-extrabold text-navy-900">{TREND_WORDS[s.trend] ?? 'Steady'}</p>
                <p className="mt-1 text-xs text-ink-muted">{s.deviation_percent == null ? 'Still learning what is usual here' : Math.abs(s.deviation_percent) < 5 ? 'In line with the usual level' : `${signed(s.deviation_percent)}% compared with usual`}</p>
              </div>
            </div>
          </section>

          {/* Simple trend */}
          <section aria-labelledby="trend" className="card mt-6 p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 id="trend" className="text-lg">How readings have changed</h2>
                <p className="text-sm text-ink-soft">The green band shows what is usual for this site.</p>
              </div>
              <Segmented value={range} onChange={setRange} options={RANGES} label="Time period" />
            </div>
            <div className="mt-4">
              <TrendChart points={series.data?.points} range={range} baseline={s.baseline_tds} usualBand loading={series.isLoading} />
            </div>
          </section>

          {/* AI explanation + recommended action */}
          <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
            {assessment.data ? <ExplanationCard a={assessment.data} /> : assessment.isError ? <ErrorState error={assessment.error} onRetry={() => assessment.refetch()} /> : <CardSkeleton lines={4} />}
            <section className="flex flex-col justify-between rounded-2xl bg-navy-900 p-5 text-white sm:p-6" aria-labelledby="action">
              <div>
                <h2 id="action" className="text-lg text-white">Recommended action</h2>
                <p className="mt-2 text-sm text-white/85">{assessment.data?.recommendation ?? s.citizen.action}</p>
              </div>
              <Link to={`/report?site=${s.id}`} className="btn mt-5 bg-aqua-400 text-navy-950 hover:bg-aqua-200"><PenLine className="size-4" aria-hidden /> Report what you see</Link>
            </section>
          </div>

          {/* Citizen observations */}
          <section aria-labelledby="obs" className="mt-10">
            <h2 id="obs" className="text-xl sm:text-2xl">Citizen observations</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {observations.isLoading ? <><CardSkeleton /><CardSkeleton /><CardSkeleton /></> : observations.data?.length ? observations.data.map((o) => <ObservationCard key={o.id} o={o} showSite={false} />) : (
                <div className="sm:col-span-2 lg:col-span-3"><EmptyState title="No observations here yet" action={<Link to={`/report?site=${s.id}`} className="btn-primary">Share the first one</Link>}>If you are near this site, a quick report helps everyone understand it better.</EmptyState></div>
              )}
            </div>
          </section>

          <div className="mt-10"><Disclaimer /></div>
        </>
      )}
    </div>
  )
}
