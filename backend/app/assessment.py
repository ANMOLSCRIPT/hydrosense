"""Evidence-based assessment engine and citizen-facing language.

Deterministic by design: HydroSense works without any external LLM. The
optional LLM layer (llm.py) only rewrites the explanation from the same
structured evidence; it never changes the assessment or the numbers.
"""
from __future__ import annotations

from datetime import datetime, timedelta

DISCLAIMER = (
    "HydroSense is an environmental monitoring and decision-support prototype. TDS is only one "
    "indicator and cannot by itself determine overall water quality, pollution, ecosystem health, "
    "or drinking-water safety. HydroSense alerts indicate changes that may warrant further "
    "observation or field verification."
)

STATE_LABELS = {
    "stable": "Stable",
    "attention": "Needs attention",
    "unusual": "Unusual change",
    "alert": "Active alert",
    "learning": "Still learning",
    "no_data": "Waiting for data",
}

RISK_LABELS = {
    "stable": "No signs of stress",
    "watch": "Worth watching",
    "potential_stress": "Potential ecosystem stress",
    "elevated": "Elevated potential stress",
    "unknown": "Not enough evidence yet",
}

OBSERVATION_WINDOW = timedelta(hours=72)


def _dir_word(direction: str) -> str:
    return {"higher": "higher than", "lower": "lower than"}.get(direction, "different from")


def citizen_copy(state: str, direction: str = "none", deviation: float | None = None,
                 current: float | None = None, baseline: float | None = None) -> dict:
    """Plain-language status for citizens, with progressive-disclosure layers."""
    d = _dir_word(direction)
    pct = f"{abs(deviation):.0f}%" if deviation is not None else None
    data = f"{current:.0f} ppm now, compared with a usual {baseline:.0f} ppm." if current is not None and baseline else None
    if state == "stable":
        return {
            "label": STATE_LABELS[state], "headline": "Looking stable",
            "summary": "Recent measurements are consistent with this site's usual pattern.",
            "why": "The latest readings sit within the range we normally see at this location.",
            "detail": data,
            "action": "No action needed. You can still share what you see to keep the picture complete.",
        }
    if state == "attention":
        return {
            "label": STATE_LABELS[state], "headline": "Worth keeping an eye on",
            "summary": f"Readings have been drifting {direction} than what we usually observe here." if direction != "none" else "Readings have been drifting away from what we usually observe here.",
            "why": f"Measurements are about {pct} {d} this site's usual level, but the change is still modest." if pct else "Measurements are drifting away from the usual level.",
            "detail": data,
            "action": "Another observation could help confirm whether this change continues.",
        }
    if state == "unusual":
        return {
            "label": STATE_LABELS[state], "headline": "Something has changed",
            "summary": f"Recent readings are {d} what we usually observe here.",
            "why": f"Dissolved solids have moved about {pct} away from this site's usual pattern, and the change has lasted across several readings." if pct else "Recent measurements differ from the site's usual pattern.",
            "detail": data,
            "action": "Consider submitting an observation or requesting a field check.",
        }
    if state == "alert":
        return {
            "label": STATE_LABELS[state], "headline": "A significant change needs attention",
            "summary": f"Recent readings are much {direction} than usual, and the change has persisted." if direction != "none" else "Recent readings are far from usual, and the change has persisted.",
            "why": f"Measurements are about {pct} {d} the usual level and have stayed there." if pct else "Measurements are far from the usual level.",
            "detail": data,
            "action": "A field check is recommended. If you are nearby, sharing what you see would help.",
        }
    if state == "learning":
        return {
            "label": STATE_LABELS[state], "headline": "Getting to know this site",
            "summary": "We're still learning what's normal here. A few more readings will help.",
            "why": "HydroSense compares new readings with a site's own history, and this site doesn't have enough history yet.",
            "detail": f"Latest reading: {current:.0f} ppm." if current is not None else None,
            "action": "Observations are especially valuable while the sensor builds up a history.",
        }
    return {
        "label": STATE_LABELS["no_data"], "headline": "Waiting for the first reading",
        "summary": "No sensor readings have arrived from this site yet.",
        "why": "The sensor has not sent any data.", "detail": None,
        "action": "You can still report what you see.",
    }


