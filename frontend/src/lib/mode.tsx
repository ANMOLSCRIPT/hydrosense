import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import type { Mode } from './api'

interface ModeValue { mode: Mode; setMode: (m: Mode) => void; refresh: number }
const Ctx = createContext<ModeValue>({ mode: 'demo', setMode: () => {}, refresh: 30_000 })

function initial(): Mode {
  try {
    return localStorage.getItem('hs-mode') === 'live' ? 'live' : 'demo'
  } catch {
    return 'demo'
  }
}

export function ModeProvider({ children }: { children: ReactNode }) {
  const [mode, set] = useState<Mode>(initial)
  const setMode = useCallback((m: Mode) => {
    set(m)
    try { localStorage.setItem('hs-mode', m) } catch { /* private browsing */ }
  }, [])
  // Live hardware data is polled quickly; demo data barely changes.
  const value = useMemo(() => ({ mode, setMode, refresh: mode === 'live' ? 6_000 : 30_000 }), [mode, setMode])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export const useMode = () => useContext(Ctx)
