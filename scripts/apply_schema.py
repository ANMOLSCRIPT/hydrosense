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
def connection_args(uri: str) -> dict:
    """Split the URI by hand so passwords with characters like / or # work unencoded."""
    rest = uri.split("://", 1)[1]
    credentials, _, location = rest.rpartition("@")
    user, _, password = credentials.partition(":")
    hostport, _, dbname = location.partition("/")
    host, _, port = hostport.partition(":")
    return {"user": user, "password": password, "host": host, "port": int(port or 5432),
            "dbname": dbname.split("?")[0] or "postgres", "sslmode": "require"}


with psycopg.connect(**connection_args(url), autocommit=True, prepare_threshold=None) as conn:
    for path in sorted((_env.ROOT / "supabase" / "migrations").glob("*.sql")):
        conn.execute(path.read_text())
        print("applied", path.name)
    conn.execute("notify pgrst, 'reload schema'")
print("Schema is up to date.")