def alert_copy(kind: str, site_name: str, analytics: dict) -> dict:
    direction = analytics.get("direction", "none")
    d = _dir_word(direction)
    dev = analytics.get("deviation_percent")
    if kind == "gradual_trend":
        word = "rising" if (analytics.get("trend_percent_per_day") or 0) > 0 else "falling"
        return {
            "title": f"Readings are slowly {word} at {site_name}",
            "message": f"Measurements have been gradually {word} over the past days at this location.",
            "recommendation": "Keep watching this site. Extra citizen observations will show whether the drift continues.",
        }
    if kind == "significant_change":
        return {
            "title": f"A significant change at {site_name}",
            "message": f"Recent readings are much {direction} than usual at this location, and the change has persisted." if direction != "none" else "Recent readings are far from usual at this location, and the change has persisted.",
            "recommendation": "Field verification is recommended. HydroSense has detected a potential anomaly, not confirmed pollution.",
        }
    return {
        "title": f"Something changed at {site_name}",
        "message": f"Recent readings are {d} usual at this location." if direction != "none" else "Recent readings differ from the usual pattern at this location.",
        "recommendation": "Consider a follow-up observation or a field check to see whether the change persists.",
    } | ({"detail": f"About {abs(dev):.0f}% {d} the site's usual level."} if dev is not None else {})


