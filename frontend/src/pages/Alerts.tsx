import { BellOff } from 'lucide-react'
import { useState } from 'react'
import { AlertCard } from '../components/cards'
import { PageTitle } from '../components/Layout'
import { CardSkeleton, Disclaimer, EmptyState, ErrorState, Segmented, useLive } from '../components/ui'
import { api } from '../lib/api'
import { useMode } from '../lib/mode'

export default function Alerts() {
  const { mode } = useMode()
  const [tab, setTab] = useState<'active' | 'resolved'>('active')
  const alerts = useLive(['alerts', mode], () => api.alerts(mode))
  const list = (alerts.data ?? []).filter((a) => a.status === tab)
  const activeCount = (alerts.data ?? []).filter((a) => a.status === 'active').length

  return (
    <div className="container-page max-w-3xl py-8 sm:py-12">
      <PageTitle eyebrow="Alerts" title="Changes worth knowing about"
        actions={<Segmented value={tab} onChange={setTab} label="Show alerts" options={[{ value: 'active', label: `Happening now${activeCount ? ` (${activeCount})` : ''}` }, { value: 'resolved', label: 'Resolved' }]} />}>
        HydroSense flags a site when its readings stay different from what is usual there. An alert means "worth a closer look", not confirmed pollution.
      </PageTitle>
      <div className="space-y-4">
        {alerts.isLoading ? <><CardSkeleton /><CardSkeleton /></>
          : alerts.isError ? <ErrorState error={alerts.error} onRetry={() => alerts.refetch()} />
          : list.length ? list.map((a) => <AlertCard key={a.id} alert={a} />)
          : <EmptyState icon={<BellOff className="size-6" aria-hidden />} title={tab === 'active' ? 'Nothing unusual right now' : 'No resolved alerts yet'}>
              {tab === 'active' ? 'All monitored sites are behaving as they usually do.' : 'When an alert is resolved it will be listed here.'}
            </EmptyState>}
      </div>
      <div className="mt-8"><Disclaimer compact /></div>
    </div>
  )
}
