import { createClient, type SupabaseClient } from '@supabase/supabase-js'

// Only the public URL and anon key ever reach the browser. The anon key can
// read public data and add a photo to the observation-photos bucket; nothing else.
const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const anon = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined
const BUCKET = 'observation-photos'

let client: SupabaseClient | null = null
export const photosEnabled = Boolean(url && anon)
function supabase(): SupabaseClient {
  if (!client) client = createClient(url!, anon!, { auth: { persistSession: false } })
  return client
}

/** Shrink a phone photo before upload so it is quick on mobile data. */
export async function compressImage(file: File, maxSide = 1600, quality = 0.82): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not process this photo.'))), 'image/jpeg', quality),
  )
}

export async function uploadObservationPhoto(file: File, siteId: string): Promise<{ url: string; path: string }> {
  const blob = await compressImage(file)
  const path = `citizen/${siteId}/${Date.now()}-${crypto.randomUUID().slice(0, 8)}.jpg`
  const { error } = await supabase().storage.from(BUCKET).upload(path, blob, { contentType: 'image/jpeg', cacheControl: '31536000' })
  if (error) throw new Error("We couldn't upload your photo. You can still send the observation without it.")
  return { url: supabase().storage.from(BUCKET).getPublicUrl(path).data.publicUrl, path }
}
