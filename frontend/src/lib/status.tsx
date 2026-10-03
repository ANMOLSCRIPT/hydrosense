import { CircleCheck, CircleHelp, Eye, Hourglass, TriangleAlert, Waves, type LucideIcon } from 'lucide-react'
import type { RiskLevel, SiteStatus } from './api'

// Every state has a colour AND an icon AND a text label, so meaning never
// depends on colour alone.
export interface StatusStyle { label: string; icon: LucideIcon; text: string; bg: string; soft: string; hex: string; emoji: string }

export const STATUS: Record<SiteStatus, StatusStyle> = {
  stable: { label: 'Stable', icon: CircleCheck, text: 'text-ok', bg: 'bg-ok', soft: 'bg-ok-soft', hex: '#1c8f5f', emoji: '🟢' },
  attention: { label: 'Needs attention', icon: Eye, text: 'text-watch', bg: 'bg-watch', soft: 'bg-watch-soft', hex: '#c18a00', emoji: '🟡' },
  unusual: { label: 'Unusual change', icon: Waves, text: 'text-change', bg: 'bg-change', soft: 'bg-change-soft', hex: '#d9601f', emoji: '🟠' },
  alert: { label: 'Active alert', icon: TriangleAlert, text: 'text-danger', bg: 'bg-danger', soft: 'bg-danger-soft', hex: '#c53030', emoji: '🔴' },
  learning: { label: 'Still learning', icon: Hourglass, text: 'text-idle', bg: 'bg-idle', soft: 'bg-idle-soft', hex: '#5c6f80', emoji: '⚪' },
  no_data: { label: 'Waiting for data', icon: CircleHelp, text: 'text-idle', bg: 'bg-idle', soft: 'bg-idle-soft', hex: '#5c6f80', emoji: '⚪' },
}
export const STATUS_ORDER: SiteStatus[] = ['alert', 'unusual', 'attention', 'stable', 'learning', 'no_data']

export const RISK: Record<RiskLevel, { label: string; status: SiteStatus }> = {
  stable: { label: 'No signs of stress', status: 'stable' },
  watch: { label: 'Worth watching', status: 'attention' },
  potential_stress: { label: 'Potential ecosystem stress', status: 'unusual' },
  elevated: { label: 'Elevated potential stress', status: 'alert' },
  unknown: { label: 'Not enough evidence yet', status: 'learning' },
}
export const SEVERITY: Record<'low' | 'medium' | 'high', SiteStatus> = { low: 'attention', medium: 'unusual', high: 'alert' }
