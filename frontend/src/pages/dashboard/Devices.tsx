import { Cpu, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../components/Layout'
import { CardSkeleton, DemoTag, EmptyState, ErrorState, useLive } from '../../components/ui'
import { api } from '../../lib/api'
import { clock, ppm, timeAgo } from '../../lib/format'
import { useMode } from '../../lib/mode'

export default function Devices() {
  const { mode } = useMode()
  const devices = useLive(['devices', mode], () => api.devices(mode))
  return (
    <>
      <PageTitle eyebrow="Devices" title="Device monitoring" actions={<Link to="/dashboard/devices/test" className="btn-dark"><Wrench className="size-4" aria-hidden /> Hardware test page</Link>}>
        Connection status and health of every HydroSense sensor node.
      </PageTitle>
      {devices.isError ? <ErrorState error={devices.error} onRetry={() => devices.refetch()} /> : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {devices.isLoading ? [0, 1, 2].map((i) => <CardSkeleton key={i} lines={4} />) : devices.data?.length ? devices.data.map((d) => {
            const online = d.status === 'online'
            return (
              <article key={d.device_id} className="card p-5 transition duration-200 hover:shadow-lift">
                <header className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <span className={`grid size-10 place-items-center rounded-xl ${online ? 'bg-ok-soft text-ok' : 'bg-idle-soft text-idle'}`}><Cpu className="size-5" aria-hidden /></span>
                    <div><h2 className="text-base">{d.device_id}</h2><p className="text-xs text-ink-muted">{d.site_name}</p></div>
                  </div>
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ${online ? 'bg-ok-soft text-ok' : 'bg-idle-soft text-idle'}`}>
                    <span className={`size-2 rounded-full ${online ? 'animate-pulse bg-ok' : 'bg-idle'}`} aria-hidden />{online ? 'Online' : 'Offline'}
                  </span>
                </header>
                <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                  <div><dt className="text-xs text-ink-muted">Last reading</dt><dd className="tabular font-bold text-navy-900">{d.latest_tds != null ? `${ppm(d.latest_tds)} ppm` : '—'}</dd></div>
                  <div><dt className="text-xs text-ink-muted">Last seen</dt><dd className="font-bold text-navy-900">{d.last_seen ? `${clock(d.last_seen)} · ${timeAgo(d.last_seen)}` : 'Never'}</dd></div>
                  <div><dt className="text-xs text-ink-muted">Firmware</dt><dd className="font-bold text-navy-900">{d.firmware_version ?? '—'}</dd></div>
                  <div><dt className="text-xs text-ink-muted">Health</dt><dd className="font-bold text-navy-900 capitalize">{d.health}{d.wifi_rssi != null ? ` · ${d.wifi_rssi} dBm` : ''}</dd></div>
                </dl>
                <footer className="mt-4 flex items-center justify-between border-t border-line pt-3">
                  {d.is_demo ? <DemoTag /> : <span className="text-xs font-bold text-ok">Physical device</span>}
                  <Link to={`/dashboard/devices/test?device=${d.device_id}`} className="text-xs font-bold text-aqua-700 hover:underline">Open test view</Link>
                </footer>
              </article>
            )
          }) : <div className="sm:col-span-2 xl:col-span-3"><EmptyState icon={<Cpu className="size-6" aria-hidden />} title="No devices yet">A device appears here the first time it sends a reading.</EmptyState></div>}
        </div>
      )}
    </>
  )
}
