// Lightweight, private-by-default participation tracking. Stored only in this
// browser; nothing is sent anywhere and no account is needed.
const KEY = 'hs-contributions'

export interface Badge { name: string; emoji: string; at: number; blurb: string }
export const BADGES: Badge[] = [
  { name: 'First Observation', emoji: '💧', at: 1, blurb: 'You shared your first observation.' },
  { name: 'Water Watcher', emoji: '🌊', at: 3, blurb: 'Three observations and counting.' },
  { name: 'Community Contributor', emoji: '🌱', at: 10, blurb: 'Ten observations for your community.' },
]

export function contributionCount(): number {
  try { return Number(localStorage.getItem(KEY)) || 0 } catch { return 0 }
}
export function recordContribution(): { count: number; earned: Badge | null } {
  const count = contributionCount() + 1
  try { localStorage.setItem(KEY, String(count)) } catch { /* private browsing */ }
  return { count, earned: BADGES.find((b) => b.at === count) ?? null }
}
export const currentBadge = (count: number) => [...BADGES].reverse().find((b) => count >= b.at) ?? null
export const nextBadge = (count: number) => BADGES.find((b) => count < b.at) ?? null
