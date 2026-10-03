"""Persistence layer.

SupabaseStore is the production data layer (Supabase PostgreSQL via the
service-role key, server side only). MemoryStore implements the same interface
in memory so the API and tests can run with no credentials at all.
"""
from __future__ import annotations

import os
import uuid
from datetime import datetime, timezone
from typing import Any

TS_FIELDS = ("timestamp", "created_at", "last_seen", "resolved_at", "latest_at", "t")
TABLES = ("sites", "devices", "measurements", "observations", "alerts", "assessments")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def parse_ts(value: Any) -> datetime | None:
    if value is None or isinstance(value, datetime):
        return value
    dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _load(row: dict) -> dict:
    for f in TS_FIELDS:
        if row.get(f) is not None:
            row[f] = parse_ts(row[f])
    return row


def _dump(row: dict) -> dict:
    return {k: (v.isoformat() if isinstance(v, datetime) else v) for k, v in row.items()}


class MemoryStore:
    """In-memory store with the same interface as SupabaseStore."""

    kind = "memory"

    def __init__(self) -> None:
        self.t: dict[str, list[dict]] = {name: [] for name in TABLES}
        self._seq = 0

    # -- generic -----------------------------------------------------------
    def bulk_insert(self, table: str, rows: list[dict]) -> None:
        for r in rows:
            row = dict(r)
            if table == "measurements" and "id" not in row:
                self._seq += 1
                row["id"] = self._seq
            row.setdefault("id", str(uuid.uuid4()))
            row.setdefault("created_at", utcnow())
            self.t[table].append(row)

    def clear_demo(self) -> None:
        demo_sites = {s["id"] for s in self.t["sites"] if s.get("is_demo")}
        for name in TABLES:
            key = "id" if name == "sites" else "site_id"
            self.t[name] = [r for r in self.t[name] if r.get(key) not in demo_sites]

    def ping(self) -> bool:
        return True

    # -- sites -------------------------------------------------------------
    def list_sites(self, is_demo: bool | None = None) -> list[dict]:
        rows = [dict(s) for s in self.t["sites"] if is_demo is None or bool(s.get("is_demo")) == is_demo]
        return sorted(rows, key=lambda s: s["id"])

    def get_site(self, site_id: str) -> dict | None:
        return next((dict(s) for s in self.t["sites"] if s["id"] == site_id), None)

    def update_site(self, site_id: str, fields: dict) -> None:
        for s in self.t["sites"]:
            if s["id"] == site_id:
                s.update(fields)

    # -- devices -----------------------------------------------------------
    def list_devices(self, site_ids: list[str] | None = None) -> list[dict]:
        rows = [dict(d) for d in self.t["devices"] if site_ids is None or d["site_id"] in site_ids]
        return sorted(rows, key=lambda d: d["device_id"])

    def get_device(self, device_id: str) -> dict | None:
        return next((dict(d) for d in self.t["devices"] if d["device_id"] == device_id), None)

    def upsert_device(self, device_id: str, fields: dict) -> None:
        for d in self.t["devices"]:
            if d["device_id"] == device_id:
                d.update(fields)
                return
        self.bulk_insert("devices", [{"device_id": device_id, **fields}])

    # -- measurements ------------------------------------------------------
    def insert_measurement(self, row: dict) -> dict:
        self.bulk_insert("measurements", [row])
        return dict(self.t["measurements"][-1])

    def history(self, site_id: str, start: datetime, end: datetime, bucket_seconds: int) -> list[dict]:
        buckets: dict[int, list[float]] = {}
        for m in self.t["measurements"]:
            if m["site_id"] == site_id and start <= m["timestamp"] < end:
                key = int(m["timestamp"].timestamp() // bucket_seconds) * bucket_seconds
                buckets.setdefault(key, []).append(float(m["tds_ppm"]))
        return [
            {"t": datetime.fromtimestamp(k, timezone.utc), "tds": sum(v) / len(v), "tds_min": min(v), "tds_max": max(v), "n": len(v)}
            for k, v in sorted(buckets.items())
        ]

    def recent(self, site_id: str, limit: int = 200, device_id: str | None = None) -> list[dict]:
        rows = [dict(m) for m in self.t["measurements"]
                if m["site_id"] == site_id and (device_id is None or m["device_id"] == device_id)]
        rows.sort(key=lambda m: m["timestamp"])
        return rows[-limit:]

    # -- observations ------------------------------------------------------
    def insert_observation(self, row: dict) -> dict:
        self.bulk_insert("observations", [row])
        return dict(self.t["observations"][-1])

    def list_observations(self, site_ids: list[str], limit: int = 50) -> list[dict]:
        rows = [dict(o) for o in self.t["observations"] if o["site_id"] in site_ids]
        rows.sort(key=lambda o: o["timestamp"], reverse=True)
        return rows[:limit]

    def count_observations(self, site_ids: list[str]) -> int:
        return sum(o["site_id"] in site_ids for o in self.t["observations"])

    # -- alerts ------------------------------------------------------------
    def list_alerts(self, site_ids: list[str], status: str | None = None, limit: int = 100) -> list[dict]:
        rows = [dict(a) for a in self.t["alerts"]
                if a["site_id"] in site_ids and (status is None or a["status"] == status)]
        rows.sort(key=lambda a: a["timestamp"], reverse=True)
        return rows[:limit]

    def get_alert(self, alert_id: str) -> dict | None:
        return next((dict(a) for a in self.t["alerts"] if str(a["id"]) == alert_id), None)

    def insert_alert(self, row: dict) -> dict:
        self.bulk_insert("alerts", [row])
        return dict(self.t["alerts"][-1])

    def update_alert(self, alert_id: str, fields: dict) -> None:
        for a in self.t["alerts"]:
            if str(a["id"]) == alert_id:
                a.update(fields)

    # -- assessments -------------------------------------------------------
    def insert_assessment(self, row: dict) -> None:
        self.bulk_insert("assessments", [row])

    def list_assessments(self, site_id: str, limit: int = 20) -> list[dict]:
        rows = [dict(a) for a in self.t["assessments"] if a["site_id"] == site_id]
        rows.sort(key=lambda a: a["timestamp"], reverse=True)
        return rows[:limit]


class SupabaseStore:
    """Supabase PostgreSQL through the server-side service-role key."""

    kind = "supabase"

    def __init__(self, url: str, key: str) -> None:
        from supabase import create_client

        self.db = create_client(url, key)

    def _rows(self, query) -> list[dict]:
        return [_load(r) for r in (query.execute().data or [])]

    # -- generic -----------------------------------------------------------
    def bulk_insert(self, table: str, rows: list[dict]) -> None:
        for i in range(0, len(rows), 500):
            self.db.table(table).insert([_dump(r) for r in rows[i:i + 500]]).execute()

    def clear_demo(self) -> None:
        ids = [s["id"] for s in self.list_sites(True)]
        if ids:  # child rows are removed by ON DELETE CASCADE
            self.db.table("sites").delete().in_("id", ids).execute()

    def ping(self) -> bool:
        self.db.table("sites").select("id").limit(1).execute()
        return True

    # -- sites -------------------------------------------------------------
    def list_sites(self, is_demo: bool | None = None) -> list[dict]:
        q = self.db.table("sites").select("*").order("id")
        if is_demo is not None:
            q = q.eq("is_demo", is_demo)
        return self._rows(q)

    def get_site(self, site_id: str) -> dict | None:
        rows = self._rows(self.db.table("sites").select("*").eq("id", site_id).limit(1))
        return rows[0] if rows else None

    def update_site(self, site_id: str, fields: dict) -> None:
        self.db.table("sites").update(_dump(fields)).eq("id", site_id).execute()

    # -- devices -----------------------------------------------------------
    def list_devices(self, site_ids: list[str] | None = None) -> list[dict]:
        q = self.db.table("devices").select("*").order("device_id")
        if site_ids is not None:
            q = q.in_("site_id", site_ids or ["-"])
        return self._rows(q)

    def get_device(self, device_id: str) -> dict | None:
        rows = self._rows(self.db.table("devices").select("*").eq("device_id", device_id).limit(1))
        return rows[0] if rows else None

    def upsert_device(self, device_id: str, fields: dict) -> None:
        self.db.table("devices").upsert(_dump({"device_id": device_id, **fields}), on_conflict="device_id").execute()

    # -- measurements ------------------------------------------------------
    def insert_measurement(self, row: dict) -> dict:
        data = self.db.table("measurements").insert(_dump(row)).execute().data
        return _load(data[0])

    def history(self, site_id: str, start: datetime, end: datetime, bucket_seconds: int) -> list[dict]:
        data = self.db.rpc("site_series", {
            "p_site": site_id, "p_from": start.isoformat(), "p_to": end.isoformat(), "p_bucket": bucket_seconds,
        }).execute().data or []
        return [_load(r) for r in data]

    def recent(self, site_id: str, limit: int = 200, device_id: str | None = None) -> list[dict]:
        q = self.db.table("measurements").select("*").eq("site_id", site_id)
        if device_id:
            q = q.eq("device_id", device_id)
        rows = self._rows(q.order("timestamp", desc=True).limit(limit))
        return list(reversed(rows))

    # -- observations ------------------------------------------------------
    def insert_observation(self, row: dict) -> dict:
        data = self.db.table("observations").insert(_dump(row)).execute().data
        return _load(data[0])

    def list_observations(self, site_ids: list[str], limit: int = 50) -> list[dict]:
        if not site_ids:
            return []
        q = self.db.table("observations").select("*").in_("site_id", site_ids)
        return self._rows(q.order("timestamp", desc=True).limit(limit))

    def count_observations(self, site_ids: list[str]) -> int:
        if not site_ids:
            return 0
        res = self.db.table("observations").select("id", count="exact").in_("site_id", site_ids).limit(1).execute()
        return res.count or 0

    # -- alerts ------------------------------------------------------------
    def list_alerts(self, site_ids: list[str], status: str | None = None, limit: int = 100) -> list[dict]:
        if not site_ids:
            return []
        q = self.db.table("alerts").select("*").in_("site_id", site_ids)
        if status:
            q = q.eq("status", status)
        return self._rows(q.order("timestamp", desc=True).limit(limit))

    def get_alert(self, alert_id: str) -> dict | None:
        try:
            uuid.UUID(alert_id)
        except ValueError:
            return None
        rows = self._rows(self.db.table("alerts").select("*").eq("id", alert_id).limit(1))
        return rows[0] if rows else None

    def insert_alert(self, row: dict) -> dict:
        data = self.db.table("alerts").insert(_dump(row)).execute().data
        return _load(data[0])

    def update_alert(self, alert_id: str, fields: dict) -> None:
        self.db.table("alerts").update(_dump(fields)).eq("id", alert_id).execute()

    # -- assessments -------------------------------------------------------
    def insert_assessment(self, row: dict) -> None:
        self.db.table("assessments").insert(_dump(row)).execute()

    def list_assessments(self, site_id: str, limit: int = 20) -> list[dict]:
        q = self.db.table("assessments").select("*").eq("site_id", site_id)
        return self._rows(q.order("timestamp", desc=True).limit(limit))


def supabase_credentials() -> tuple[str | None, str | None]:
    url = os.environ.get("SUPABASE_URL") or os.environ.get("VITE_SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY") or os.environ.get("SUPABASE_SECRET_KEY")
    return url, key


def create_store():
    """Supabase when credentials are configured, otherwise a seeded in-memory store."""
    url, key = supabase_credentials()
    if url and key and os.environ.get("HYDROSENSE_STORE", "supabase") != "memory":
        return SupabaseStore(url, key)
    from .demo import build_demo_dataset

    store = MemoryStore()
    for table, rows in build_demo_dataset(utcnow()).items():
        store.bulk_insert(table, rows)
    return store
