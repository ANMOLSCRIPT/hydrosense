"""HydroSense analytics and anomaly detection.

Pure functions, no I/O. Everything here is deliberately simple and transparent
so the methodology can be explained to a non-specialist (see docs/METHODOLOGY.md).

The HydroSense anomaly score is NOT a validated environmental index. It only
describes how different recent TDS readings are from the same site's own history.
"""
from __future__ import annotations

from datetime import datetime, timedelta
from statistics import mean, median, pstdev
from typing import Sequence

Point = tuple[datetime, float]

# --- Tunable parameters (documented in docs/METHODOLOGY.md) -----------------
EVAL_POINTS = 6              # how many recent evaluation points we look at
DENSE_CHUNK = 5              # raw readings merged into one evaluation point
DENSE_INTERVAL = timedelta(minutes=20)
MIN_BASELINE_POINTS = 10
FLAG_DEVIATION_PCT = 15.0    # an evaluation point is "unusual" above this ...
FLAG_Z = 2.0                 # ... and above this many spreads from baseline
MAGNITUDE_FULL_PCT = 70.0    # deviation that earns the full magnitude weight
W_MAGNITUDE, W_SIGNIFICANCE, W_PERSISTENCE, W_DYNAMICS = 55, 15, 20, 10
SCORE_ATTENTION, SCORE_UNUSUAL, SCORE_ALERT = 20, 45, 85
PERSISTENCE_REQUIRED = 0.5   # share of evaluation points that must agree
STALE_AFTER = timedelta(hours=6)


def _clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def _slope_per_hour(points: Sequence[Point]) -> float:
    """Least-squares slope in ppm per hour."""
    if len(points) < 3:
        return 0.0
    t0 = points[0][0]
    xs = [(t - t0).total_seconds() / 3600 for t, _ in points]
    ys = [v for _, v in points]
    mx, my = mean(xs), mean(ys)
    den = sum((x - mx) ** 2 for x in xs)
    if den == 0:
        return 0.0
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / den


