import { Bell, BrainCircuit, Cpu, Droplets, FlaskConical, Gauge, Home, Info, LayoutDashboard, LineChart, Map as MapIcon, MapPinned, PenLine, Radio, Users } from 'lucide-react'
import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import type { Mode } from '../lib/api'
import { useMode } from '../lib/mode'
import { useToast } from '../lib/toast'

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2.5" aria-label="HydroSense home">
      <span className={`grid size-9 place-items-center rounded-xl ${light ? 'bg-white/10 text-aqua-400' : 'bg-navy-900 text-aqua-400'}`}><Droplets className="size-5" aria-hidden /></span>
      <span className={`text-lg font-extrabold tracking-tight ${light ? 'text-white' : 'text-navy-900'}`}>HydroSense</span>
    </Link>
  )
}

export function ModeToggle({ dark = false }: { dark?: boolean }) {
  const { mode, setMode } = useMode()
  const toast = useToast()
  const pick = (m: Mode) => {
    if (m === mode) return
    setMode(m)
    toast(m === 'demo' ? 'Demo Mode: using simulated data.' : 'Live Mode: connected to the HydroSense sensor network.', 'info')
  }
  const base = 'flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-bold transition'
  const on = dark ? 'bg-white text-navy-900 shadow-sm' : 'bg-white text-navy-900 shadow-sm'
  const off = dark ? 'text-white/70 hover:text-white' : 'text-ink-muted hover:text-navy-900'
  return (
    <div role="radiogroup" aria-label="Data source" className={`inline-flex rounded-xl p-1 ${dark ? 'bg-white/10' : 'bg-idle-soft'}`}>
      <button role="radio" aria-checked={mode === 'demo'} onClick={() => pick('demo')} className={`${base} ${mode === 'demo' ? on : off}`}>
        <FlaskConical className="size-3.5" aria-hidden /> Demo<span className="hidden sm:inline"> Mode</span>
      </button>
      <button role="radio" aria-checked={mode === 'live'} onClick={() => pick('live')} className={`${base} ${mode === 'live' ? on : off}`}>
        <Radio className="size-3.5" aria-hidden /> Live<span className="hidden sm:inline"> Mode</span>
      </button>
    </div>
  )
}

export function ModeBanner() {
  const { mode } = useMode()
  return mode === 'demo' ? (
    <div className="border-b border-aqua-200 bg-aqua-50 px-4 py-1.5 text-center text-xs font-semibold text-aqua-700">
      <FlaskConical className="mr-1 inline size-3.5 align-[-2px]" aria-hidden />
      Demo Data · Using simulated data, not real environmental measurements
    </div>
  ) : (
    <div className="border-b border-ok/20 bg-ok-soft px-4 py-1.5 text-center text-xs font-semibold text-ok">
      <span className="relative mr-1.5 inline-flex size-2 align-middle"><span className="absolute inline-flex size-full animate-ping rounded-full bg-ok opacity-60" /><span className="relative inline-flex size-2 rounded-full bg-ok" /></span>
      Live Mode · Connected to HydroSense sensor network
    </div>
  )
}

function ScrollReset() {
  const { pathname, hash } = useLocation()
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [pathname, hash])
  return null
}

const CITIZEN_NAV = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/explore', label: 'Explore Water', short: 'Explore', icon: MapIcon },
  { to: '/report', label: 'Report', icon: PenLine },
  { to: '/alerts', label: 'Alerts', icon: Bell },
  { to: '/about', label: 'About', icon: Info },
]

