"""Synthetic demo dataset.

Everything produced here is simulated and is flagged is_demo=true. It is used
by Demo Mode so the public site works with no hardware attached. Alerts and
assessments are NOT hand-written: the generator replays the synthetic
measurements through the real analytics and assessment engines.
"""
from __future__ import annotations

import math
import random
import uuid
from datetime import datetime, timedelta, timezone

from . import analytics as an
from .assessment import alert_copy, assess

DAYS = 30
HOURS = DAYS * 24
LIVE_SITE = {
    "id": "SITE-001", "name": "Live Sensor Site 01", "locality": "NSUT campus, Dwarka, New Delhi",
    "description": "The HydroSense hardware prototype: an ESP32 with a TDS probe reporting over Wi-Fi.",
    "latitude": 28.6092, "longitude": 77.0350, "status": "no_data", "is_demo": False,
}

# (id, name, locality, description, lat, lon, usual ppm, scenario)
SITES = [
    ("DEMO-01", "Water Site 01", "Hauz Khas, New Delhi", "Urban lake beside a public park.", 28.5535, 77.1926, 182, "stable"),
    ("DEMO-02", "Water Site 02", "Sanjay Lake, East Delhi", "Recreational lake surrounded by housing.", 28.6143, 77.3036, 265, "stable"),
    ("DEMO-03", "Water Site 03", "Bhalswa, North Delhi", "Horseshoe lake on the city's northern edge.", 28.7395, 77.1640, 210, "gradual"),
    ("DEMO-04", "Water Site 04", "Okhla, South-East Delhi", "Wetland stretch near a bird sanctuary.", 28.5630, 77.3040, 241, "anomaly"),
    ("DEMO-05", "Water Site 05", "Model Town, North Delhi", "Neighbourhood lake with a walking track.", 28.7106, 77.1920, 300, "recovered"),
    ("DEMO-06", "Water Site 06", "Dwarka, South-West Delhi", "Stormwater pond in a residential sector.", 28.5921, 77.0460, 150, "spike"),
]

# hours_ago, clarity, algae, waste, odor, aquatic_life, comment
OBSERVATIONS = {
    "DEMO-01": [
        (5, "clear", "no", "no", "none", "yes", "Ducks near the bank, water looks normal."),
        (30, "clear", "no", "no", "none", "yes", None),
        (75, "clear", "no", "no", "none", "not_sure", "Morning walk, nothing unusual."),
        (160, "slightly_cloudy", "no", "no", "none", "yes", "A bit murky after the rain."),
    ],
    "DEMO-02": [
        (9, "clear", "no", "no", "none", "yes", "Small fish visible near the steps."),
        (52, "clear", "no", "no", "none", "yes", None),
        (120, "clear", "not_sure", "no", "none", "not_sure", None),
    ],
    "DEMO-03": [
        (7, "slightly_cloudy", "not_sure", "no", "none", "yes", "Looks a little duller than last month."),
        (40, "slightly_cloudy", "no", "no", "none", "yes", None),
        (130, "clear", "no", "no", "none", "yes", None),
        (260, "clear", "no", "no", "none", "yes", "Clear near the jetty."),
    ],
    "DEMO-04": [
        (2, "slightly_cloudy", "yes", "yes", "none", "not_sure", "Green film near the inlet and plastic bottles floating."),
        (6, "slightly_cloudy", "yes", "no", "none", "no", "More algae than I have seen here before."),
        (11, "slightly_cloudy", "not_sure", "yes", "none", "not_sure", None),
        (60, "clear", "no", "no", "none", "yes", "Birds feeding as usual."),
        (150, "clear", "no", "no", "none", "yes", None),
    ],
    "DEMO-05": [
        (8, "clear", "no", "no", "none", "yes", "Back to normal, fish are visible again."),
        (50, "clear", "no", "no", "none", "yes", None),
        (190, "very_cloudy", "no", "yes", "mild", "no", "Water turned brown after construction runoff."),
        (205, "very_cloudy", "not_sure", "yes", "mild", "not_sure", None),
    ],
    "DEMO-06": [
        (1, "very_cloudy", "no", "yes", "strong", "no", "Strong smell near the drain outlet."),
        (4, "very_cloudy", "not_sure", "yes", "strong", "no", "Grey water flowing in from the side channel."),
        (70, "clear", "no", "no", "none", "yes", None),
    ],
}


