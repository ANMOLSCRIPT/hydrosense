// Typed client for the HydroSense API.
export type Mode = 'demo' | 'live'
export type SiteStatus = 'stable' | 'attention' | 'unusual' | 'alert' | 'learning' | 'no_data'
export type RiskLevel = 'stable' | 'watch' | 'potential_stress' | 'elevated' | 'unknown'
export type RangeKey = '24h' | '7d' | '30d'

export interface Site {
  id: string
  name: string
  locality: string | null
  description: string | null
  latitude: number
  longitude: number
  is_demo: boolean
  status: SiteStatus
  status_label: string
  headline: string
  summary: string
  latest_tds: number | null
  latest_at: string | null
  baseline_tds: number | null
  deviation_percent: number | null
  anomaly_score: number
  active_alerts: number
}
export interface CitizenCopy { label: string; headline: string; summary: string; why: string; detail: string | null; action: string }
export interface Device {
  device_id: string
  site_id: string
  site_name?: string
  status: 'online' | 'offline'
  firmware_version: string | null
  last_seen: string | null
  wifi_rssi: number | null
  health: string
  latest_tds?: number | null
  is_demo: boolean
}
export interface Reading { timestamp: string; tds_ppm: number; raw_value: number | null; filtered_value: number | null; voltage: number | null }
export interface DeviceDetail extends Device { readings: Reading[]; latest: Reading | null }
export interface SiteDetail extends Site { citizen: CitizenCopy; trend: string; devices: Device[]; observation_count: number; disclaimer: string }
export interface SeriesPoint { t: string; tds: number; min: number; max: number; n: number }
export interface Series { site_id: string; range: RangeKey; bucket_seconds: number; baseline_tds: number | null; points: SeriesPoint[] }
export interface Analytics {
  site_id: string
  state: SiteStatus
  current_tds: number | null
  baseline_tds: number | null
  spread: number | null
  deviation_percent: number | null
  z_score: number | null
  trend: string
  trend_percent_per_day: number | null
  rate_of_change_ppm_per_hour: number | null
  rolling_mean_24h: number | null
  rolling_std_24h: number | null
  persistence: number
  anomaly_score: number
  score_parts: Record<string, number>
  history_days: number
  baseline_points: number
  latest_at: string | null
  stale: boolean
  anomalies: { t: string; tds: number; deviation_percent: number }[]
  methodology: string
}
export interface Observation {
  id: string
  site_id: string
  site_name: string
  timestamp: string
  water_clarity: 'clear' | 'slightly_cloudy' | 'very_cloudy' | 'not_sure'
  algae: 'yes' | 'no' | 'not_sure'
  waste: 'yes' | 'no' | 'not_sure'
  odor: 'none' | 'mild' | 'strong' | 'not_sure'
  aquatic_life: 'yes' | 'no' | 'not_sure'
  image_url: string | null
  comment: string | null
  is_demo: boolean
}
export type NewObservation = Pick<Observation, 'site_id' | 'water_clarity' | 'algae' | 'waste' | 'odor' | 'aquatic_life'> & {
  image_url?: string | null
  image_path?: string | null
  comment?: string | null
}
export interface Alert {
  id: string
  site_id: string
  site_name: string
  timestamp: string
  type: 'unusual_change' | 'significant_change' | 'gradual_trend'
  severity: 'low' | 'medium' | 'high'
  title: string
  message: string
  detail: string | null
  recommendation: string | null
  evidence: Record<string, number | string | null>
  status: 'active' | 'resolved'
  resolved_at: string | null
  resolution_note: string | null
  is_demo: boolean
}
export interface Factor { key: string; icon: string; label: string; detail: string; weight: number }
export interface Assessment {
  site_id: string
  site_name: string
  timestamp: string
  risk_level: RiskLevel
  assessment: string
  confidence: number
  anomaly_score: number
  contributing_factors: Factor[]
  explanation: string
  recommendation: string
  evidence: Record<string, number | string | boolean | null>
  observation_summary: Record<string, number>
  source: 'rules' | 'llm'
  sensor_state: SiteStatus
  disclaimer: string
  history?: { timestamp: string; risk_level: RiskLevel; label: string; confidence: number; anomaly_score: number }[]
}
export interface Stats { mode: Mode; sites: number; active_sensors: number; observations: number; potential_anomalies: number }
export interface Health { status: string; store: string; database: string; llm_enhancement: boolean; version: string; time: string }

const BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${BASE}/api${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } })
  } catch {
    throw new ApiError("We couldn't reach HydroSense. Please check your connection and try again.", 0)
  }
  if (!res.ok) {
    let message = 'Something went wrong on our side. Please try again in a moment.'
    try {
      const body = await res.json()
      if (typeof body.detail === 'string') message = body.detail
      else if (res.status === 422) message = 'Some of the information looks incomplete. Please check and try again.'
    } catch { /* keep the friendly default */ }
    throw new ApiError(message, res.status)
  }
  return res.json() as Promise<T>
}

export const api = {
  health: () => request<Health>('/health'),
  stats: (mode: Mode) => request<Stats>(`/stats?mode=${mode}`),
  sites: (mode: Mode) => request<Site[]>(`/sites?mode=${mode}`),
  site: (id: string) => request<SiteDetail>(`/sites/${id}`),
  series: (id: string, range: RangeKey) => request<Series>(`/sites/${id}/measurements?range=${range}`),
  analytics: (id: string) => request<Analytics>(`/sites/${id}/analytics`),
  observations: (mode: Mode, siteId?: string, limit = 30) =>
    request<Observation[]>(`/observations?mode=${mode}&limit=${limit}${siteId ? `&site_id=${siteId}` : ''}`),
  addObservation: (body: NewObservation) => request<Observation>('/observations', { method: 'POST', body: JSON.stringify(body) }),
  alerts: (mode: Mode, siteId?: string) => request<Alert[]>(`/alerts?mode=${mode}${siteId ? `&site_id=${siteId}` : ''}`),
  resolveAlert: (id: string, note?: string) => request<Alert>(`/alerts/${id}/resolve`, { method: 'POST', body: JSON.stringify({ note: note || null }) }),
  assessment: (id: string) => request<Assessment>(`/assessment/${id}`),
  assessments: (mode: Mode) => request<Assessment[]>(`/assessments?mode=${mode}`),
  devices: (mode: Mode) => request<Device[]>(`/devices?mode=${mode}`),
  device: (id: string) => request<DeviceDetail>(`/devices/${id}`),
}
