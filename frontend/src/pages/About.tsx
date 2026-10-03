import { Cpu, Database, Eye, LineChart, ShieldCheck, Sparkles, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageTitle } from '../components/Layout'
import { Disclaimer, Disclosure } from '../components/ui'
import { STATUS } from '../lib/status'

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-28 border-t border-line pt-8">
      <h2 className="text-2xl">{title}</h2>
      <div className="mt-3 space-y-3 text-[15px] leading-relaxed text-ink-soft">{children}</div>
    </section>
  )
}

export default function About() {
  return (
    <div className="container-page max-w-3xl space-y-10 py-8 sm:py-12">
      <PageTitle eyebrow="About" title="Helping communities understand their water">
        HydroSense brings together a low-cost sensor in the water and the eyes of the people who live nearby, and turns both into information anyone can read.
      </PageTitle>

      <Disclaimer />

      <div className="grid gap-4 sm:grid-cols-2">
        {[
          { icon: Eye, title: 'See', text: "See what's happening around local water bodies on a simple map." },
          { icon: Cpu, title: 'Measure', text: 'Low-cost sensors provide continuous measurements.' },
          { icon: Users, title: 'Contribute', text: 'Citizens add what they observe at the water.' },
          { icon: Sparkles, title: 'Understand', text: 'Analytics and AI explain meaningful changes in plain language.' },
          { icon: ShieldCheck, title: 'Respond', text: 'Potential anomalies are highlighted with a suggested next step.' },
          { icon: Database, title: 'Open by design', text: 'Measurements and observations are public, open environmental data.' },
        ].map((c) => (
          <div key={c.title} className="card flex gap-4 p-5">
            <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-aqua-50 text-aqua-600"><c.icon className="size-5" aria-hidden /></span>
            <div><h3 className="text-base">{c.title}</h3><p className="mt-0.5 text-sm text-ink-soft">{c.text}</p></div>
          </div>
        ))}
      </div>

      <Section id="methodology" title="Methodology">
        <p>
          <strong className="text-navy-900">What the sensor measures.</strong> Each HydroSense node is an ESP32 microcontroller with a TDS probe. TDS (total dissolved solids) estimates how much dissolved material, such as salts and minerals, is in the water, in parts per million (ppm).
          The node takes many samples, removes outliers, smooths the result and sends one reading over Wi-Fi.
        </p>
        <p>
          <strong className="text-navy-900">What counts as "usual".</strong> Every site is compared only with its own history. The baseline is the median of that site's past readings, leaving out the most recent day so that an ongoing event cannot quietly become the new normal.
        </p>
        <p>
          <strong className="text-navy-900">How a change is detected.</strong> HydroSense looks at how far recent readings are from the baseline, whether that difference is larger than the site's normal ups and downs, whether it has lasted across several readings, and how quickly it happened.
          A single odd reading is never enough: a change has to persist before a site is flagged.
        </p>
        <div className="grid gap-2 sm:grid-cols-2">
          {(['stable', 'attention', 'unusual', 'alert'] as const).map((k) => {
            const Icon = STATUS[k].icon
            return (
              <div key={k} className="flex items-center gap-3 rounded-xl border border-line bg-white p-3">
                <span className={`grid size-8 place-items-center rounded-full text-white ${STATUS[k].bg}`}><Icon className="size-4" aria-hidden /></span>
                <span className="text-sm"><strong className="text-navy-900">{STATUS[k].emoji} {STATUS[k].label}</strong><br />
                  {{ stable: 'Readings match the usual pattern.', attention: 'A modest or slow drift from usual.', unusual: 'A clear, sustained change from usual.', alert: 'A large, sustained change from usual.' }[k]}
                </span>
              </div>
            )
          })}
        </div>
        <p>These are HydroSense monitoring states. They are <strong className="text-navy-900">not</strong> certified water-safety classifications.</p>
        <p>
          <strong className="text-navy-900">How the assessment works.</strong> The assessment combines the sensor evidence with what citizens reported in the last 72 hours (algae, floating waste, cloudiness, smell) using fixed, published rules.
          Confidence reflects how much evidence there is and whether the sensor and citizens agree. If an AI language model is enabled, it only rewrites the explanation in plain words from that same evidence. It cannot change the result or add measurements.
        </p>
        <Disclosure summary="Technical detail: the anomaly score">
          <p>The HydroSense anomaly score runs from 0 to 100 and is the sum of four parts:</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            <li><strong>Magnitude (55):</strong> size of the deviation from baseline, full weight at 70%.</li>
            <li><strong>Significance (15):</strong> deviation relative to the site's normal spread (robust z-score, full weight at 4).</li>
            <li><strong>Persistence (20):</strong> share of the last six evaluation points that are unusual (at least 15% and 2 spreads from baseline).</li>
            <li><strong>Dynamics (10):</strong> short-term rate of change or weekly trend, whichever is stronger.</li>
          </ul>
          <p className="mt-2">Below 20 is stable, 20 to 44 needs attention, 45 to 84 is an unusual change and 85 or more is an active alert. The two highest states also require persistence of at least 50%. This score is a transparent monitoring heuristic, not a validated environmental index. Full details are in the project documentation.</p>
        </Disclosure>
      </Section>

      <Section id="limitations" title="Limitations">
        <ul className="list-disc space-y-2 pl-5">
          <li>TDS is a single indicator. It does not detect bacteria, heavy metals, pesticides, oxygen levels or many other things that matter for water and ecosystem health.</li>
          <li>Low-cost TDS probes are not laboratory instruments. Readings depend on calibration and water temperature, and should be read as changes over time rather than exact values.</li>
          <li>HydroSense cannot tell you whether water is safe to drink, swim in or fish from.</li>
          <li>An alert means readings changed in a way that deserves a closer look. It is a potential anomaly, not confirmed pollution.</li>
          <li>Citizen observations are valuable but subjective, and are not verified individually.</li>
          <li>In Demo Mode all data is simulated and clearly labelled as Demo Data.</li>
        </ul>
      </Section>

      <Section id="privacy" title="Privacy">
        <p>You do not need an account to use HydroSense. When you submit an observation we store the site you chose, your answers, an optional comment and an optional photo. We do not ask for your name, email or phone number, and we do not store your precise location.</p>
        <p>Observations and photos are public. Please avoid including people's faces, licence plates or anything personal in photos or comments. Your contribution count and badges are kept only in your own browser.</p>
      </Section>

      <Section id="hackathon" title="Hackathon information">
        <p>HydroSense was built for the <strong className="text-navy-900">OneAquaHealth IEEE Hackathon</strong>. Its primary track is <strong className="text-navy-900">Track 6: Resilience Informatics</strong>, and it naturally touches Data-to-Insight, AI-Supported Assessment and Citizen Science UX.</p>
        <p className="flex flex-wrap gap-3 pt-2">
          <Link to="/explore" className="btn-primary">Explore Water</Link>
          <Link to="/dashboard" className="btn-ghost"><LineChart className="size-4" aria-hidden /> Monitoring dashboard</Link>
        </p>
      </Section>
    </div>
  )
}
