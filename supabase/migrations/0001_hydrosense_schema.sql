-- HydroSense database schema (Supabase PostgreSQL)
-- Safe to run more than once.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
create table if not exists public.sites (
  id                text primary key,                 -- e.g. SITE-001, DEMO-04
  name              text not null,
  description       text,
  locality          text,
  latitude          double precision not null,
  longitude         double precision not null,
  status            text not null default 'no_data'   -- HydroSense monitoring state (not a water-safety class)
                    check (status in ('stable','attention','unusual','alert','learning','no_data')),
  is_demo           boolean not null default false,   -- true = simulated data
  -- cached analytics so the map loads with one query
  latest_tds        numeric,
  latest_at         timestamptz,
  baseline_tds      numeric,
  deviation_percent numeric,
  anomaly_score     integer not null default 0,
  direction         text not null default 'none',
  created_at        timestamptz not null default now()
);

create table if not exists public.devices (
  id               uuid primary key default gen_random_uuid(),
  device_id        text not null unique,              -- e.g. HS-001
  site_id          text not null references public.sites(id) on delete cascade,
  status           text not null default 'offline' check (status in ('online','offline')),
  firmware_version text,
  wifi_rssi        integer,
  last_seen        timestamptz,
  is_demo          boolean not null default false,
  created_at       timestamptz not null default now()
);

create table if not exists public.measurements (
  id             bigint generated always as identity primary key,
  device_id      text not null,
  site_id        text not null references public.sites(id) on delete cascade,
  "timestamp"    timestamptz not null,
  tds_ppm        numeric not null check (tds_ppm >= 0 and tds_ppm <= 5000),
  raw_value      numeric,                             -- mean raw ADC value
  filtered_value numeric,                             -- filtered ADC value
  voltage        numeric,
  created_at     timestamptz not null default now()
);
create index if not exists measurements_site_time_idx on public.measurements (site_id, "timestamp" desc);
create index if not exists measurements_device_time_idx on public.measurements (device_id, "timestamp" desc);

create table if not exists public.observations (
  id            uuid primary key default gen_random_uuid(),
  site_id       text not null references public.sites(id) on delete cascade,
  "timestamp"   timestamptz not null default now(),
  water_clarity text not null check (water_clarity in ('clear','slightly_cloudy','very_cloudy','not_sure')),
  algae         text not null check (algae in ('yes','no','not_sure')),
  waste         text not null check (waste in ('yes','no','not_sure')),
  odor          text not null check (odor in ('none','mild','strong','not_sure')),
  aquatic_life  text not null check (aquatic_life in ('yes','no','not_sure')),
  image_url     text,                                 -- public URL in Supabase Storage
  image_path    text,                                 -- object path inside the bucket
  comment       text check (comment is null or char_length(comment) <= 500),
  is_demo       boolean not null default false,
  created_at    timestamptz not null default now()
);
create index if not exists observations_site_time_idx on public.observations (site_id, "timestamp" desc);

create table if not exists public.alerts (
  id              uuid primary key default gen_random_uuid(),
  site_id         text not null references public.sites(id) on delete cascade,
  "timestamp"     timestamptz not null default now(),
  type            text not null check (type in ('unusual_change','significant_change','gradual_trend')),
  severity        text not null check (severity in ('low','medium','high')),
  title           text not null,
  message         text not null,
  detail          text,
  recommendation  text,
  evidence        jsonb not null default '{}'::jsonb,
  status          text not null default 'active' check (status in ('active','resolved')),
  resolved_at     timestamptz,
  resolution_note text,
  is_demo         boolean not null default false,
  created_at      timestamptz not null default now()
);
create index if not exists alerts_site_status_idx on public.alerts (site_id, status, "timestamp" desc);

create table if not exists public.assessments (
  id                   uuid primary key default gen_random_uuid(),
  site_id              text not null references public.sites(id) on delete cascade,
  "timestamp"          timestamptz not null default now(),
  risk_level           text not null check (risk_level in ('stable','watch','potential_stress','elevated','unknown')),
  confidence           integer not null check (confidence between 0 and 100),
  anomaly_score        integer not null default 0,
  explanation          text not null,
  recommendation       text not null,
  contributing_factors jsonb not null default '[]'::jsonb,
  evidence             jsonb not null default '{}'::jsonb,
  source               text not null default 'rules' check (source in ('rules','llm')),
  is_demo              boolean not null default false,
  created_at           timestamptz not null default now()
);
create index if not exists assessments_site_time_idx on public.assessments (site_id, "timestamp" desc);

-- ---------------------------------------------------------------------------
-- Time-bucketed series for charts and analytics
-- ---------------------------------------------------------------------------
create or replace function public.site_series(p_site text, p_from timestamptz, p_to timestamptz, p_bucket integer)
returns table (t timestamptz, tds double precision, tds_min double precision, tds_max double precision, n bigint)
language sql stable
set search_path = public
as $$
  select to_timestamp(floor(extract(epoch from m."timestamp") / p_bucket) * p_bucket) as t,
         avg(m.tds_ppm)::double precision, min(m.tds_ppm)::double precision,
         max(m.tds_ppm)::double precision, count(*)
  from public.measurements m
  where m.site_id = p_site and m."timestamp" >= p_from and m."timestamp" < p_to
  group by 1
  order by 1;
$$;

-- ---------------------------------------------------------------------------
-- Row Level Security
-- Public data is readable by anyone (open environmental data).
-- Nothing is writable with the anon key: all writes go through the HydroSense
-- API, which validates input and uses the server-side service-role key.
-- ---------------------------------------------------------------------------
alter table public.sites        enable row level security;
alter table public.devices      enable row level security;
alter table public.measurements enable row level security;
alter table public.observations enable row level security;
alter table public.alerts       enable row level security;
alter table public.assessments  enable row level security;

do $$
declare tbl text;
begin
  foreach tbl in array array['sites','devices','measurements','observations','alerts','assessments'] loop
    execute format('drop policy if exists "Public read" on public.%I', tbl);
    execute format('create policy "Public read" on public.%I for select to anon, authenticated using (true)', tbl);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Storage: citizen photos
-- Public bucket, images only, 5 MB limit. Anyone may add a photo; nobody can
-- overwrite or delete one with the anon key.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('observation-photos', 'observation-photos', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Citizens can upload observation photos" on storage.objects;
create policy "Citizens can upload observation photos" on storage.objects
  for insert to anon, authenticated
  with check (bucket_id = 'observation-photos' and (storage.foldername(name))[1] = 'citizen');

-- The one real (non-demo) site used by the hardware prototype.
insert into public.sites (id, name, description, locality, latitude, longitude, status, is_demo)
values ('SITE-001', 'Live Sensor Site 01', 'The HydroSense hardware prototype: an ESP32 with a TDS probe reporting over Wi-Fi.',
        'NSUT campus, Dwarka, New Delhi', 28.6092, 77.0350, 'no_data', false)
on conflict (id) do nothing;