def floor_hour(dt: datetime) -> datetime:
    return dt.astimezone(timezone.utc).replace(minute=0, second=0, microsecond=0)


def _ramp(x: float) -> float:
    return max(0.0, min(1.0, x))


def _factor(scenario: str, h: float) -> float:
    """Multiplier on the usual level, h hours before the anchor."""
    if scenario == "gradual":      # slow rise over 13 days to about +13%
        return 1 + 0.13 * _ramp((312 - h) / 312)
    if scenario == "anomaly":      # jump to about +41% over the last 14 hours
        return 1 + 0.415 * _ramp((14 - h) / 5)
    if scenario == "recovered":    # +40% event 9 to 7.5 days ago, then back to normal
        return 1 + 0.40 * min(_ramp((216 - h) / 4), _ramp((h - 180) / 6))
    if scenario == "spike":        # sharp jump to about +93% in the last 8 hours
        return 1 + 0.93 * _ramp((8 - h) / 2)
    return 1.0


def _voltage_for(tds: float) -> float:
    """Invert the firmware's TDS curve so demo rows carry plausible raw values."""
    v = tds / 430
    for _ in range(6):
        f = (133.42 * v ** 3 - 255.86 * v ** 2 + 857.39 * v) * 0.5 - tds
        df = (400.26 * v ** 2 - 511.72 * v + 857.39) * 0.5
        v -= f / df
    return v


