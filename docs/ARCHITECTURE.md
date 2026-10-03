# Architecture

```text
            WATER
              │
        TDS probe (analog)
              │
   ESP32  ── sample ×30 → trim outliers → smooth → TDS estimate
              │  HTTPS POST /api/measurements  (buffered + retried when offline)
              ▼
   ┌──────────────────────────── Vercel ────────────────────────────┐
   │  FastAPI (Python function, api/index.py → backend/app)         │
   │   ingest → validate → store → analytics → alerts → assessment  │
   │                                                                │
   │  React + Vite SPA (static)  ── polls /api every 6 s (live)     │
   └───────────────┬────────────────────────────────┬───────────────┘
                   │ service-role key (server only) │ anon key (browser)
                   ▼                                ▼
        Supabase PostgreSQL (RLS)          Supabase Storage
        sites, devices, measurements,      observation-photos bucket
        observations, alerts, assessments  (citizen photo uploads)
```

## Components

| Layer | Technology | Location |
|---|---|---|
| Firmware | Arduino C++ on ESP32 | `firmware/hydrosense/` |
| API | FastAPI, Pydantic | `backend/app/main.py` |
| Application logic | Python | `backend/app/service.py` |
| Analytics and anomaly detection | Pure Python, no dependencies | `backend/app/analytics.py` |
| Assessment engine | Deterministic rules | `backend/app/assessment.py` |
| Optional LLM wording | Anthropic SDK | `backend/app/llm.py` |
| Data layer | Supabase (PostgreSQL + Storage) | `backend/app/store.py`, `supabase/migrations/` |
| Demo generator | Python | `backend/app/demo.py`, `scripts/seed_demo.py` |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query, Recharts, Leaflet | `frontend/` |

## Design decisions

- **One deployment.** The SPA and the FastAPI app ship in the same Vercel project, so the browser calls `/api` on the same origin: no CORS setup and a single public URL.
- **All writes go through the API.** The browser only holds the Supabase anon key, which can read public data and add a photo. Inserts into tables use the service-role key on the server after validation.
- **Analytics on read, cached on write.** Each site row caches its latest status so the map loads with one query. Full analytics are computed when a site is opened and after every ingested reading.
- **Demo data is real rows.** Demo sites are ordinary rows flagged `is_demo`. Their alerts and assessments were produced by replaying synthetic measurements through the same engines used for live data.
- **Demo time shift.** Seeded demo rows keep fixed timestamps and are shifted at read time so the newest demo reading is always 0 to 30 minutes old. Nothing is rewritten.
- **Store abstraction.** `SupabaseStore` is used whenever credentials are present. `MemoryStore` implements the same interface for tests and as a fallback, so the API runs with no credentials at all.
- **Concurrent reads.** Independent queries (different sites, unrelated tables) run on thread pools, one pool per nesting level so nested fan-out cannot deadlock. Writes stay sequential. Dropped keep-alive connections are retried.
- **Polling, not WebSockets.** Live mode refetches every 6 seconds, which is enough for a 10-second sensor interval and keeps the serverless backend stateless.

## Data flows

**Demo:** `demo.py` generator → Supabase → analytics → assessment → Vercel → citizen.

**Hardware:** water → TDS probe → ESP32 → Wi-Fi → `POST /api/measurements` → Supabase → analytics → alerts and assessment → dashboard and citizen pages.

**Citizen report:** browser → (optional) photo to Supabase Storage → `POST /api/observations` → Supabase → next assessment includes it.
