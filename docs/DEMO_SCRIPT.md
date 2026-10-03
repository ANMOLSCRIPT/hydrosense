# Demo script (about 5 minutes)

| # | Action | What to say |
|---|---|---|
| 1 | Open the public URL | "HydroSense helps people understand what is happening with water around them." |
| 2 | Home page | Snapshot, four steps: Measure, Share, Understand, Act. Data is labelled Demo Data. |
| 3 | Click **Explore Water** | Four monitoring states, each with colour, icon and label. |
| 4 | Click **Water Site 04** | "Something has changed", in plain language. |
| 5 | **Understand this site** → trend chart | The green band is what is usual here. Switch 24 hours, 7 days, 30 days. |
| 6 | Scroll to citizen observations | Algae and floating waste reported by people nearby. |
| 7 | "What HydroSense thinks" | Sensor and citizens agree: potential ecosystem stress, not confirmed pollution. Open "Why?" then "Show data" to show progressive disclosure. |
| 8 | **Report what you see** | Complete the flow in under a minute; thank-you screen and badge. |
| 9 | **Monitoring dashboard** | KPIs, baseline, anomaly score, AI Insights with expandable evidence. |
| 10 | Switch to **Live Mode**, open Devices → Hardware test page | Power the ESP32. |
| 11 | Probe in plain water | Readings arrive every 10 seconds: raw ADC, filtered value, TDS. |
| 12 | Let it run a few minutes | The site moves from "Still learning" to "Stable" once it has about 20 readings (3 to 4 minutes at the default 10 s interval; set `READ_INTERVAL 5000` to halve it). |
| 13 | Stir a pinch of salt into the water | Dashboard updates; one odd reading does nothing. |
| 14 | Keep it there about 2 minutes | A sustained change raises an alert; show it in Alerts. |
| 15 | Back to **Explore Water** in Live Mode | The live site's marker has changed state. |

Tips: seed fresh demo data beforehand (`python scripts/seed_demo.py`), flash and test the ESP32 on the venue Wi-Fi, and keep `scripts/simulate_device.py` ready as a fallback for steps 10 to 14.
