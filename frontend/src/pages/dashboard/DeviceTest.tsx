import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageTitle } from '../../components/Layout'
import { Sparkline } from '../../components/TrendChart'
import { Disclosure } from '../../components/ui'
import { api, ApiError } from '../../lib/api'
import { clock, timeAgo } from '../../lib/format'

function Cell({ label, value, tone = 'text-navy-900' }: { label: string; value: React.ReactNode; tone?: string }) {
  return (
    <div className="rounded-2xl border border-line bg-white p-4">
      <dt className="text-xs font-bold tracking-wide text-ink-muted uppercase">{label}</dt>
      <dd className={`tabular mt-1 text-2xl font-extrabold ${tone}`}>{value}</dd>
    </div>
  )
}

export default function DeviceTest() {
  const [params, setParams] = useSearchParams()
  const deviceId = params.get('device') || 'HS-001'
  const [draft, setDraft] = useState(deviceId)
  useEffect(() => setDraft(deviceId), [deviceId])
  // Polls quickly regardless of mode: this page is for bench testing hardware.
  const device = useQuery({ queryKey: ['device', deviceId], queryFn: () => api.device(deviceId), refetchInterval: 3000, retry: false })
  const health = useQuery({ queryKey: ['health'], queryFn: api.health, refetchInterval: 10_000, retry: false })
  const d = device.data
  const latest = d?.latest
  const online = d?.status === 'online'
  const unknown = device.error instanceof ApiError && device.error.status === 404
  const apiOk = health.data?.status === 'ok'
  const series = d ? [...d.readings].reverse().map((r) => ({ t: r.timestamp, tds: r.tds_ppm, min: 0, max: 0, n: 1 })) : undefined

  return (
    <>
      <Link to="/dashboard/devices" className="inline-flex items-center gap-1.5 text-sm font-bold text-aqua-700 hover:underline"><ArrowLeft className="size-4" aria-hidden /> All devices</Link>
      <div className="mt-3" />
      <PageTitle eyebrow="Hardware test" title="Device test page" actions={
        <form onSubmit={(e) => { e.preventDefault(); if (draft.trim()) setParams({ device: draft.trim() }) }} className="flex items-center gap-2">
          <label htmlFor="dev" className="text-sm font-semibold text-ink-soft">Device ID</label>
          <input id="dev" value={draft} onChange={(e) => setDraft(e.target.value)} className="w-32 rounded-xl border border-line bg-white px-3 py-2 text-sm font-bold focus:border-aqua-400" />
          <button className="btn-dark px-4 py-2">Watch</button>
        </form>}>
        Live view of what a sensor node is sending, for debugging the physical prototype. Refreshes every 3 seconds.
      </PageTitle>

      <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Cell label="Device" value={deviceId} />
        <Cell label="Connection" value={unknown ? 'NO DATA YET' : online ? 'ONLINE' : 'OFFLINE'} tone={online ? 'text-ok' : 'text-idle'} />
        <Cell label="Raw ADC" value={latest?.raw_value ?? '—'} />
        <Cell label="Filtered value" value={latest?.filtered_value ?? '—'} />
        <Cell label="TDS" value={latest ? <>{Math.round(latest.tds_ppm)} <span className="text-sm font-semibold text-ink-muted">ppm</span></> : '—'} />
        <Cell label="Voltage" value={latest?.voltage != null ? <>{latest.voltage.toFixed(3)} <span className="text-sm font-semibold text-ink-muted">V</span></> : '—'} />
        <Cell label="Last upload" value={latest ? clock(latest.timestamp) : '—'} />
        <Cell label="API" value={health.isLoading ? '…' : apiOk ? 'CONNECTED' : 'UNREACHABLE'} tone={apiOk ? 'text-ok' : 'text-danger'} />
      </dl>

      {unknown ? (
        <div className="card mt-4 p-6 text-sm text-ink-soft">
          <h2 className="text-lg">Waiting for {deviceId}</h2>
          <p className="mt-2">No reading has arrived from this device yet. Power the ESP32 and check its serial monitor at 115200 baud. This page will update by itself as soon as the first reading is uploaded.</p>
          <ol className="mt-3 list-decimal space-y-1 pl-5">
            <li>Confirm <code>WIFI_SSID</code>, <code>WIFI_PASSWORD</code> and <code>API_URL</code> in <code>config.h</code>.</li>
            <li>The serial log should show <code>WiFi connected</code>, then <code>POST 201</code>.</li>
            <li><code>DEVICE_ID</code> must match the ID in the box above, and <code>SITE_ID</code> must be a registered live site (for example SITE-001).</li>
          </ol>
        </div>
      ) : (
        <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_320px]">
          <section className="card overflow-hidden" aria-labelledby="readings">
            <h2 id="readings" className="px-5 pt-5 text-lg">Recent readings</h2>
            <div className="mt-3 max-h-96 overflow-auto">
              <table className="tabular w-full text-left text-sm">
                <thead className="sticky top-0 bg-white text-xs font-bold tracking-wide text-ink-muted uppercase"><tr><th className="py-2 pl-5">Time</th><th>TDS (ppm)</th><th>Raw ADC</th><th>Filtered</th><th className="pr-5">Voltage</th></tr></thead>
                <tbody>{d?.readings.map((r, i) => (
                  <tr key={r.timestamp + i} className={`border-t border-line ${i === 0 ? 'animate-rise bg-aqua-50/60' : ''}`}>
                    <td className="py-2 pl-5">{new Date(r.timestamp).toLocaleTimeString()}</td><td className="font-bold text-navy-900">{r.tds_ppm}</td>
                    <td>{r.raw_value ?? '—'}</td><td>{r.filtered_value ?? '—'}</td><td className="pr-5">{r.voltage ?? '—'}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          </section>
          <section className="card p-5">
            <h2 className="text-lg">Signal</h2>
            <div className="mt-3"><Sparkline points={series} width={270} height={90} /></div>
            <dl className="mt-4 space-y-1.5 text-sm">
              <div className="flex justify-between"><dt className="text-ink-muted">Site</dt><dd className="font-bold">{d?.site_name ?? '—'}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Firmware</dt><dd className="font-bold">{d?.firmware_version ?? '—'}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Wi-Fi signal</dt><dd className="tabular font-bold">{d?.wifi_rssi != null ? `${d.wifi_rssi} dBm` : '—'}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Last seen</dt><dd className="font-bold">{timeAgo(d?.last_seen)}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Data store</dt><dd className="font-bold capitalize">{health.data?.store ?? '—'}</dd></div>
            </dl>
          </section>
        </div>
      )}
      <Disclosure summary="How to read these values" className="mt-4">
        <p><strong>Raw ADC</strong> is the mean of the ESP32's analog samples (0 to 4095). <strong>Filtered value</strong> is the same signal after outliers are removed and readings are smoothed. <strong>Voltage</strong> is the probe output the TDS estimate is calculated from. If Raw ADC is 0 or 4095 the probe is probably disconnected or wired to the wrong pin. See the calibration guide in the project documentation to adjust <code>TDS_CALIBRATION_FACTOR</code>.</p>
      </Disclosure>
    </>
  )
}
