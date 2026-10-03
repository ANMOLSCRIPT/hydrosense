import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Camera, Check, ImagePlus, LoaderCircle, LocateFixed, MapPin, Send, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { observationChips } from '../components/cards'
import { ErrorState, Skeleton, StatusBadge, useLive } from '../components/ui'
import { api, type NewObservation, type Site } from '../lib/api'
import { BADGES, contributionCount, currentBadge, nextBadge, recordContribution } from '../lib/contrib'
import { useMode } from '../lib/mode'
import { photosEnabled, uploadObservationPhoto } from '../lib/supabase'
import { useToast } from '../lib/toast'

type Answers = Partial<Omit<NewObservation, 'site_id'>>
interface Choice { value: string; label: string; emoji: string }
interface Question { key: keyof Answers; title: string; hint?: string; choices: Choice[] }

const YES_NO: Choice[] = [
  { value: 'yes', label: 'Yes', emoji: '✅' }, { value: 'no', label: 'No', emoji: '🚫' }, { value: 'not_sure', label: 'Not sure', emoji: '🤷' },
]
const QUESTIONS: Question[] = [
  { key: 'water_clarity', title: 'What does the water look like?', hint: 'Look at the water near the edge.', choices: [
    { value: 'clear', label: 'Clear', emoji: '💧' }, { value: 'slightly_cloudy', label: 'Slightly cloudy', emoji: '🌫' },
    { value: 'very_cloudy', label: 'Very cloudy', emoji: '🌁' }, { value: 'not_sure', label: 'Not sure', emoji: '🤷' }] },
  { key: 'algae', title: 'Do you see algae?', hint: 'Green film, scum or mats on or under the surface.', choices: YES_NO },
  { key: 'waste', title: 'Do you see floating waste?', hint: 'Plastic, litter, foam or oily patches.', choices: YES_NO },
  { key: 'odor', title: 'Any unusual smell?', choices: [
    { value: 'none', label: 'No unusual smell', emoji: '🙂' }, { value: 'mild', label: 'Mild', emoji: '😐' },
    { value: 'strong', label: 'Strong', emoji: '😖' }, { value: 'not_sure', label: 'Not sure', emoji: '🤷' }] },
  { key: 'aquatic_life', title: 'Can you see aquatic life?', hint: 'Fish, frogs, insects on the water, or water birds.', choices: YES_NO },
]
const TOTAL = QUESTIONS.length + 3 // site + questions + photo + comment

function distanceKm(a: GeolocationCoordinates, s: Site) {
  const rad = Math.PI / 180
  const dLat = (s.latitude - a.latitude) * rad, dLon = (s.longitude - a.longitude) * rad
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(s.latitude * rad) * Math.sin(dLon / 2) ** 2
  return 12742 * Math.asin(Math.sqrt(h))
}

