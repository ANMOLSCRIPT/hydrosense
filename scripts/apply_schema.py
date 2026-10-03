#!/usr/bin/env python3
"""Apply supabase/migrations/*.sql to the Supabase database.

Needs a direct Postgres connection string in DATABASE_URL (Supabase dashboard:
Project Settings > Database > Connection string). Alternatively paste the SQL
file into the Supabase SQL editor; the result is the same.
"""
import os

import _env  # noqa: F401
import psycopg

url = os.environ.get("DATABASE_URL") or os.environ.get("POSTGRES_URL_NON_POOLING") or os.environ.get("POSTGRES_URL")
if not url:
    raise SystemExit("Set DATABASE_URL (or POSTGRES_URL_NON_POOLING) first.")
with psycopg.connect(url, autocommit=True, prepare_threshold=None) as conn:
    for path in sorted((_env.ROOT / "supabase" / "migrations").glob("*.sql")):
        conn.execute(path.read_text())
        print("applied", path.name)
    conn.execute("notify pgrst, 'reload schema'")
print("Schema is up to date.")
