# Supabase setup

HydroSense uses Supabase for PostgreSQL (all tables) and Storage (citizen photos).

## 1. Create the project

Either create a project at supabase.com, or provision one from the linked Vercel project:

```bash
vercel integration add supabase
```

## 2. Apply the schema

Open the Supabase SQL editor, paste `supabase/migrations/0001_hydrosense_schema.sql` and run it. Or, with a direct connection string:

```bash
DATABASE_URL="postgresql://..." python scripts/apply_schema.py
```

The script accepts passwords containing characters such as `/` without URL-encoding.

This creates the tables, indexes, the `site_series` function, Row Level Security policies, the `observation-photos` bucket and the live site `SITE-001`. It is safe to run again.

## 3. Environment variables

| Variable | Where | Purpose |
|---|---|---|
| `SUPABASE_URL` | server | project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | server only | privileged writes from the API |
| `VITE_SUPABASE_URL` | browser | project URL |
| `VITE_SUPABASE_ANON_KEY` | browser | photo upload |

Put them in `.env` locally and in Vercel (`vercel env add`). The service-role key must never get a `VITE_` prefix.

## 4. Seed demo data

```bash
python scripts/seed_demo.py
```

Inserts 6 demo sites, 7 demo devices, 4,320 measurements (30 days, hourly), observations, alerts and assessments, all flagged `is_demo`. Re-running it replaces demo data only.

## Tables

| Table | Key columns |
|---|---|
| `sites` | `id` (text), name, description, locality, latitude, longitude, status, is_demo, cached analytics, created_at |
| `devices` | id, device_id (unique), site_id → sites, status, firmware_version, wifi_rssi, last_seen, created_at |
| `measurements` | id, device_id, site_id → sites, timestamp, tds_ppm, raw_value, filtered_value, voltage, created_at |
| `observations` | id, site_id → sites, timestamp, water_clarity, algae, waste, odor, aquatic_life, image_url, image_path, comment, created_at |
| `alerts` | id, site_id → sites, timestamp, type, severity, title, message, recommendation, evidence (jsonb), status, resolved_at, resolution_note, created_at |
| `assessments` | id, site_id → sites, timestamp, risk_level, confidence, anomaly_score, explanation, recommendation, contributing_factors (jsonb), evidence (jsonb), source, created_at |

Child tables cascade on site deletion. Enumerated columns have CHECK constraints.

## Security model

- RLS is enabled on every table. The only policy is public `SELECT`: this is open environmental data.
- There are no insert, update or delete policies, so the anon key cannot modify any table. All writes come from the API using the service-role key after validation.
- Storage: the `observation-photos` bucket is public-read, limited to JPEG, PNG and WebP up to 5 MB. The anon key may insert objects under `citizen/` only; it cannot overwrite or delete.
- The API only accepts `image_url` values that point into that bucket.
