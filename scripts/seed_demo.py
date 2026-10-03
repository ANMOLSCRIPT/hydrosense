#!/usr/bin/env python3
"""Seed Supabase with the HydroSense demo dataset.

Replaces every demo site (and its readings, observations, alerts and
assessments). Live data is never touched. Run it again any time to reset the
demo, for example to clear test observations.

Needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in .env (server side only).
"""
import _env  # noqa: F401
from backend.app.demo import build_demo_dataset
from backend.app.store import SupabaseStore, supabase_credentials, utcnow

url, key = supabase_credentials()
if not (url and key):
    raise SystemExit("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in .env first.")
store = SupabaseStore(url, key)

data = build_demo_dataset(utcnow())
demo_sites = {s["id"] for s in data["sites"] if s["is_demo"]}
store.clear_demo()
for table in ("sites", "devices", "measurements", "observations", "alerts", "assessments"):
    rows = [r for r in data[table] if (r["id"] if table == "sites" else r["site_id"]) in demo_sites]
    keys = sorted({k for r in rows for k in r})          # PostgREST needs identical keys per batch
    store.bulk_insert(table, [{k: r.get(k) for k in keys} for r in rows])
    print(f"{table:13s} {len(rows):6d} rows")
print("Demo data seeded. Newest demo reading:", max(r["timestamp"] for r in data["measurements"]).isoformat())
