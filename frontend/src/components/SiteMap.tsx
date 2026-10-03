import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MapContainer, Marker, TileLayer, Tooltip, useMap } from 'react-leaflet'
import type { Site } from '../lib/api'
import { STATUS } from '../lib/status'

function pin(site: Site, selected: boolean) {
  const s = STATUS[site.status]
  const Icon = s.icon
  const ring = site.status === 'alert' || site.status === 'unusual' ? `<span class="hs-ring" style="color:${s.hex}"></span>` : ''
  return L.divIcon({
    className: 'hs-marker',
    iconSize: [38, 38],
    iconAnchor: [19, 19],
    html: `<div class="hs-pin${selected ? ' is-selected' : ''}" style="background:${s.hex}">${ring}${renderToStaticMarkup(<Icon strokeWidth={2.5} />)}</div>`,
  })
}

function Fit({ sites, focus }: { sites: Site[]; focus?: Site }) {
  const map = useMap()
  const key = sites.map((s) => s.id).join()
  useEffect(() => {
    if (!sites.length) return
    if (sites.length === 1) map.setView([sites[0].latitude, sites[0].longitude], 14)
    else map.fitBounds(L.latLngBounds(sites.map((s) => [s.latitude, s.longitude])), { padding: [48, 48], maxZoom: 14 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, map])
  useEffect(() => {
    if (focus) map.flyTo([focus.latitude, focus.longitude], Math.max(map.getZoom(), 13), { duration: 0.6 })
  }, [focus?.id, map]) // eslint-disable-line react-hooks/exhaustive-deps
  return null
}

interface Props { sites: Site[]; selectedId?: string | null; onSelect?: (site: Site) => void; className?: string; interactive?: boolean }

export default function SiteMap({ sites, selectedId, onSelect, className = '', interactive = true }: Props) {
  const center = useMemo<[number, number]>(() => (sites.length ? [sites[0].latitude, sites[0].longitude] : [28.61, 77.2]), [sites])
  const focus = sites.find((s) => s.id === selectedId)
  return (
    <MapContainer center={center} zoom={11} className={className} scrollWheelZoom={interactive} dragging={interactive}
      zoomControl={interactive} doubleClickZoom={interactive} touchZoom={interactive} keyboard={interactive} attributionControl>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19}
      />
      <Fit sites={sites} focus={focus} />
      {sites.map((site) => (
        <Marker key={`${site.id}-${site.status}-${site.id === selectedId}`} position={[site.latitude, site.longitude]} icon={pin(site, site.id === selectedId)}
          title={`${site.name}: ${STATUS[site.status].label}`} alt={`${site.name}: ${STATUS[site.status].label}`}
          keyboard eventHandlers={{ click: () => onSelect?.(site), keypress: (e) => { if (e.originalEvent.key === 'Enter') onSelect?.(site) } }}>
          <Tooltip direction="top" offset={[0, -20]} opacity={1}>
            <strong>{site.name}</strong> · {STATUS[site.status].label}
          </Tooltip>
        </Marker>
      ))}
    </MapContainer>
  )
}
