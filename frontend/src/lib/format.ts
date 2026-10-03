export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return 'never'
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 45) return 'just now'
  if (s < 3600) return plural(Math.round(s / 60), 'minute')
  if (s < 86400) return plural(Math.round(s / 3600), 'hour')
  if (s < 86400 * 14) return plural(Math.round(s / 86400), 'day')
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}
const plural = (n: number, unit: string) => `${n} ${unit}${n === 1 ? '' : 's'} ago`

export const clock = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : '—'

export const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—'

export const ppm = (v: number | null | undefined) => (v == null ? '—' : Math.round(v).toLocaleString())

export const signed = (v: number | null | undefined, digits = 0) =>
  v == null ? '—' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toFixed(digits)}`