def summarize_observations(observations: list[dict], now: datetime) -> dict:
    """Count what citizens reported in the recent window."""
    recent = [o for o in observations if now - o["timestamp"] <= OBSERVATION_WINDOW]
    cloud = {"slightly_cloudy": 0, "very_cloudy": 0, "clear": 0}
    for o in recent:
        if o.get("water_clarity") in cloud:
            cloud[o["water_clarity"]] += 1
    return {
        "count": len(recent),
        "algae": sum(o.get("algae") == "yes" for o in recent),
        "waste": sum(o.get("waste") == "yes" for o in recent),
        "odor_mild": sum(o.get("odor") == "mild" for o in recent),
        "odor_strong": sum(o.get("odor") == "strong" for o in recent),
        "slightly_cloudy": cloud["slightly_cloudy"],
        "very_cloudy": cloud["very_cloudy"],
        "clear": cloud["clear"],
        "aquatic_life": sum(o.get("aquatic_life") == "yes" for o in recent),
        "window_hours": int(OBSERVATION_WINDOW.total_seconds() // 3600),
    }


def assess(site: dict, analytics: dict, observations: list[dict], now: datetime) -> dict:
    """Combine sensor analytics and citizen observations into one assessment."""
    obs = summarize_observations(observations, now)
    state = analytics["state"]
    score = analytics.get("anomaly_score", 0)
    dev = analytics.get("deviation_percent")
    direction = analytics.get("direction", "none")
    factors: list[dict] = []

    # --- Sensor evidence -----------------------------------------------------
    sensor_points = score * 0.7
    if state in ("attention", "unusual", "alert") and dev is not None:
        factors.append({
            "key": "tds_change", "icon": "trending-up" if dev > 0 else "trending-down",
            "label": "Dissolved solids increased" if dev > 0 else "Dissolved solids decreased",
            "detail": f"About {abs(dev):.0f}% {_dir_word(direction)} this site's usual level.",
            "weight": round(sensor_points),
        })
    if analytics.get("trend") in ("increasing", "decreasing") and analytics.get("trend_percent_per_day") is not None and state != "stable":
        factors.append({
            "key": "trend", "icon": "activity",
            "label": f"Readings are {analytics['trend']} over the week",
            "detail": f"Roughly {abs(analytics['trend_percent_per_day']):.1f}% per day.", "weight": 0,
        })

    # --- Citizen evidence ----------------------------------------------------
    citizen_points = 0.0
    if obs["algae"]:
        citizen_points += 12
        factors.append({"key": "algae", "icon": "leaf", "label": "Algae reported",
                        "detail": f"{obs['algae']} recent report(s).", "weight": 12})
    if obs["waste"]:
        citizen_points += 8
        factors.append({"key": "waste", "icon": "trash", "label": "Floating waste reported",
                        "detail": f"{obs['waste']} recent report(s).", "weight": 8})
    if obs["very_cloudy"] or obs["slightly_cloudy"]:
        pts = 10 if obs["very_cloudy"] else 5
        citizen_points += pts
        factors.append({"key": "cloudiness", "icon": "cloud", "label": "Cloudiness reported",
                        "detail": "Very cloudy water reported." if obs["very_cloudy"] else "Slightly cloudy water reported.", "weight": pts})
    if obs["odor_strong"] or obs["odor_mild"]:
        pts = 10 if obs["odor_strong"] else 5
        citizen_points += pts
        factors.append({"key": "odor", "icon": "wind", "label": "Unusual smell reported",
                        "detail": "Strong smell reported." if obs["odor_strong"] else "Mild smell reported.", "weight": pts})
    concerning_reports = obs["algae"] + obs["waste"] + obs["very_cloudy"] + obs["odor_strong"]
    if concerning_reports >= 2:
        citizen_points += 5
    citizen_points = min(citizen_points, 40)

    stress = min(100.0, sensor_points + citizen_points)
    if state in ("no_data", "learning") and citizen_points == 0:
        risk = "unknown"
    elif stress >= 88:
        risk = "elevated"
    elif stress >= 45:
        risk = "potential_stress"
    elif stress >= 20:
        risk = "watch"
    else:
        risk = "stable"

    # --- Confidence: how much evidence supports this assessment --------------
    sensor_concern = state in ("unusual", "alert")
    citizen_concern = citizen_points >= 10
    confidence = 30.0
    confidence += min(analytics.get("history_days", 0) / 30, 1) * 15
    confidence += (analytics.get("persistence", 0) if sensor_concern else 1 - analytics.get("persistence", 0)) * 12
    confidence += min(obs["count"], 4) * 2
    if not analytics.get("stale") and state not in ("no_data", "learning"):
        confidence += 5
    if obs["count"]:
        confidence += 12 if sensor_concern == citizen_concern else -5
    if state in ("no_data", "learning"):
        confidence = min(confidence, 45)
    confidence = int(max(15, min(95, round(confidence))))

    explanation = _explain(site, analytics, obs, risk)
    recommendation = _recommend(risk, state, obs)
    evidence = {
        "current_tds": analytics.get("current_tds"),
        "baseline_tds": analytics.get("baseline_tds"),
        "deviation_percent": dev,
        "trend": analytics.get("trend"),
        "rate_of_change_ppm_per_hour": analytics.get("rate_of_change_ppm_per_hour"),
        "persistence": analytics.get("persistence"),
        "anomaly_score": score,
        "sensor_state": state,
        "observations_considered": obs["count"],
        "algae": bool(obs["algae"]),
        "waste": bool(obs["waste"]),
        "cloudiness": "very_cloudy" if obs["very_cloudy"] else "slightly_cloudy" if obs["slightly_cloudy"] else "clear" if obs["clear"] else "not_reported",
        "odor": "strong" if obs["odor_strong"] else "mild" if obs["odor_mild"] else "none_reported",
        "aquatic_life_seen": bool(obs["aquatic_life"]),
        "sensor_points": round(sensor_points, 1),
        "citizen_points": round(citizen_points, 1),
        "stress_points": round(stress, 1),
    }
    return {
        "site_id": site["id"], "site_name": site["name"], "timestamp": now,
        "risk_level": risk, "assessment": RISK_LABELS[risk], "confidence": confidence,
        "anomaly_score": score, "contributing_factors": factors,
        "explanation": explanation, "recommendation": recommendation,
        "evidence": evidence, "observation_summary": obs, "source": "rules",
        "disclaimer": DISCLAIMER,
    }


def _explain(site: dict, a: dict, obs: dict, risk: str) -> str:
    state, dev, direction = a["state"], a.get("deviation_percent"), a.get("direction", "none")
    parts: list[str] = []
    if state == "no_data":
        parts.append("No sensor readings are available for this site yet.")
    elif state == "learning":
        parts.append("The sensor at this site is still building up enough history to know what is normal here.")
    elif state == "stable":
        parts.append("Recent measurements are consistent with this site's historical pattern.")
    elif state == "attention":
        parts.append(f"Recent measurements are moderately {_dir_word(direction)} the site's historical pattern (about {abs(dev):.0f}%).")
    else:
        strength = "substantially" if abs(dev or 0) >= 30 else "noticeably"
        parts.append(f"Recent measurements are {strength} {_dir_word(direction)} the site's historical pattern (about {abs(dev):.0f}%), and the change has persisted across several readings.")

    seen = []
    if obs["algae"]:
        seen.append("algae")
    if obs["waste"]:
        seen.append("floating waste")
    if obs["very_cloudy"] or obs["slightly_cloudy"]:
        seen.append("increased cloudiness")
    if obs["odor_strong"] or obs["odor_mild"]:
        seen.append("an unusual smell")
    if seen:
        listed = seen[0] if len(seen) == 1 else ", ".join(seen[:-1]) + " and " + seen[-1]
        also = "also " if state in ("attention", "unusual", "alert") else ""
        parts.append(f"Citizen observations {also}indicate {listed}.")
    elif obs["count"]:
        parts.append("Recent citizen observations did not report anything unusual.")
    else:
        parts.append("There are no recent citizen observations for this site.")

    if risk in ("potential_stress", "elevated"):
        parts.append("Together this points to potential ecosystem stress, which is not the same as confirmed pollution.")
    return " ".join(parts)


def _recommend(risk: str, state: str, obs: dict) -> str:
    if risk == "elevated":
        return "HydroSense recommends prompt field verification and additional observations from people nearby."
    if risk == "potential_stress":
        return "HydroSense recommends additional observation and field verification to confirm whether the change is persistent."
    if risk == "watch":
        return "Another observation could help confirm whether this change is persistent."
    if risk == "unknown":
        return "Sharing an observation is the most useful thing right now, while the sensor builds up a history."
    return "No action is needed. Occasional observations help keep this picture up to date."