def build_demo_dataset(now: datetime) -> dict[str, list[dict]]:
    """Return rows for every table, anchored so the newest reading is at `anchor`."""
    anchor = floor_hour(now)
    rng = random.Random(20261003)
    out: dict[str, list[dict]] = {k: [] for k in ("sites", "devices", "measurements", "observations", "alerts", "assessments")}
    out["sites"].append({**LIVE_SITE, "created_at": anchor})
    out["devices"].append({
        "device_id": "HS-001", "site_id": "SITE-001", "status": "offline",
        "firmware_version": None, "last_seen": None, "is_demo": False,
    })

    for idx, (sid, name, locality, desc, lat, lon, usual, scenario) in enumerate(SITES, start=1):
        device_id = f"HS-D{idx:02d}"
        series: list[tuple[datetime, float]] = []
        for h in range(HOURS - 1, -1, -1):
            t = anchor - timedelta(hours=h)
            daily = 1 + 0.025 * math.sin(2 * math.pi * (t.hour + 5.5) / 24)
            tds = usual * _factor(scenario, h) * daily + rng.gauss(0, usual * 0.012)
            series.append((t, round(tds, 1)))
            v = _voltage_for(tds)
            out["measurements"].append({
                "device_id": device_id, "site_id": sid, "timestamp": t, "tds_ppm": round(tds, 1),
                "raw_value": round(v / 3.3 * 4095 + rng.gauss(0, 6)), "filtered_value": round(v / 3.3 * 4095),
                "voltage": round(v, 3),
            })

        obs_rows = [{
            "id": str(uuid.UUID(int=rng.getrandbits(128))), "site_id": sid, "timestamp": anchor - timedelta(hours=ago, minutes=rng.randint(3, 50)),
            "water_clarity": clarity, "algae": algae, "waste": waste, "odor": odor, "aquatic_life": life,
            "image_url": None, "image_path": None, "comment": comment, "is_demo": True,
        } for ago, clarity, algae, waste, odor, life, comment in OBSERVATIONS[sid]]
        out["observations"] += obs_rows

        site = {"id": sid, "name": name}
        open_alerts: list[dict] = []
        last_risk, last_snapshot = None, None
        final: dict = {}
        # Replay the last 12 days hour by hour through the real engines.
        for i in range(HOURS - 12 * 24, HOURS):
            t = series[i][0]
            a = an.analyze(series[: i + 1], series[max(0, i - 23): i + 1], t)
            for act in an.alert_actions(a, open_alerts):
                if act["action"] == "create":
                    row = {
                        "id": str(uuid.UUID(int=rng.getrandbits(128))), "site_id": sid, "timestamp": t,
                        "type": act["type"], "severity": act["severity"], "status": "active",
                        "resolved_at": None, "resolution_note": None, "is_demo": True,
                        "evidence": _evidence(a), **alert_copy(act["type"], name, a),
                    }
                    open_alerts.append(row)
                    out["alerts"].append(row)
                elif act["action"] == "escalate":
                    row = next(r for r in open_alerts if r["id"] == act["id"])
                    row.update(type=act["type"], severity=act["severity"], evidence=_evidence(a), **alert_copy(act["type"], name, a))
                elif act["action"] == "resolve":
                    row = next(r for r in open_alerts if r["id"] == act["id"])
                    row.update(status="resolved", resolved_at=t, resolution_note=act.get("note") or "Readings returned to this site's usual range.")
                    open_alerts.remove(row)
            for row in open_alerts:  # keep evidence current while the alert is open
                row["evidence"] = _evidence(a)
            seen = [o for o in obs_rows if o["timestamp"] <= t]
            result = assess(site, a, seen, t)
            due = last_snapshot is None or t - last_snapshot >= timedelta(hours=24)
            if result["risk_level"] != last_risk or due or i == HOURS - 1:
                out["assessments"].append(assessment_row(result, is_demo=True))
                last_risk, last_snapshot = result["risk_level"], t
            final = a

        out["sites"].append({
            "id": sid, "name": name, "locality": locality, "description": desc,
            "latitude": lat, "longitude": lon, "is_demo": True, "created_at": anchor - timedelta(days=DAYS),
            **site_cache(final),
        })
        out["devices"].append({
            "device_id": device_id, "site_id": sid, "status": "online", "firmware_version": "1.2.0",
            "last_seen": anchor, "wifi_rssi": rng.randint(-74, -52), "is_demo": True,
        })

    out["devices"].append({  # a spare unit that has gone quiet, to show the offline state
        "device_id": "HS-D07", "site_id": "DEMO-02", "status": "offline", "firmware_version": "1.1.3",
        "last_seen": anchor - timedelta(days=2, hours=5), "wifi_rssi": -86, "is_demo": True,
    })
    return out


def _evidence(a: dict) -> dict:
    keys = ("current_tds", "baseline_tds", "deviation_percent", "z_score", "anomaly_score", "persistence",
            "trend", "trend_percent_per_day", "rate_of_change_ppm_per_hour")
    return {k: a.get(k) for k in keys}


def site_cache(a: dict) -> dict:
    """Fields cached on the site row so the map can load with a single query."""
    return {
        "status": a["state"], "latest_tds": a.get("current_tds"), "latest_at": a.get("latest_at"),
        "baseline_tds": a.get("baseline_tds"), "deviation_percent": a.get("deviation_percent"),
        "anomaly_score": a.get("anomaly_score", 0), "direction": a.get("direction", "none"),
    }


def assessment_row(result: dict, is_demo: bool) -> dict:
    return {
        "site_id": result["site_id"], "timestamp": result["timestamp"], "risk_level": result["risk_level"],
        "confidence": result["confidence"], "anomaly_score": result["anomaly_score"],
        "explanation": result["explanation"], "recommendation": result["recommendation"],
        "contributing_factors": result["contributing_factors"], "evidence": result["evidence"],
        "source": result.get("source", "rules"), "is_demo": is_demo,
    }
