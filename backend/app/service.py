"""Application logic that sits between the HTTP routes and the store."""
from __future__ import annotations

import os
import time
from datetime import datetime, timedelta, timezone

from . import analytics as an
from . import llm
from .assessment import DISCLAIMER, RISK_LABELS, alert_copy, assess, citizen_copy
from .demo import _evidence, assessment_row, site_cache
from .store import utcnow

RANGES = {"24h": (timedelta(hours=24), 300), "7d": (timedelta(days=7), 3600), "30d": (timedelta(days=30), 4 * 3600)}
LIVE_ONLINE_WINDOW = timedelta(seconds=int(os.environ.get("DEVICE_ONLINE_SECONDS", "120")))
DEMO_ONLINE_WINDOW = timedelta(minutes=45)
HALF_HOUR = 1800


class NotFound(Exception):
    pass


class Invalid(Exception):
    pass


class Service:
    def __init__(self, store) -> None:
        self.store = store
        self._offset: tuple[float, timedelta] | None = None

    # ------------------------------------------------------------------
    # Demo time shift
    # ------------------------------------------------------------------
    # Seeded demo rows are stored once with fixed timestamps. When they are
    # read they are shifted so the newest demo reading is always 0-30 minutes
    # old. This keeps the public demo looking current without rewriting data.
    def offset(self) -> timedelta:
        if self._offset and time.monotonic() - self._offset[0] < 60:
            return self._offset[1]
        stamps = [s["latest_at"] for s in self.store.list_sites(True) if s.get("latest_at")]
        if stamps:
            now = utcnow()
            floored = datetime.fromtimestamp(now.timestamp() // HALF_HOUR * HALF_HOUR, timezone.utc)
            off = floored - max(stamps)
        else:
            off = timedelta(0)
        self._offset = (time.monotonic(), off)
        return off

    def _shift(self, row: dict, *fields: str, demo: bool | None = None) -> dict:
        is_demo = row.get("is_demo") if demo is None else demo
        if is_demo:
            off = self.offset()
            for f in fields:
                if row.get(f):
                    row[f] = row[f] + off
        return row

    # ------------------------------------------------------------------
    # Sites
    # ------------------------------------------------------------------
    def _site(self, site_id: str) -> dict:
        site = self.store.get_site(site_id)
        if not site:
            raise NotFound(f"We couldn't find a water site called '{site_id}'.")
        return self._shift(site, "latest_at")

    def _site_ids(self, mode: str) -> dict[str, dict]:
        return {s["id"]: self._shift(s, "latest_at") for s in self.store.list_sites(mode == "demo")}

    def _public_site(self, site: dict, active_alerts: int = 0) -> dict:
        copy = citizen_copy(site.get("status") or "no_data", site.get("direction") or "none",
                            site.get("deviation_percent"), site.get("latest_tds"), site.get("baseline_tds"))
        return {
            "id": site["id"], "name": site["name"], "locality": site.get("locality"),
            "description": site.get("description"), "latitude": site["latitude"], "longitude": site["longitude"],
            "is_demo": bool(site.get("is_demo")), "status": site.get("status") or "no_data",
            "status_label": copy["label"], "headline": copy["headline"], "summary": copy["summary"],
            "latest_tds": site.get("latest_tds"), "latest_at": site.get("latest_at"),
            "baseline_tds": site.get("baseline_tds"), "deviation_percent": site.get("deviation_percent"),
            "anomaly_score": site.get("anomaly_score") or 0, "active_alerts": active_alerts,
        }

    def list_sites(self, mode: str) -> list[dict]:
        sites = self._site_ids(mode)
        counts: dict[str, int] = {}
        for a in self.store.list_alerts(list(sites), status="active"):
            counts[a["site_id"]] = counts.get(a["site_id"], 0) + 1
        return [self._public_site(s, counts.get(sid, 0)) for sid, s in sites.items()]

    def _series(self, site: dict, start: datetime, end: datetime, bucket: int) -> list[dict]:
        off = self.offset() if site.get("is_demo") else timedelta(0)
        rows = self.store.history(site["id"], start - off, end - off, bucket)
        for r in rows:
            r["t"] = r["t"] + off
        return rows

    def _recent(self, site: dict, limit: int = 200, device_id: str | None = None) -> list[dict]:
        rows = self.store.recent(site["id"], limit, device_id)
        return [self._shift(r, "timestamp", demo=bool(site.get("is_demo"))) for r in rows]

    def _analyze(self, site: dict, now: datetime) -> tuple[dict, list]:
        hourly = [(r["t"], float(r["tds"])) for r in self._series(site, now - timedelta(days=30), now + timedelta(minutes=5), 3600)]
        recent = [(r["timestamp"], float(r["tds_ppm"])) for r in self._recent(site) if r["timestamp"] >= now - timedelta(hours=24)]
        return an.analyze(hourly, recent, now), hourly

    def _observations(self, site_ids: list[str], limit: int = 50) -> list[dict]:
        rows = [self._shift(o, "timestamp") for o in self.store.list_observations(site_ids, limit)]
        return sorted(rows, key=lambda o: o["timestamp"], reverse=True)

    def site_detail(self, site_id: str) -> dict:
        site = self._site(site_id)
        now = utcnow()
        a, _ = self._analyze(site, now)
        alerts = self.store.list_alerts([site_id], status="active")
        public = self._public_site({**site, **site_cache(a)}, len(alerts))
        public["citizen"] = citizen_copy(a["state"], a["direction"], a["deviation_percent"], a["current_tds"], a["baseline_tds"])
        public["trend"] = a["trend"]
        public["devices"] = [self._device_public(d) for d in self.store.list_devices([site_id])]
        public["observation_count"] = self.store.count_observations([site_id])
        public["disclaimer"] = DISCLAIMER
        return public

    def measurements(self, site_id: str, range_key: str) -> dict:
        site = self._site(site_id)
        span, bucket = RANGES[range_key]
        now = utcnow()
        rows = self._series(site, now - span, now + timedelta(minutes=5), bucket)
        return {
            "site_id": site_id, "range": range_key, "bucket_seconds": bucket,
            "baseline_tds": site.get("baseline_tds"), "unit": "ppm",
            "points": [{"t": r["t"], "tds": round(float(r["tds"]), 1), "min": round(float(r["tds_min"]), 1),
                        "max": round(float(r["tds_max"]), 1), "n": r["n"]} for r in rows],
        }

    def analytics(self, site_id: str) -> dict:
        site = self._site(site_id)
        a, hourly = self._analyze(site, utcnow())
        a["site_id"] = site_id
        a["anomalies"] = an.anomaly_markers(hourly, a["baseline_tds"], a["spread"])
        a["methodology"] = (
            "Baseline: median of this site's hourly history, excluding the most recent data. "
            "Anomaly score (0-100) = magnitude of deviation (55) + statistical significance (15) + "
            "persistence across recent readings (20) + speed of change (10). "
            "This is a HydroSense monitoring score, not a validated environmental index."
        )
        return a

    # ------------------------------------------------------------------
    # Ingestion (live hardware)
    # ------------------------------------------------------------------
    def ingest(self, m: dict) -> dict:
        site = self.store.get_site(m["site_id"])
        if not site:
            raise NotFound(f"Unknown site '{m['site_id']}'. Register the site before sending measurements.")
        if site.get("is_demo"):
            raise Invalid("Demo sites only hold simulated data and do not accept sensor readings.")
        now = utcnow()
        ts = m.get("timestamp")
        if m.get("age_seconds") is not None:
            ts = now - timedelta(seconds=m["age_seconds"])
        if ts is None or ts > now + timedelta(minutes=5) or ts.year < 2024:
            ts = now  # device clock not set: trust the server's receive time
        row = self.store.insert_measurement({
            "device_id": m["device_id"], "site_id": m["site_id"], "timestamp": ts, "tds_ppm": m["tds_ppm"],
            "raw_value": m.get("raw_value"), "filtered_value": m.get("filtered_value"), "voltage": m.get("voltage"),
        })
        device = {"site_id": m["site_id"], "status": "online", "last_seen": now, "is_demo": False}
        for key in ("firmware_version", "wifi_rssi"):
            if m.get(key) is not None:
                device[key] = m[key]
        self.store.upsert_device(m["device_id"], device)
        a = self.refresh_site(site, now)
        return {"ok": True, "id": row.get("id"), "timestamp": ts, "site_status": a["state"], "anomaly_score": a["anomaly_score"]}

    def refresh_site(self, site: dict, now: datetime) -> dict:
        """Recompute analytics, cached site status, alerts and the stored assessment."""
        a, _ = self._analyze(site, now)
        self.store.update_site(site["id"], site_cache(a))
        open_alerts = self.store.list_alerts([site["id"]], status="active")
        for act in an.alert_actions(a, open_alerts):
            if act["action"] == "create":
                self.store.insert_alert({
                    "site_id": site["id"], "timestamp": now, "type": act["type"], "severity": act["severity"],
                    "status": "active", "is_demo": False, "evidence": _evidence(a), **alert_copy(act["type"], site["name"], a),
                })
            elif act["action"] == "escalate":
                self.store.update_alert(str(act["id"]), {"type": act["type"], "severity": act["severity"],
                                                         "evidence": _evidence(a), **alert_copy(act["type"], site["name"], a)})
            elif act["action"] == "resolve":
                self.store.update_alert(str(act["id"]), {"status": "resolved", "resolved_at": now,
                                                         "resolution_note": act.get("note") or "Readings returned to this site's usual range."})
        self._snapshot(site, a, now)
        return a

    def _snapshot(self, site: dict, a: dict, now: datetime) -> None:
        result = assess(site, a, self._observations([site["id"]], 30), now)
        latest = self.store.list_assessments(site["id"], 1)
        if not latest or latest[0]["risk_level"] != result["risk_level"] or now - latest[0]["timestamp"] >= timedelta(hours=1):
            self.store.insert_assessment(assessment_row(result, is_demo=False))

    # ------------------------------------------------------------------
    # Observations
    # ------------------------------------------------------------------
    def add_observation(self, o: dict) -> dict:
        site = self.store.get_site(o["site_id"])
        if not site:
            raise NotFound("We couldn't find that water site.")
        now = utcnow()
        row = self.store.insert_observation({**o, "timestamp": now, "is_demo": False})
        if not site.get("is_demo"):
            self._snapshot(self._shift(site, "latest_at"), self._analyze(site, now)[0], now)
        return {**row, "site_name": site["name"]}

    def list_observations(self, mode: str, site_id: str | None, limit: int) -> list[dict]:
        sites = self._site_ids(mode)
        if site_id:
            sites = {site_id: self._site(site_id)}
        rows = self._observations(list(sites), limit)
        return [{**o, "site_name": sites[o["site_id"]]["name"]} for o in rows]

    # ------------------------------------------------------------------
    # Alerts
    # ------------------------------------------------------------------
    def _alert_public(self, a: dict, sites: dict[str, dict]) -> dict:
        self._shift(a, "timestamp", "resolved_at")
        site = sites.get(a["site_id"]) or {}
        return {**a, "site_name": site.get("name", a["site_id"]), "site_status": site.get("status")}

    def list_alerts(self, mode: str, status: str | None, site_id: str | None) -> list[dict]:
        sites = self._site_ids(mode)
        ids = [site_id] if site_id else list(sites)
        rows = [self._alert_public(a, sites) for a in self.store.list_alerts(ids, status)]
        return sorted(rows, key=lambda a: (a["status"] != "active", -a["timestamp"].timestamp()))

    def get_alert(self, alert_id: str) -> dict:
        alert = self.store.get_alert(alert_id)
        if not alert:
            raise NotFound("We couldn't find that alert.")
        return self._alert_public(alert, {alert["site_id"]: self.store.get_site(alert["site_id"]) or {}})

    def resolve_alert(self, alert_id: str, note: str | None) -> dict:
        alert = self.store.get_alert(alert_id)
        if not alert:
            raise NotFound("We couldn't find that alert.")
        if alert["status"] != "resolved":
            when = utcnow() - (self.offset() if alert.get("is_demo") else timedelta(0))
            self.store.update_alert(alert_id, {"status": "resolved", "resolved_at": when,
                                               "resolution_note": note or "Marked as resolved by a monitoring user."})
        return self.get_alert(alert_id)

    # ------------------------------------------------------------------
    # Assessment
    # ------------------------------------------------------------------
    def assessment(self, site_id: str, with_history: bool = True) -> dict:
        site = self._site(site_id)
        now = utcnow()
        a, _ = self._analyze(site, now)
        result = assess(site, a, self._observations([site_id], 30), now)
        text = llm.explain(result["evidence"], result["assessment"], result["risk_level"])
        if text:
            result["explanation"], result["source"] = text, "llm"
        result["sensor_state"] = a["state"]
        result["llm_enabled"] = llm.enabled()
        if with_history:
            past = [self._shift(r, "timestamp") for r in self.store.list_assessments(site_id, 12)]
            result["history"] = [{"timestamp": r["timestamp"], "risk_level": r["risk_level"],
                                  "label": RISK_LABELS.get(r["risk_level"], r["risk_level"]),
                                  "confidence": r["confidence"], "anomaly_score": r["anomaly_score"]} for r in past]
        return result

    def assessments(self, mode: str) -> list[dict]:
        order = {"elevated": 0, "potential_stress": 1, "watch": 2, "unknown": 3, "stable": 4}
        rows = [self.assessment(sid, with_history=False) for sid in self._site_ids(mode)]
        return sorted(rows, key=lambda r: (order.get(r["risk_level"], 9), -r["anomaly_score"]))

    # ------------------------------------------------------------------
    # Devices
    # ------------------------------------------------------------------
    def _device_public(self, d: dict) -> dict:
        self._shift(d, "last_seen")
        window = DEMO_ONLINE_WINDOW if d.get("is_demo") else LIVE_ONLINE_WINDOW
        online = bool(d.get("last_seen")) and utcnow() - d["last_seen"] <= window
        health = "good" if online and (d.get("wifi_rssi") or -60) > -80 else "weak signal" if online else "offline"
        return {
            "device_id": d["device_id"], "site_id": d["site_id"], "status": "online" if online else "offline",
            "firmware_version": d.get("firmware_version"), "last_seen": d.get("last_seen"),
            "wifi_rssi": d.get("wifi_rssi"), "health": health, "is_demo": bool(d.get("is_demo")),
        }

    def list_devices(self, mode: str) -> list[dict]:
        sites = self._site_ids(mode)
        out = []
        for d in self.store.list_devices(list(sites)):
            pub = self._device_public(d)
            site = sites[d["site_id"]]
            pub.update(site_name=site["name"], latest_tds=site.get("latest_tds") if pub["status"] == "online" else None)
            out.append(pub)
        return out

    def device_detail(self, device_id: str) -> dict:
        d = self.store.get_device(device_id)
        if not d:
            raise NotFound(f"No device called '{device_id}' has reported to HydroSense yet.")
        site = self._site(d["site_id"])
        pub = self._device_public(d)
        readings = self._recent(site, 30, device_id)
        pub.update(site_name=site["name"], readings=[{
            "timestamp": r["timestamp"], "tds_ppm": float(r["tds_ppm"]), "raw_value": r.get("raw_value"),
            "filtered_value": r.get("filtered_value"), "voltage": r.get("voltage"),
        } for r in reversed(readings)])
        pub["latest"] = pub["readings"][0] if pub["readings"] else None
        return pub

    # ------------------------------------------------------------------
    # Home snapshot
    # ------------------------------------------------------------------
    def stats(self, mode: str) -> dict:
        sites = self._site_ids(mode)
        devices = [self._device_public(d) for d in self.store.list_devices(list(sites))]
        return {
            "mode": mode, "sites": len(sites),
            "active_sensors": sum(d["status"] == "online" for d in devices),
            "observations": self.store.count_observations(list(sites)),
            "potential_anomalies": len(self.store.list_alerts(list(sites), status="active")),
        }
