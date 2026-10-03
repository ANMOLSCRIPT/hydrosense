import { BrainCircuit } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ConfidenceBar, EvidenceTable, FactorList } from '../../components/cards'
import { PageTitle } from '../../components/Layout'
import { CardSkeleton, Disclaimer, Disclosure, EmptyState, ErrorState, StatusBadge, useLive } from '../../components/ui'
import { api } from '../../lib/api'
import { dateTime } from '../../lib/format'
import { useMode } from '../../lib/mode'
import { RISK, STATUS } from '../../lib/status'

export default function Insights() {
  const { mode } = useMode()
  const [picked, setPicked] = useState<string>()
  const list = useLive(['assessments', mode], () => api.assessments(mode))
  const selectedId = picked && list.data?.some((a) => a.site_id === picked) ? picked : list.data?.[0]?.site_id
  const detail = useLive(['assessment', selectedId], () => api.assessment(selectedId!), { enabled: !!selectedId })
  const a = detail.data

  return (
    <>
      <PageTitle eyebrow="AI Insights" title="HydroSense AI Assessment">
        Each assessment is built from fixed, explainable rules over sensor analytics and citizen observations. A language model, when enabled, only rewords the explanation.
      </PageTitle>
      {list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> : list.isLoading ? <div className="grid gap-4 lg:grid-cols-[300px_1fr]"><CardSkeleton lines={5} /><CardSkeleton lines={8} /></div>
        : !list.data?.length ? <EmptyState icon={<BrainCircuit className="size-6" aria-hidden />} title="Nothing to assess yet">Assessments appear once a site has readings or observations.</EmptyState> : (
        <div className="grid items-start gap-4 lg:grid-cols-[300px_1fr]">
          <ul className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible" aria-label="Sites">
            {list.data.map((x) => {
              const st = RISK[x.risk_level].status
              const on = x.site_id === selectedId
              return (
                <li key={x.site_id} className="shrink-0 lg:shrink">
                  <button onClick={() => setPicked(x.site_id)} aria-pressed={on}
                    className={`w-64 rounded-2xl border-2 bg-white p-4 text-left transition hover:border-aqua-400 lg:w-full ${on ? 'border-aqua-600 shadow-card' : 'border-line'}`}>
                    <span className="flex items-center justify-between gap-2"><span className="text-sm font-bold text-navy-900">{x.site_name}</span><span className="tabular text-xs font-bold text-ink-muted">{x.confidence}%</span></span>
                    <span className={`mt-1 block text-xs font-bold ${STATUS[st].text}`}>{x.assessment}</span>
                  </button>
                </li>
              )
            })}
          </ul>

          {!a ? <CardSkeleton lines={8} /> : (
            <article key={a.site_id} className="card animate-rise p-5 sm:p-7">
              <div className="grid gap-6 md:grid-cols-[1fr_240px]">
                <div>
                  <p className="text-sm font-semibold text-ink-muted">{a.site_name} · assessed {dateTime(a.timestamp)}</p>
                  <h2 className={`mt-1 text-3xl ${STATUS[RISK[a.risk_level].status].text}`}>{a.assessment}</h2>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <StatusBadge status={a.sensor_state} label={`Sensor: ${STATUS[a.sensor_state].label}`} />
                    <span className="rounded-full bg-idle-soft px-2.5 py-1 text-xs font-bold text-ink-soft">{a.source === 'llm' ? 'Explanation: AI language model' : 'Explanation: rule-based engine'}</span>
                  </div>
                </div>
                <ConfidenceBar value={a.confidence} />
              </div>

              <h3 className="mt-7 text-base">What contributed?</h3>
              <div className="mt-3"><FactorList factors={a.contributing_factors} /></div>

              <h3 className="mt-7 text-base">Explanation</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-ink">{a.explanation}</p>

              <h3 className="mt-7 text-base">Recommended next step</h3>
              <p className="mt-2 rounded-xl bg-aqua-50 p-4 text-[15px] font-medium text-ink">{a.recommendation}</p>

              <div className="mt-6 space-y-2">
                <Disclosure summary="Evidence (technical)"><EvidenceTable evidence={a.evidence} /></Disclosure>
                {a.history && a.history.length > 0 && (
                  <Disclosure summary="Assessment history">
                    <ol className="space-y-1.5">
                      {a.history.map((h) => (
                        <li key={h.timestamp} className="flex items-center justify-between gap-3 text-xs">
                          <span className="text-ink-muted">{dateTime(h.timestamp)}</span>
                          <span className="font-bold text-navy-900">{h.label}</span>
                          <span className="tabular text-ink-soft">confidence {h.confidence}% · score {h.anomaly_score}</span>
                        </li>
                      ))}
                    </ol>
                  </Disclosure>
                )}
              </div>
              <div className="mt-6 flex flex-wrap gap-3">
                <Link to={`/dashboard/sites/${a.site_id}`} className="btn-ghost">Site analytics</Link>
                <Link to={`/explore/${a.site_id}`} className="btn-ghost">Citizen view of this site</Link>
              </div>
            </article>
          )}
        </div>
      )}
      <div className="mt-6"><Disclaimer /></div>
    </>
  )
}
