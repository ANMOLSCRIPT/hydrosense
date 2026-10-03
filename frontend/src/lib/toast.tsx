import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type Kind = 'success' | 'error' | 'info'
interface Toast { id: number; kind: Kind; text: string }
const Ctx = createContext<(text: string, kind?: Kind) => void>(() => {})
export const useToast = () => useContext(Ctx)

const ICONS = { success: CircleCheck, error: CircleAlert, info: Info }
const TONES = { success: 'text-ok', error: 'text-danger', info: 'text-aqua-600' }

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), [])
  const push = useCallback((text: string, kind: Kind = 'info') => {
    const id = Date.now() + Math.random()
    setToasts((t) => [...t.slice(-2), { id, kind, text }])
    setTimeout(() => dismiss(id), 4500)
  }, [dismiss])
  return (
    <Ctx.Provider value={push}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-20 z-[2000] flex flex-col items-center gap-2 px-4 md:bottom-6">
        {toasts.map((t) => {
          const Icon = ICONS[t.kind]
          return (
            <div key={t.id} role="status" className="pointer-events-auto flex max-w-md animate-rise items-start gap-3 rounded-xl border border-line bg-white px-4 py-3 text-sm font-medium text-ink shadow-lift">
              <Icon className={`mt-0.5 size-5 shrink-0 ${TONES[t.kind]}`} aria-hidden />
              <span>{t.text}</span>
              <button onClick={() => dismiss(t.id)} aria-label="Dismiss" className="ml-1 text-ink-muted hover:text-ink"><X className="size-4" /></button>
            </div>
          )
        })}
      </div>
    </Ctx.Provider>
  )
}
