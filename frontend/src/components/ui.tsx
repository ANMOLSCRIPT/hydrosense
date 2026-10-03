import { useQuery, type UseQueryOptions } from '@tanstack/react-query'
import { ChevronDown, CloudOff, FlaskConical, Info, RefreshCw } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { SiteStatus } from '../lib/api'
import { useMode } from '../lib/mode'
import { STATUS } from '../lib/status'

/** useQuery that keeps data fresh on the interval suited to the current mode. */
export function useLive<T>(key: unknown[], fn: () => Promise<T>, options?: Partial<UseQueryOptions<T>>) {
  const { refresh } = useMode()
  return useQuery<T>({ queryKey: key, queryFn: fn, refetchInterval: refresh, ...options })
}

export function StatusBadge({ status, label, size = 'md' }: { status: SiteStatus; label?: string; size?: 'sm' | 'md' | 'lg' }) {
  const s = STATUS[status]
  const Icon = s.icon
  const sizes = { sm: 'gap-1 px-2 py-0.5 text-xs', md: 'gap-1.5 px-2.5 py-1 text-xs', lg: 'gap-2 px-3.5 py-1.5 text-sm' }
  return (
    <span className={`inline-flex items-center rounded-full font-bold whitespace-nowrap ${s.soft} ${s.text} ${sizes[size]}`}>
      <Icon className={size === 'lg' ? 'size-4' : 'size-3.5'} aria-hidden />
      {label ?? s.label}
    </span>
  )
}

export function StatusDot({ status }: { status: SiteStatus }) {
  const s = STATUS[status]
  const Icon = s.icon
  return (
    <span className={`grid size-10 shrink-0 place-items-center rounded-full text-white ${s.bg}`}>
      <Icon className="size-5" aria-hidden />
    </span>
  )
}

export function DemoTag({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border border-aqua-200 bg-aqua-50 px-2 py-0.5 text-[11px] font-bold text-aqua-700 ${className}`}>
      <FlaskConical className="size-3" aria-hidden /> Demo data
    </span>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`skeleton ${className}`} aria-hidden />
}
export function CardSkeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="card p-5" role="status" aria-label="Loading">
      <Skeleton className="h-5 w-2/5" />
      {Array.from({ length: lines }, (_, i) => <Skeleton key={i} className={`mt-3 h-4 ${i % 2 ? 'w-3/5' : 'w-4/5'}`} />)}
    </div>
  )
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="card flex flex-col items-center px-6 py-12 text-center">
      <div className="grid size-14 place-items-center rounded-2xl bg-aqua-50 text-aqua-600">{icon ?? <Info className="size-6" aria-hidden />}</div>
      <h3 className="mt-4 text-lg">{title}</h3>
      {children && <p className="mt-1.5 max-w-sm text-sm text-ink-soft">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong. Please try again.'
  return (
    <div className="card flex flex-col items-center px-6 py-10 text-center" role="alert">
      <div className="grid size-14 place-items-center rounded-2xl bg-idle-soft text-idle"><CloudOff className="size-6" aria-hidden /></div>
      <h3 className="mt-4 text-lg">We couldn't load this right now</h3>
      <p className="mt-1.5 max-w-sm text-sm text-ink-soft">{message}</p>
      {onRetry && <button onClick={onRetry} className="btn-ghost mt-5"><RefreshCw className="size-4" aria-hidden /> Try again</button>}
    </div>
  )
}

/** Progressive disclosure: simple by default, detail on demand. */
export function Disclosure({ summary, children, defaultOpen = false, className = '' }: { summary: ReactNode; children: ReactNode; defaultOpen?: boolean; className?: string }) {
  return (
    <details open={defaultOpen} className={`group rounded-xl border border-line bg-white ${className}`}>
      <summary className="flex list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-navy-900 select-none hover:text-aqua-700 [&::-webkit-details-marker]:hidden">
        <span>{summary}</span>
        <ChevronDown className="size-4 shrink-0 text-ink-muted transition-transform duration-200 group-open:rotate-180" aria-hidden />
      </summary>
      <div className="animate-rise border-t border-line px-4 py-3 text-sm text-ink-soft">{children}</div>
    </details>
  )
}

/** Counts up once when it scrolls into view. */
export function AnimatedNumber({ value }: { value: number }) {
  const [shown, setShown] = useState(0)
  const ref = useRef<HTMLSpanElement>(null)
  const from = useRef(0)
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setShown(value); return }
    let frame = 0
    const start = performance.now()
    const begin = from.current
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / 900)
      setShown(Math.round(begin + (value - begin) * (1 - Math.pow(1 - p, 3))))
      if (p < 1) frame = requestAnimationFrame(tick)
      else from.current = value
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return <span ref={ref} aria-label={String(value)}>{shown.toLocaleString()}</span>
}

export const DISCLAIMER =
  'HydroSense is an environmental monitoring and decision-support prototype. TDS is only one indicator and cannot by itself determine overall water quality, pollution, ecosystem health, or drinking-water safety. HydroSense alerts indicate changes that may warrant further observation or field verification.'

export function Disclaimer({ compact = false }: { compact?: boolean }) {
  return (
    <aside className={`flex gap-3 rounded-xl border border-line bg-white text-ink-soft ${compact ? 'p-3 text-xs' : 'p-4 text-sm'}`}>
      <Info className="mt-0.5 size-4 shrink-0 text-aqua-600" aria-hidden />
      <p><strong className="text-navy-900">Important:</strong> {DISCLAIMER}</p>
    </aside>
  )
}

export function Segmented<T extends string>({ value, onChange, options, label }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[]; label: string }) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-xl bg-idle-soft p-1">
      {options.map((o) => (
        <button key={o.value} role="radio" aria-checked={value === o.value} onClick={() => onChange(o.value)}
          className={`rounded-lg px-3 py-1.5 text-xs font-bold transition ${value === o.value ? 'bg-white text-navy-900 shadow-sm' : 'text-ink-muted hover:text-navy-900'}`}>
          {o.label}
        </button>
      ))}
    </div>
  )
}