def evaluation_points(history: Sequence[Point], recent: Sequence[Point]) -> list[Point]:
    """Reduce the newest readings to a handful of smoothed evaluation points.

    Fast sensors (seconds apart) are median-merged in chunks so one noisy
    reading can never decide the outcome on its own.
    """
    src = list(recent) if recent else list(history)
    if not src:
        return []
    tail = src[-10:]
    gaps = [(b[0] - a[0]) for a, b in zip(tail, tail[1:])]
    dense = bool(gaps) and median(gaps) < DENSE_INTERVAL
    if not dense:
        return src[-EVAL_POINTS:]
    # Use up to 30 readings, but never more than half of what exists, so a new
    # sensor can establish a baseline after about 20 readings.
    size = min(EVAL_POINTS * DENSE_CHUNK, max(2 * DENSE_CHUNK, len(src) // 2 // DENSE_CHUNK * DENSE_CHUNK))
    window = src[-size:]
    out: list[Point] = []
    end = len(window)
    while end > 0:
        chunk = window[max(0, end - DENSE_CHUNK):end]
        out.append((chunk[-1][0], float(median(v for _, v in chunk))))
        end -= DENSE_CHUNK
    return list(reversed(out))


def analyze(history: Sequence[Point], recent: Sequence[Point], now: datetime) -> dict:
    """Compute baseline, deviation, trend, rate of change and anomaly score.

    history: hourly averages, oldest first (up to 30 days)
    recent:  newest raw readings, oldest first
    """
    history = sorted(history)
    recent = sorted(recent)
    evals = evaluation_points(history, recent)
    result: dict = {
        "state": "no_data", "current_tds": None, "baseline_tds": None,
        "spread": None, "deviation_percent": None, "z_score": None,
        "trend": "unknown", "trend_percent_per_day": None,
        "rate_of_change_ppm_per_hour": None, "rate_percent_per_hour": None,
        "rolling_mean_24h": None, "rolling_std_24h": None,
        "persistence": 0.0, "anomaly_score": 0, "score_parts": {},
        "direction": "none", "latest_at": None, "stale": False,
        "history_days": 0.0, "baseline_points": 0, "evaluation": [],
    }
    if not evals:
        return result

    current = evals[-1][1]
    latest_at = (recent or history)[-1][0]
    first_at = min((history or recent)[0][0], (recent or history)[0][0])
    span = now - first_at
    result.update(
        current_tds=round(current, 1), latest_at=latest_at,
        stale=(now - latest_at) > STALE_AFTER,
        history_days=round(span.total_seconds() / 86400, 2),
    )

    # --- Historical baseline -------------------------------------------------
    # Exclude the newest data so an ongoing event cannot become "normal".
    eval_start = evals[0][0]
    cutoff = now - timedelta(hours=24) if span >= timedelta(hours=72) else eval_start
    base = [v for t, v in history if t < cutoff]
    if len(base) < 12:
        base = [v for t, v in history if t < eval_start] + [v for t, v in recent if t < eval_start]
    result["baseline_points"] = len(base)

    last24 = [v for t, v in history if t >= now - timedelta(hours=24)]
    if len(last24) < 3:
        last24 = [v for _, v in recent]
    if last24:
        result["rolling_mean_24h"] = round(mean(last24), 1)
        result["rolling_std_24h"] = round(pstdev(last24), 2) if len(last24) > 1 else 0.0

    if len(base) < MIN_BASELINE_POINTS:
        result["state"] = "learning"
        return result

    baseline = float(median(base))
    mad = float(median(abs(v - baseline) for v in base)) * 1.4826
    spread = max(mad, 0.03 * baseline, 3.0)  # floor: sensor noise is never zero
    deviation = (current - baseline) / baseline * 100 if baseline else 0.0
    z = (current - baseline) / spread

    # --- Persistence ---------------------------------------------------------
    flags = []
    for t, v in evals:
        d = (v - baseline) / baseline * 100 if baseline else 0.0
        flagged = abs(d) >= FLAG_DEVIATION_PCT and abs((v - baseline) / spread) >= FLAG_Z
        flags.append(flagged)
        result["evaluation"].append({"t": t, "tds": round(v, 1), "deviation_percent": round(d, 1), "unusual": flagged})
    persistence = sum(flags) / max(len(flags), 3)

    # --- Rate of change (short term) ----------------------------------------
    target = now - timedelta(hours=3)
    earlier = [p for p in history if abs(p[0] - target) <= timedelta(minutes=90)]
    if earlier:
        ref = min(earlier, key=lambda p: abs(p[0] - target))
        hours = max((latest_at - ref[0]).total_seconds() / 3600, 0.5)
        rate = (current - ref[1]) / hours
    else:
        rate = _slope_per_hour(recent[-60:])
    rate_pct = rate / baseline * 100 if baseline else 0.0

    # --- Trend (long term) ---------------------------------------------------
    week = [p for p in history if p[0] >= now - timedelta(days=7)]
    if span >= timedelta(hours=48) and len(week) >= 24:
        trend_pct_day = _slope_per_hour(week) * 24 / baseline * 100
        trend = "increasing" if trend_pct_day > 0.7 else "decreasing" if trend_pct_day < -0.7 else "stable"
    else:
        trend_pct_day = None
        trend = "increasing" if rate_pct > 2 else "decreasing" if rate_pct < -2 else "stable"

    # --- Anomaly score -------------------------------------------------------
    parts = {
        "magnitude": W_MAGNITUDE * _clamp(abs(deviation) / MAGNITUDE_FULL_PCT),
        "significance": W_SIGNIFICANCE * _clamp(abs(z) / 4),
        "persistence": W_PERSISTENCE * _clamp(persistence),
        "dynamics": W_DYNAMICS * max(_clamp(abs(rate_pct) / 10), _clamp(abs(trend_pct_day or 0) / 2)),
    }
    score = round(sum(parts.values()))

    sustained = persistence >= PERSISTENCE_REQUIRED
    if score >= SCORE_ALERT and sustained:
        state = "alert"
    elif score >= SCORE_UNUSUAL and sustained:
        state = "unusual"
    elif score >= SCORE_ATTENTION:
        state = "attention"
    else:
        state = "stable"

    result.update(
        state=state, baseline_tds=round(baseline, 1), spread=round(spread, 2),
        deviation_percent=round(deviation, 1), z_score=round(z, 2),
        trend=trend, trend_percent_per_day=None if trend_pct_day is None else round(trend_pct_day, 2),
        rate_of_change_ppm_per_hour=round(rate, 2), rate_percent_per_hour=round(rate_pct, 2),
        persistence=round(persistence, 2), anomaly_score=int(score),
        score_parts={k: round(v, 1) for k, v in parts.items()},
        direction="higher" if deviation > 3 else "lower" if deviation < -3 else "none",
    )
    return result


def anomaly_markers(history: Sequence[Point], baseline: float | None, spread: float | None) -> list[dict]:
    """Hourly points that sit clearly outside the site's usual range (for charts)."""
    if not baseline or not spread:
        return []
    out = []
    for t, v in history:
        d = (v - baseline) / baseline * 100
        if abs(d) >= FLAG_DEVIATION_PCT and abs((v - baseline) / spread) >= FLAG_Z:
            out.append({"t": t, "tds": round(v, 1), "deviation_percent": round(d, 1)})
    return out


def alert_actions(analytics: dict, open_alerts: list[dict]) -> list[dict]:
    """Decide what should happen to a site's alerts given fresh analytics.

    Returns a list of actions: {"action": "create"|"escalate"|"resolve", ...}.
    Alerts are only raised for sustained changes, never a single reading.
    """
    state = analytics["state"]
    evals_dev = [abs(e["deviation_percent"]) for e in analytics.get("evaluation", [])]
    actions: list[dict] = []
    change_alerts = [a for a in open_alerts if a["type"] in ("unusual_change", "significant_change")]
    trend_alerts = [a for a in open_alerts if a["type"] == "gradual_trend"]

    if state in ("unusual", "alert"):
        severity = "high" if state == "alert" else "medium"
        kind = "significant_change" if state == "alert" else "unusual_change"
        if not change_alerts:
            actions.append({"action": "create", "type": kind, "severity": severity})
            for a in trend_alerts:  # the slower "drift" alert is superseded by the stronger one
                actions.append({"action": "resolve", "id": a["id"], "note": "Replaced by a more recent alert for this site."})
        elif state == "alert" and all(a["severity"] != "high" for a in change_alerts):
            actions.append({"action": "escalate", "id": change_alerts[0]["id"], "type": kind, "severity": severity})
    elif state == "attention":
        drifting = (abs(analytics.get("trend_percent_per_day") or 0) >= 0.7
                    and abs(analytics.get("deviation_percent") or 0) >= 10
                    and min(evals_dev, default=0) >= 8          # every recent point agrees
                    and abs(analytics.get("rate_percent_per_hour") or 0) < 2)  # slow drift, not a sudden jump
        if drifting and not trend_alerts and not change_alerts:
            actions.append({"action": "create", "type": "gradual_trend", "severity": "low"})
    elif state == "stable" and analytics["persistence"] == 0:
        for a in open_alerts:
            # Hysteresis: a drift alert stays open until readings are clearly back near the baseline.
            if a["type"] == "gradual_trend" and max(evals_dev, default=0) >= 5:
                continue
            actions.append({"action": "resolve", "id": a["id"]})
    return actions