export function CitizenLayout() {
  const fullScreen = useLocation().pathname === '/explore' // the map page fills the screen
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:z-[3000] focus:m-2 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:font-bold">Skip to content</a>
      <ScrollReset />
      <header className="sticky top-0 z-[1000] border-b border-line bg-white/90 backdrop-blur">
        <div className="container-page flex h-16 items-center justify-between gap-3">
          <Logo />
          <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
            {CITIZEN_NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.end}
                className={({ isActive }) => `rounded-lg px-3 py-2 text-sm font-semibold transition ${isActive ? 'bg-aqua-50 text-aqua-700' : 'text-ink-soft hover:bg-mist hover:text-navy-900'}`}>
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ModeToggle />
            <Link to="/dashboard" className="hidden items-center gap-1.5 rounded-xl border border-line px-3 py-2 text-xs font-bold text-navy-900 transition hover:border-aqua-400 hover:bg-aqua-50 lg:inline-flex">
              <LayoutDashboard className="size-3.5" aria-hidden /> Monitoring dashboard
            </Link>
          </div>
        </div>
        <ModeBanner />
      </header>
      <main id="main" className="flex-1 pb-20 md:pb-0"><Outlet /></main>
      {!fullScreen && <Footer />}
      <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-[1000] grid grid-cols-5 border-t border-line bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden">
        {CITIZEN_NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end}
            className={({ isActive }) => `flex flex-col items-center gap-0.5 py-2 text-[11px] font-bold ${isActive ? 'text-aqua-700' : 'text-ink-muted'}`}>
            {n.to === '/report'
              ? <span className="-mt-5 grid size-11 place-items-center rounded-full bg-aqua-600 text-white shadow-lift ring-4 ring-white"><n.icon className="size-5" aria-hidden /></span>
              : <n.icon className="size-5" aria-hidden />}
            {n.short ?? n.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function Footer() {
  return (
    <footer className="mt-16 bg-navy-950 pb-24 text-white/70 md:pb-0">
      <div className="container-page grid gap-8 py-12 sm:grid-cols-2 lg:grid-cols-4">
        <div className="lg:col-span-2">
          <Logo light />
          <p className="mt-3 max-w-sm text-sm">Understand Your Water. Protect Your Ecosystem. A citizen-friendly water monitoring prototype built for the OneAquaHealth IEEE Hackathon.</p>
        </div>
        <div>
          <h2 className="text-sm font-bold text-white">Learn more</h2>
          <ul className="mt-3 space-y-2 text-sm">
            <li><Link className="hover:text-white" to="/about">About</Link></li>
            <li><Link className="hover:text-white" to="/about#methodology">Methodology</Link></li>
            <li><Link className="hover:text-white" to="/about#limitations">Limitations</Link></li>
            <li><Link className="hover:text-white" to="/about#privacy">Privacy</Link></li>
          </ul>
        </div>
        <div>
          <h2 className="text-sm font-bold text-white">Project</h2>
          <ul className="mt-3 space-y-2 text-sm">
            <li><a className="hover:text-white" href={import.meta.env.VITE_GITHUB_URL || 'https://github.com/ANMOLSCRIPT/hydrosense'} target="_blank" rel="noreferrer">GitHub</a></li>
            <li><Link className="hover:text-white" to="/about#hackathon">Hackathon information</Link></li>
            <li><Link className="hover:text-white" to="/dashboard">Monitoring dashboard</Link></li>
            <li><a className="hover:text-white" href="/docs">API documentation</a></li>
          </ul>
        </div>
      </div>
      <div className="border-t border-white/10">
        <p className="container-page py-5 text-xs text-white/50">
          HydroSense is a monitoring and decision-support prototype. It does not certify water quality or drinking-water safety. Map data © OpenStreetMap contributors.
        </p>
      </div>
    </footer>
  )
}

const DASH_NAV = [
  { to: '/dashboard', label: 'Dashboard', icon: Gauge, end: true },
  { to: '/dashboard/sites', label: 'Sites', icon: MapPinned, end: true },
  { to: '/dashboard/devices', label: 'Devices', icon: Cpu },
  { to: '/dashboard/analytics', label: 'Analytics', icon: LineChart },
  { to: '/dashboard/insights', label: 'AI Insights', icon: BrainCircuit },
  { to: '/dashboard/alerts', label: 'Alerts', icon: Bell },
]

export function DashboardLayout() {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[240px_1fr]">
      <ScrollReset />
      <aside className="bg-navy-950 lg:sticky lg:top-0 lg:h-dvh">
        <div className="flex items-center justify-between gap-3 px-4 py-4 lg:block lg:px-5 lg:py-6">
          <Logo light />
          <p className="hidden text-[11px] font-bold tracking-[0.14em] text-white/40 uppercase lg:mt-6 lg:block">Monitoring</p>
          <div className="lg:hidden"><ModeToggle dark /></div>
        </div>
        <nav aria-label="Dashboard" className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:px-3">
          {DASH_NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}
              className={({ isActive }) => `flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${isActive ? 'bg-white/12 text-white' : 'text-white/60 hover:bg-white/6 hover:text-white'}`}>
              <n.icon className="size-4" aria-hidden /> {n.label}
            </NavLink>
          ))}
          <Link to="/" className="flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-semibold text-aqua-400 transition hover:bg-white/6 lg:mt-4">
            <Users className="size-4" aria-hidden /> Citizen view
          </Link>
        </nav>
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-[1000] hidden items-center justify-between border-b border-line bg-white/90 px-8 py-3 backdrop-blur lg:flex">
          <p className="text-sm font-semibold text-ink-soft">Monitoring dashboard <span className="text-ink-muted">· for monitoring, research and admin users</span></p>
          <ModeToggle />
        </header>
        <ModeBanner />
        <main id="main" className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8"><Outlet /></main>
      </div>
    </div>
  )
}

export function PageTitle({ eyebrow, title, children, actions }: { eyebrow?: string; title: string; children?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1 className="mt-1 text-2xl sm:text-3xl">{title}</h1>
        {children && <p className="mt-1.5 max-w-2xl text-sm text-ink-soft sm:text-base">{children}</p>}
      </div>
      {actions}
    </div>
  )
}
