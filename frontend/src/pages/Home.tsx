import { ArrowRight, Bell, HandHeart, MapPinned, PenLine, Radio, Sparkles, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertCard, ObservationCard } from '../components/cards'
import SiteMap from '../components/SiteMap'
import { AnimatedNumber, CardSkeleton, DemoTag, Disclaimer, EmptyState, Skeleton, useLive } from '../components/ui'
import { api } from '../lib/api'
import { useMode } from '../lib/mode'

const STEPS = [
  { n: '01', title: 'Measure', text: 'A small low-cost sensor in the water takes readings around the clock.', icon: Radio },
  { n: '02', title: 'Share', text: 'People nearby add what they can see: colour, algae, waste, wildlife.', icon: Users },
  { n: '03', title: 'Understand', text: 'HydroSense compares new information with what is normal for each place.', icon: Sparkles },
  { n: '04', title: 'Act', text: 'When something changes, we flag it so it can be checked and followed up.', icon: Bell },
]

export default function Home() {
  const { mode } = useMode()
  const navigate = useNavigate()
  const stats = useLive(['stats', mode], () => api.stats(mode))
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const observations = useLive(['observations', mode, 'home'], () => api.observations(mode, undefined, 6))
  const alerts = useLive(['alerts', mode], () => api.alerts(mode))
  const active = (alerts.data ?? []).filter((a) => a.status === 'active').slice(0, 2)

  const tiles = [
    { label: 'Monitoring sites', value: stats.data?.sites },
    { label: 'Active sensors', value: stats.data?.active_sensors },
    { label: 'Community observations', value: stats.data?.observations },
    { label: 'Potential anomalies', value: stats.data?.potential_anomalies },
  ]

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-navy-900 text-white">
        <div aria-hidden className="absolute inset-0 bg-[radial-gradient(60rem_30rem_at_80%_-10%,rgb(53_182_201/0.35),transparent)]" />
        <svg aria-hidden className="absolute inset-x-0 bottom-0 h-24 w-full text-mist" viewBox="0 0 1440 96" preserveAspectRatio="none">
          <path fill="currentColor" fillOpacity="0.12" d="M0 40c160 32 320 32 480 8s320-40 480-16 320 48 480 24v40H0Z" />
          <path fill="currentColor" d="M0 64c180 24 360 24 540 4s360-28 540-8 240 28 360 12v24H0Z" />
        </svg>
        <div className="container-page relative pt-16 pb-32 sm:pt-24 sm:pb-40">
          <p className="inline-flex animate-rise items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-aqua-200 ring-1 ring-white/15">
            <HandHeart className="size-3.5" aria-hidden /> Community water monitoring
          </p>
          <h1 className="mt-5 animate-rise text-5xl font-extrabold text-white sm:text-7xl">HydroSense</h1>
          <p className="mt-3 animate-rise text-2xl font-bold text-aqua-200 sm:text-3xl">Understand Your Water. Protect Your Ecosystem.</p>
          <p className="mt-5 max-w-xl animate-rise text-base text-white/80 sm:text-lg">
            HydroSense combines low-cost water sensing and citizen observations to help communities understand changes in urban freshwater environments.
          </p>
          <div className="mt-8 flex animate-rise flex-wrap gap-3">
            <Link to="/explore" className="btn bg-aqua-400 px-6 py-3.5 text-base text-navy-950 hover:bg-aqua-200 active:scale-[0.98]"><MapPinned className="size-5" aria-hidden /> Explore Water</Link>
            <Link to="/report" className="btn-light px-6 py-3.5 text-base"><PenLine className="size-5" aria-hidden /> Report an Observation</Link>
          </div>
        </div>
      </section>

      {/* Live ecosystem snapshot */}
      <section className="container-page relative z-10 -mt-20" aria-labelledby="snapshot">
        <div className="card p-5 sm:p-7">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="snapshot" className="text-lg">Ecosystem snapshot</h2>
            {mode === 'demo' ? <DemoTag /> : <span className="text-xs font-bold text-ok">Live sensor network</span>}
          </div>
          <dl className="mt-5 grid grid-cols-2 gap-4 lg:grid-cols-4">
            {tiles.map((t) => (
              <div key={t.label} className="rounded-xl bg-mist p-4">
                <dd className="text-3xl font-extrabold text-navy-900 sm:text-4xl">
                  {t.value == null ? <Skeleton className="h-9 w-16" /> : <AnimatedNumber value={t.value} />}
                </dd>
                <dt className="mt-1 text-sm font-semibold text-ink-soft">{t.label}</dt>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* How it works */}
      <section className="container-page mt-16" aria-labelledby="how">
        <p className="eyebrow">How HydroSense works</p>
        <h2 id="how" className="mt-1 text-2xl sm:text-3xl">From a sensor in the water to something you can act on</h2>
        <ol className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((s) => (
            <li key={s.n} className="card group p-5 transition duration-200 hover:-translate-y-1 hover:shadow-lift">
              <div className="flex items-center justify-between">
                <span className="text-sm font-extrabold text-aqua-600">{s.n}</span>
                <span className="grid size-10 place-items-center rounded-xl bg-aqua-50 text-aqua-600 transition group-hover:bg-aqua-600 group-hover:text-white"><s.icon className="size-5" aria-hidden /></span>
              </div>
              <h3 className="mt-4 text-lg">{s.title}</h3>
              <p className="mt-1 text-sm text-ink-soft">{s.text}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* Map preview */}
      <section className="container-page mt-16" aria-labelledby="map-preview">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Explore the map</p>
            <h2 id="map-preview" className="mt-1 text-2xl sm:text-3xl">See what's happening near you</h2>
          </div>
          <Link to="/explore" className="btn-ghost">Open the full map <ArrowRight className="size-4" aria-hidden /></Link>
        </div>
        <div className="card mt-6 overflow-hidden">
          {sites.isLoading ? <Skeleton className="h-80 w-full rounded-none" /> : (
            <SiteMap sites={sites.data ?? []} className="h-80 w-full sm:h-96" onSelect={(s) => navigate(`/explore?site=${s.id}`)} />
          )}
        </div>
      </section>

      {/* Recent alerts */}
      <section className="container-page mt-16" aria-labelledby="recent-alerts">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Recent alerts</p>
            <h2 id="recent-alerts" className="mt-1 text-2xl sm:text-3xl">Changes worth knowing about</h2>
          </div>
          <Link to="/alerts" className="text-sm font-bold text-aqua-700 hover:underline">See all alerts</Link>
        </div>
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          {alerts.isLoading ? <><CardSkeleton /><CardSkeleton /></> : active.length ? active.map((a) => <AlertCard key={a.id} alert={a} />) : (
            <div className="lg:col-span-2"><EmptyState title="All quiet right now">No unusual changes have been detected at the monitored sites.</EmptyState></div>
          )}
        </div>
      </section>

      {/* Recent observations */}
      <section className="container-page mt-16" aria-labelledby="recent-observations">
        <p className="eyebrow">Recent observations</p>
        <h2 id="recent-observations" className="mt-1 text-2xl sm:text-3xl">What people are seeing</h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {observations.isLoading ? <><CardSkeleton /><CardSkeleton /><CardSkeleton /></> : observations.data?.length ? observations.data.map((o) => <ObservationCard key={o.id} o={o} />) : (
            <div className="sm:col-span-2 lg:col-span-3">
              <EmptyState title="No observations yet" action={<Link to="/report" className="btn-primary">Be the first to report</Link>}>Observations from people near the water will appear here.</EmptyState>
            </div>
          )}
        </div>
      </section>

      {/* Why citizen observations matter */}
      <section className="container-page mt-16" aria-labelledby="why">
        <div className="overflow-hidden rounded-3xl bg-navy-900 p-7 text-white sm:p-10 lg:grid lg:grid-cols-[1.2fr_1fr] lg:gap-10">
          <div>
            <p className="text-xs font-bold tracking-[0.14em] text-aqua-400 uppercase">Why your observations matter</p>
            <h2 id="why" className="mt-2 text-2xl text-white sm:text-3xl">A sensor can count. Only people can see.</h2>
            <p className="mt-4 text-white/80">
              A sensor measures one thing very well, all day. But it cannot notice a green film on the surface, plastic drifting by the bank, or fish that have stopped showing up.
              When your observation and the sensor point the same way, we can be more confident that something has really changed. When they disagree, that tells us to look closer.
            </p>
            <Link to="/report" className="btn mt-6 bg-white text-navy-900 hover:bg-aqua-100">Share what you see <ArrowRight className="size-4" aria-hidden /></Link>
          </div>
          <ul className="mt-8 grid content-center gap-3 lg:mt-0">
            {['Takes less than a minute', 'No account or app needed', 'Your report is combined with sensor data, never used alone'].map((t) => (
              <li key={t} className="flex items-center gap-3 rounded-xl bg-white/8 px-4 py-3 text-sm font-semibold ring-1 ring-white/10">
                <span className="grid size-6 place-items-center rounded-full bg-aqua-400 text-xs font-extrabold text-navy-950" aria-hidden>✓</span>{t}
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="container-page mt-10"><Disclaimer /></section>
    </>
  )
}
