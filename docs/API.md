# API

Interactive documentation (Swagger UI): `/docs`. OpenAPI schema: `/api/openapi.json`.

Most list endpoints take `mode=demo|live` (default `demo`).

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Service status and active data store |
| GET | `/api/stats` | Headline counts for the home page |
| GET | `/api/sites` | Sites with current monitoring state |
| GET | `/api/sites/{site_id}` | One site with plain-language status and devices |
| GET | `/api/sites/{site_id}/measurements?range=24h\|7d\|30d` | Bucketed TDS series |
| GET | `/api/sites/{site_id}/analytics` | Baseline, deviation, trend, rate, anomaly score, anomaly markers |
| POST | `/api/measurements` | Sensor ingestion (ESP32) |
| POST | `/api/observations` | Submit a citizen observation |
| GET | `/api/observations?site_id=&limit=` | Recent observations |
| GET | `/api/alerts?status=&site_id=` | Alerts |
| GET | `/api/alerts/{id}` | One alert |
| POST | `/api/alerts/{id}/resolve` | Mark resolved, optional `{"note": "..."}` |
| GET | `/api/assessment/{site_id}` | Assessment with factors, explanation, evidence, history |
| GET | `/api/assessments` | Latest assessment for every site |
| GET | `/api/devices` | Device status |
| GET | `/api/devices/{device_id}` | Device with its recent raw readings |

## Ingestion

```bash
curl -X POST https://hydrosense-beta.vercel.app/api/measurements \
  -H 'Content-Type: application/json' \
  -d '{"device_id":"HS-001","site_id":"SITE-001","tds_ppm":287}'
```

Optional fields: `timestamp` (ISO 8601 UTC), `age_seconds` (for buffered readings), `raw_value`, `filtered_value`, `voltage`, `firmware_version`, `wifi_rssi`. If neither `timestamp` nor `age_seconds` is sent, the server's receive time is used. If `INGEST_API_KEY` is set on the server, send it in the `X-Device-Key` header.

Validation: `tds_ppm` must be 0 to 5000; the site must exist and must not be a demo site. Errors return `{"detail": "..."}` with 400, 401, 404 or 422.

## Observation

```json
{
  "site_id": "DEMO-04",
  "water_clarity": "clear | slightly_cloudy | very_cloudy | not_sure",
  "algae": "yes | no | not_sure",
  "waste": "yes | no | not_sure",
  "odor": "none | mild | strong | not_sure",
  "aquatic_life": "yes | no | not_sure",
  "image_url": null,
  "comment": "optional, up to 500 characters"
}
```