export default function Report() {
  const { mode } = useMode()
  const toast = useToast()
  const qc = useQueryClient()
  const [params] = useSearchParams()
  const sites = useLive(['sites', mode], () => api.sites(mode))
  const [step, setStep] = useState(0)
  const [siteId, setSiteId] = useState<string | null>(params.get('site'))
  const [answers, setAnswers] = useState<Answers>({})
  const [photo, setPhoto] = useState<File | null>(null)
  const [comment, setComment] = useState('')
  const [coords, setCoords] = useState<GeolocationCoordinates | null>(null)
  const [locating, setLocating] = useState(false)
  const [done, setDone] = useState<{ count: number; badge: string | null } | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const preview = useMemo(() => (photo ? URL.createObjectURL(photo) : null), [photo])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])
  useEffect(() => { if (params.get('site')) setStep(1) }, []) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { headingRef.current?.focus() }, [step])

  const site = sites.data?.find((s) => s.id === siteId)
  const ordered = useMemo(() => {
    const list = [...(sites.data ?? [])]
    return coords ? list.sort((a, b) => distanceKm(coords, a) - distanceKm(coords, b)) : list
  }, [sites.data, coords])

  const locate = () => {
    if (!navigator.geolocation) return toast('Location is not available on this device. Please pick a site from the list.', 'info')
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => { setCoords(pos.coords); setLocating(false) },
      () => { setLocating(false); toast("We couldn't get your location. Please pick a site from the list.", 'info') },
      { timeout: 8000 },
    )
  }

  const submit = useMutation({
    mutationFn: async () => {
      let image: { url: string; path: string } | null = null
      if (photo && photosEnabled) {
        try { image = await uploadObservationPhoto(photo, siteId!) } catch (e) { toast((e as Error).message, 'error') }
      }
      return api.addObservation({ site_id: siteId!, ...(answers as Required<Answers>), image_url: image?.url, image_path: image?.path, comment: comment.trim() || null })
    },
    onSuccess: () => {
      const { count, earned } = recordContribution()
      setDone({ count, badge: earned?.name ?? null })
      if (earned) toast(`${earned.emoji} New badge: ${earned.name}`, 'success')
      qc.invalidateQueries({ queryKey: ['observations'] })
      qc.invalidateQueries({ queryKey: ['stats'] })
      qc.invalidateQueries({ queryKey: ['assessment'] })
    },
    onError: (e) => toast((e as Error).message, 'error'),
  })

  const choose = (key: keyof Answers, value: string) => {
    setAnswers((a) => ({ ...a, [key]: value }))
    setTimeout(() => setStep((s) => s + 1), 180) // brief pause so the selection is visible
  }
  const reset = () => { setStep(0); setSiteId(null); setAnswers({}); setPhoto(null); setComment(''); setDone(null); submit.reset() }

  // ---- Thank-you screen ---------------------------------------------------
  if (done && site) {
    const badge = currentBadge(done.count), next = nextBadge(done.count)
    return (
      <div className="container-page max-w-xl py-10 text-center sm:py-16">
        <div className="mx-auto grid size-20 animate-pop place-items-center rounded-full bg-ok text-white shadow-lift"><Check className="size-10" strokeWidth={3} aria-hidden /></div>
        <h1 ref={headingRef} tabIndex={-1} className="mt-6 text-3xl outline-none">Thank you for helping monitor your local water.</h1>
        <p className="mt-2 text-base text-ink-soft">Your observation has been added to HydroSense.</p>
        <ul className="mt-5 flex flex-wrap justify-center gap-1.5">
          {observationChips(answers as Required<Answers>).map((c) => <li key={c.text} className="rounded-full bg-aqua-50 px-3 py-1 text-sm font-semibold"><span aria-hidden>{c.emoji}</span> {c.text}</li>)}
        </ul>
        <section className="card mt-8 p-5 text-left" aria-label="Community contribution">
          <p className="eyebrow">Community contribution</p>
          <p className="mt-2 text-sm text-ink-soft">You've helped document</p>
          <p className="text-2xl font-extrabold text-navy-900">{done.count} water observation{done.count === 1 ? '' : 's'}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {BADGES.map((b) => (
              <span key={b.name} title={b.blurb} className={`rounded-full px-3 py-1 text-xs font-bold ${done.count >= b.at ? 'bg-aqua-600 text-white' : 'bg-idle-soft text-ink-muted'}`}>{b.emoji} {b.name}</span>
            ))}
          </div>
          {next && <p className="mt-3 text-xs text-ink-muted">{next.at - done.count} more to reach {next.name}{badge ? '' : ''}.</p>}
        </section>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link to={`/explore/${site.id}`} className="btn-primary">See {site.name}</Link>
          <button onClick={reset} className="btn-ghost">Report another</button>
        </div>
      </div>
    )
  }

  const q = step >= 1 && step <= QUESTIONS.length ? QUESTIONS[step - 1] : null
  const photoStep = step === QUESTIONS.length + 1
  const commentStep = step === QUESTIONS.length + 2

  return (
    <div className="container-page max-w-xl py-6 sm:py-10">
      {/* Progress */}
      <div className="flex items-center gap-3">
        <button onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0} aria-label="Previous question"
          className="grid size-10 shrink-0 place-items-center rounded-full border border-line bg-white text-navy-900 transition hover:bg-aqua-50 disabled:invisible"><ArrowLeft className="size-4" /></button>
        <div className="flex-1">
          <div className="h-2 overflow-hidden rounded-full bg-aqua-100" role="progressbar" aria-valuenow={step + 1} aria-valuemin={1} aria-valuemax={TOTAL} aria-label="Progress">
            <div className="h-full rounded-full bg-aqua-600 transition-[width] duration-300" style={{ width: `${((step + 1) / TOTAL) * 100}%` }} />
          </div>
          <p className="mt-1 text-xs font-semibold text-ink-muted">Step {step + 1} of {TOTAL}{site && step > 0 ? ` · ${site.name}` : ''}</p>
        </div>
      </div>

      <div key={step} className="mt-8 animate-rise">
        {/* Step 1: where */}
        {step === 0 && (
          <>
            <h1 ref={headingRef} tabIndex={-1} className="text-3xl outline-none">Where are you?</h1>
            <p className="mt-1.5 text-ink-soft">Pick the water site you're at. It takes less than a minute from here.</p>
            <button onClick={locate} disabled={locating} className="btn-ghost mt-5 w-full">
              {locating ? <LoaderCircle className="size-4 animate-spin" aria-hidden /> : <LocateFixed className="size-4" aria-hidden />} {coords ? 'Sorted by distance from you' : 'Find sites near me'}
            </button>
            {sites.isError ? <div className="mt-4"><ErrorState error={sites.error} onRetry={() => sites.refetch()} /></div> : (
              <ul className="mt-4 space-y-2.5">
                {sites.isLoading && [0, 1, 2].map((i) => <li key={i}><Skeleton className="h-[4.5rem] w-full rounded-2xl" /></li>)}
                {ordered.map((s) => (
                  <li key={s.id}>
                    <button onClick={() => { setSiteId(s.id); setStep(1) }} aria-pressed={siteId === s.id}
                      className={`flex w-full items-center gap-3 rounded-2xl border-2 bg-white p-4 text-left transition hover:border-aqua-400 hover:shadow-card active:scale-[0.99] ${siteId === s.id ? 'border-aqua-600' : 'border-line'}`}>
                      <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-aqua-50 text-aqua-600"><MapPin className="size-5" aria-hidden /></span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-bold text-navy-900">{s.name}</span>
                        <span className="block truncate text-sm text-ink-muted">{s.locality}{coords ? ` · ${distanceKm(coords, s).toFixed(1)} km away` : ''}</span>
                      </span>
                      <StatusBadge status={s.status} size="sm" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}

        {/* Steps 2-6: big tappable answers */}
        {q && (
          <>
            <h1 ref={headingRef} tabIndex={-1} className="text-3xl outline-none">{q.title}</h1>
            {q.hint && <p className="mt-1.5 text-ink-soft">{q.hint}</p>}
            <div role="radiogroup" aria-label={q.title} className={`mt-6 grid gap-3 ${q.choices.length === 4 ? 'grid-cols-2' : 'grid-cols-1 sm:grid-cols-3'}`}>
              {q.choices.map((c) => {
                const selected = answers[q.key] === c.value
                return (
                  <button key={c.value} role="radio" aria-checked={selected} onClick={() => choose(q.key, c.value)}
                    className={`flex min-h-28 flex-col items-center justify-center gap-2 rounded-2xl border-2 bg-white p-4 text-center transition hover:-translate-y-0.5 hover:border-aqua-400 hover:shadow-card active:scale-[0.98] ${selected ? 'border-aqua-600 bg-aqua-50 shadow-card' : 'border-line'}`}>
                    <span className="text-4xl" aria-hidden>{c.emoji}</span>
                    <span className="text-base font-bold text-navy-900">{c.label}</span>
                  </button>
                )
              })}
            </div>
          </>
        )}

        {/* Step 7: photo */}
        {photoStep && (
          <>
            <h1 ref={headingRef} tabIndex={-1} className="text-3xl outline-none">Add a photo</h1>
            <p className="mt-1.5 text-ink-soft">Optional. A picture of the water helps others see what you see. Please avoid faces.</p>
            <input ref={fileRef} type="file" accept="image/*" className="sr-only" id="photo" aria-label="Choose a photo"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) setPhoto(f); e.target.value = '' }} />
            {!photosEnabled ? (
              <p className="mt-6 rounded-2xl bg-idle-soft p-5 text-sm text-ink-soft">Photo upload isn't available right now. You can continue without one.</p>
            ) : preview ? (
              <div className="relative mt-6 overflow-hidden rounded-2xl border border-line">
                <img src={preview} alt="Your photo of the water" className="max-h-80 w-full object-cover" />
                <button onClick={() => setPhoto(null)} className="btn absolute top-3 right-3 bg-white/95 px-3 py-2 text-xs text-danger shadow-card"><Trash2 className="size-4" aria-hidden /> Remove</button>
              </div>
            ) : (
              <label htmlFor="photo" className="mt-6 flex min-h-44 cursor-pointer flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-aqua-200 bg-aqua-50/60 p-6 text-center transition hover:border-aqua-400 hover:bg-aqua-50">
                <span className="grid size-14 place-items-center rounded-full bg-aqua-600 text-white"><Camera className="size-6" aria-hidden /></span>
                <span className="font-bold text-navy-900">Take or choose a photo</span>
                <span className="flex items-center gap-1 text-xs text-ink-muted"><ImagePlus className="size-3.5" aria-hidden /> Use your camera or pick from your gallery</span>
              </label>
            )}
            <button onClick={() => setStep((s) => s + 1)} className="btn-primary mt-6 w-full py-3.5 text-base">{photo ? 'Continue' : 'Skip'} <ArrowRight className="size-4" aria-hidden /></button>
          </>
        )}

        {/* Step 8: comment + send */}
        {commentStep && (
          <>
            <h1 ref={headingRef} tabIndex={-1} className="text-3xl outline-none">Anything else?</h1>
            <p className="mt-1.5 text-ink-soft">Optional. Add anything you think is worth noting.</p>
            <label htmlFor="comment" className="sr-only">Your comment</label>
            <textarea id="comment" value={comment} onChange={(e) => setComment(e.target.value.slice(0, 500))} rows={4} placeholder="For example: the water level looks lower than last week."
              className="mt-6 w-full rounded-2xl border-2 border-line bg-white p-4 text-base placeholder:text-ink-muted focus:border-aqua-400" />
            <p className="mt-1 text-right text-xs text-ink-muted">{comment.length}/500</p>
            <button onClick={() => submit.mutate()} disabled={submit.isPending || !siteId} className="btn-primary mt-4 w-full py-3.5 text-base">
              {submit.isPending ? <><LoaderCircle className="size-5 animate-spin" aria-hidden /> Sending…</> : <><Send className="size-4" aria-hidden /> Send my observation</>}
            </button>
            <p className="mt-3 text-center text-xs text-ink-muted">No account needed. Observations are public and anonymous.</p>
          </>
        )}
      </div>

      {step === 0 && contributionCount() > 0 && (
        <p className="mt-8 rounded-2xl bg-aqua-50 p-4 text-sm text-ink">
          <strong>Community contribution:</strong> you've helped document {contributionCount()} water observation{contributionCount() === 1 ? '' : 's'}
          {currentBadge(contributionCount()) ? ` · ${currentBadge(contributionCount())!.emoji} ${currentBadge(contributionCount())!.name}` : ''}
        </p>
      )}
    </div>
  )
}
