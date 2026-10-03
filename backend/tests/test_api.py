"""End-to-end API tests against the in-memory store (no credentials needed)."""
import os

os.environ["HYDROSENSE_STORE"] = "memory"
os.environ.pop("INGEST_API_KEY", None)
os.environ.pop("ANTHROPIC_API_KEY", None)

from fastapi.testclient import TestClient  # noqa: E402

from backend.app.main import app  # noqa: E402

c = TestClient(app)


def test_health_and_docs():
    assert c.get("/api/health").json()["status"] == "ok"
    assert c.get("/api/openapi.json").status_code == 200


def test_demo_sites_cover_every_scenario():
    sites = {s["id"]: s for s in c.get("/api/sites").json()}
    assert len(sites) == 6
    assert sites["DEMO-01"]["status"] == "stable"
    assert sites["DEMO-02"]["status"] == "stable"
    assert sites["DEMO-03"]["status"] == "attention"
    assert sites["DEMO-04"]["status"] == "unusual"
    assert sites["DEMO-05"]["status"] == "stable"
    assert sites["DEMO-06"]["status"] == "alert"
    assert 35 < sites["DEMO-04"]["deviation_percent"] < 48


def test_site_detail_series_and_analytics():
    d = c.get("/api/sites/DEMO-04").json()
    assert d["citizen"]["headline"] == "Something has changed"
    for rng, low in (("24h", 20), ("7d", 150), ("30d", 150)):
        pts = c.get(f"/api/sites/DEMO-04/measurements?range={rng}").json()["points"]
        assert len(pts) >= low
    a = c.get("/api/sites/DEMO-04/analytics").json()
    assert a["baseline_tds"] and a["anomalies"] and a["persistence"] >= 0.5
    assert c.get("/api/sites/NOPE").status_code == 404


def test_alerts_lifecycle():
    alerts = c.get("/api/alerts").json()
    by_site = {}
    for a in alerts:
        by_site.setdefault(a["site_id"], []).append(a)
    assert any(a["status"] == "active" and a["severity"] == "medium" for a in by_site["DEMO-04"])
    assert any(a["status"] == "active" and a["severity"] == "high" for a in by_site["DEMO-06"])
    assert all(a["status"] == "resolved" for a in by_site["DEMO-05"])
    assert "DEMO-01" not in by_site
    target = by_site["DEMO-06"][0]
    assert c.get(f"/api/alerts/{target['id']}").json()["site_name"] == "Water Site 06"
    done = c.post(f"/api/alerts/{target['id']}/resolve", json={"note": "Checked on site"}).json()
    assert done["status"] == "resolved" and done["resolution_note"] == "Checked on site"
    assert c.get("/api/alerts/does-not-exist").status_code == 404


def test_assessment_uses_sensor_and_citizen_evidence():
    r = c.get("/api/assessment/DEMO-04").json()
    assert r["risk_level"] in ("potential_stress", "elevated")
    keys = {f["key"] for f in r["contributing_factors"]}
    assert {"tds_change", "algae", "waste", "cloudiness"} <= keys
    assert 50 <= r["confidence"] <= 95 and r["source"] == "rules"
    assert c.get("/api/assessment/DEMO-01").json()["risk_level"] == "stable"
    assert len(c.get("/api/assessments").json()) == 6


def test_observation_validation_and_submit():
    bad = c.post("/api/observations", json={"site_id": "DEMO-01", "water_clarity": "purple"})
    assert bad.status_code == 422
    body = {"site_id": "DEMO-01", "water_clarity": "clear", "algae": "no", "waste": "no", "odor": "none",
            "aquatic_life": "yes", "comment": "  test  "}
    ok = c.post("/api/observations", json=body)
    assert ok.status_code == 201 and ok.json()["comment"] == "test"
    assert c.post("/api/observations", json={**body, "image_url": "https://evil.example/x.jpg"}).status_code == 422
    assert c.get("/api/observations?site_id=DEMO-01").json()[0]["comment"] == "test"


def test_live_hardware_flow_raises_alert_only_when_sustained():
    assert c.get("/api/sites?mode=live").json()[0]["status"] == "no_data"
    post = lambda tds, age: c.post("/api/measurements", json={  # noqa: E731
        "device_id": "HS-001", "site_id": "SITE-001", "tds_ppm": tds, "age_seconds": age, "raw_value": 800, "wifi_rssi": -60})
    assert c.post("/api/measurements", json={"device_id": "HS-001", "site_id": "DEMO-01", "tds_ppm": 1}).status_code == 400
    assert c.post("/api/measurements", json={"device_id": "HS-001", "site_id": "SITE-001", "tds_ppm": -4}).status_code == 422
    for i in range(60):                       # ten minutes of normal readings
        r = post(285 + (i % 3), 900 - i * 10)
        assert r.status_code == 201
    assert r.json()["site_status"] == "stable"
    assert post(600, 295).json()["site_status"] == "stable"   # one noisy reading is ignored
    for i in range(4):
        post(286, 290 - i * 10)
    for i in range(20):                       # sustained change (e.g. salt added)
        r = post(520, 250 - i * 10)
    assert r.json()["site_status"] in ("unusual", "alert")
    live_alerts = c.get("/api/alerts?mode=live&status=active").json()
    assert len(live_alerts) == 1
    dev = c.get("/api/devices/HS-001").json()
    assert dev["status"] == "online" and dev["latest"]["tds_ppm"] == 520
    assert c.get("/api/stats?mode=live").json()["active_sensors"] == 1
