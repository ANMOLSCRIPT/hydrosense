# Methodology: analytics, anomaly detection and assessment

> HydroSense is an environmental monitoring and decision-support prototype. TDS is only one indicator and cannot by itself determine overall water quality, pollution, ecosystem health, or drinking-water safety. HydroSense alerts indicate changes that may warrant further observation or field verification.

Everything below is implemented in `backend/app/analytics.py` and `backend/app/assessment.py`. The parameters are constants at the top of those files.

## 1. Inputs

- **History:** hourly mean TDS for the last 30 days.
- **Recent:** up to the newest 200 raw readings from the last 24 hours.

## 2. Evaluation points (noise handling)

The newest readings are reduced to at most six evaluation points. For fast sensors (readings less than 20 minutes apart) the last 30 readings (or half of the available readings while a sensor is new) are merged in chunks of five using the median, so one noisy reading cannot decide anything. For slow series the last six points are used directly. The current value is the last evaluation point.

## 3. Historical baseline

`baseline = median(history excluding the most recent data)`

- With 3 or more days of history, the last 24 hours are excluded so an ongoing event cannot become "normal".
- With less history, everything before the evaluation window is used.
- With fewer than 10 baseline points the site is reported as **still learning** and no anomaly is evaluated.

`spread = max(1.4826 × MAD, 3% of baseline, 3 ppm)` is a robust estimate of the site's normal variation with a floor for sensor noise.

## 4. Derived metrics

| Metric | Definition |
|---|---|
| Current deviation | `(current − baseline) / baseline × 100` |
| z-score | `(current − baseline) / spread` |
| Rolling mean and standard deviation | over the last 24 hours |
| Rate of change | `(current − value about 3 h ago) / hours`, in ppm per hour |
| Trend | least-squares slope of the last 7 days as % of baseline per day: above +0.7 increasing, below −0.7 decreasing, otherwise stable |
| Persistence | share of evaluation points with deviation of at least 15% and at least 2 spreads from baseline |

## 5. HydroSense anomaly score (0 to 100)

| Part | Weight | Full weight at |
|---|---|---|
| Magnitude | 55 | 70% deviation |
| Significance | 15 | z-score of 4 |
| Persistence | 20 | all evaluation points unusual |
| Dynamics | 10 | 10% per hour, or 2% per day trend |

| Score | State | Extra condition |
|---|---|---|
| below 20 | Stable | |
| 20 to 44 | Needs attention | |
| 45 to 84 | Unusual change | persistence at least 50% |
| 85 or more | Active alert | persistence at least 50% |

A high score without persistence is capped at "Needs attention". These are HydroSense monitoring states, not certified water-safety classes, and the score is a transparent heuristic, not a validated environmental index. The weights were chosen for interpretability and have not been calibrated against field data.

## 6. Alerts

- **Unusual change (medium)** or **significant change (high):** raised when the state becomes unusual or alert, which already requires persistence. Escalated if it worsens.
- **Gradual trend (low):** raised when a site is drifting (at least 0.7% per day, at least 10% from baseline, every evaluation point at least 8% off) without a sudden jump.
- **Automatic resolution:** when the state is stable and no evaluation point is unusual. Drift alerts additionally wait until all points are within 5% of baseline, which prevents flapping.

Alerts are always worded as potential anomalies.

## 7. Assessment

Evidence points:

- Sensor: `anomaly score × 0.7` (up to 70).
- Citizens, last 72 hours, capped at 40: algae 12, floating waste 8, cloudiness 5 (slight) or 10 (very), smell 5 (mild) or 10 (strong), plus 5 when two or more concerning reports agree.

| Total | Assessment |
|---|---|
| below 20 | No signs of stress |
| 20 to 44 | Worth watching |
| 45 to 87 | Potential ecosystem stress |
| 88 or more | Elevated potential stress |

**Confidence** (15 to 95) expresses how much evidence supports the assessment, not the probability of pollution: 30 base, up to 15 for length of history, up to 12 for consistency of recent readings, 2 per observation up to 8, 5 for fresh sensor data, plus 12 when sensor and citizens agree or minus 5 when they disagree. Sites still learning are capped at 45.

## 8. Optional LLM

If `ANTHROPIC_API_KEY` is set, the structured evidence (the same JSON shown under "Evidence" in the AI Insights page) is sent to a Claude model, which returns a 2 to 3 sentence plain-language explanation. The model cannot change the assessment, confidence, factors or recommendation, and is instructed to use only the supplied facts. On any error or refusal the rule-based text is used. HydroSense is fully functional without it.
