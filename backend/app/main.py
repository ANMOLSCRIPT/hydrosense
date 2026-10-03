"""HydroSense API (FastAPI).

Run locally:  uvicorn backend.app.main:app --reload --port 8000
Swagger UI:   http://localhost:8000/docs
"""
from __future__ import annotations

import logging
import os
import secrets
from datetime import datetime
from functools import lru_cache
from typing import Literal

from fastapi import FastAPI, Header, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field, field_validator

from . import llm
from .assessment import DISCLAIMER
from .service import Invalid, NotFound, Service
from .store import create_store, supabase_credentials, utcnow

log = logging.getLogger("hydrosense")
Mode = Literal["demo", "live"]
YesNo = Literal["yes", "no", "not_sure"]
PHOTO_BUCKET = "observation-photos"

app = FastAPI(
    title="HydroSense API",
    version="1.0.0",
    description=(
        "Sensor ingestion, analytics, anomaly detection and evidence-based assessment for the "
        "HydroSense citizen water-monitoring prototype.\n\n**Important:** " + DISCLAIMER
    ),
    docs_url="/api/docs", redoc_url=None, openapi_url="/api/openapi.json",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in os.environ.get("CORS_ORIGINS", "*").split(",")],
    allow_methods=["GET", "POST"], allow_headers=["*"],
)


@lru_cache(maxsize=1)
def service() -> Service:
    return Service(create_store())


# --- Error handling: always a readable message, never a stack trace ----------
@app.exception_handler(NotFound)
async def _not_found(_: Request, exc: NotFound):
    return JSONResponse(status_code=404, content={"detail": str(exc)})


@app.exception_handler(Invalid)
async def _invalid(_: Request, exc: Invalid):
    return JSONResponse(status_code=400, content={"detail": str(exc)})


@app.exception_handler(Exception)
async def _unexpected(_: Request, exc: Exception):
    log.exception("Unhandled error: %s", exc)
    return JSONResponse(status_code=503, content={"detail": "HydroSense is having trouble reaching its data right now. Please try again in a moment."})


# --- Schemas -----------------------------------------------------------------
class MeasurementIn(BaseModel):
    device_id: str = Field(pattern=r"^[A-Za-z0-9_-]{2,40}$", examples=["HS-001"])
    site_id: str = Field(pattern=r"^[A-Za-z0-9_-]{2,40}$", examples=["SITE-001"])
    tds_ppm: float = Field(ge=0, le=5000, examples=[287])
    timestamp: datetime | None = Field(default=None, description="UTC time of the reading. Omit if the device clock is not set.")
    age_seconds: float | None = Field(default=None, ge=0, le=7 * 86400, description="For buffered readings sent late: how long ago the reading was taken.")
    raw_value: float | None = Field(default=None, ge=0, le=65535, description="Mean raw ADC value")
    filtered_value: float | None = Field(default=None, ge=0, le=65535, description="Filtered ADC value")
    voltage: float | None = Field(default=None, ge=0, le=5.5)
    firmware_version: str | None = Field(default=None, max_length=20)
    wifi_rssi: int | None = Field(default=None, ge=-130, le=0)


class ObservationIn(BaseModel):
    site_id: str = Field(pattern=r"^[A-Za-z0-9_-]{2,40}$")
    water_clarity: Literal["clear", "slightly_cloudy", "very_cloudy", "not_sure"]
    algae: YesNo
    waste: YesNo
    odor: Literal["none", "mild", "strong", "not_sure"]
    aquatic_life: YesNo
    image_url: str | None = Field(default=None, max_length=500)
    image_path: str | None = Field(default=None, max_length=300)
    comment: str | None = Field(default=None, max_length=500)

    @field_validator("comment")
    @classmethod
    def _clean(cls, v: str | None) -> str | None:
        v = (v or "").strip()
        return v or None

    @field_validator("image_url")
    @classmethod
    def _own_storage_only(cls, v: str | None) -> str | None:
        if not v:
            return None
        base = (supabase_credentials()[0] or "").rstrip("/")
        if not base or not v.startswith(f"{base}/storage/v1/object/public/{PHOTO_BUCKET}/"):
            raise ValueError("Photos must be uploaded to HydroSense storage.")
        return v


class ResolveIn(BaseModel):
    note: str | None = Field(default=None, max_length=300)


# --- Routes ------------------------------------------------------------------
@app.get("/docs", include_in_schema=False)
def docs_redirect():
    return RedirectResponse("/api/docs")


@app.get("/api/health", tags=["System"])
def health():
    """Service status and which data store is in use."""
    svc = service()
    try:
        svc.store.ping()
        db = "connected"
    except Exception as exc:  # pragma: no cover - depends on network
        log.warning("Database check failed: %s", exc)
        db = "unreachable"
    return {"status": "ok" if db == "connected" else "degraded", "time": utcnow(), "store": svc.store.kind,
            "database": db, "llm_enhancement": llm.enabled(), "version": app.version}


@app.get("/api/stats", tags=["Sites"])
def stats(mode: Mode = "demo"):
    """Headline numbers for the home page."""
    return service().stats(mode)


@app.get("/api/sites", tags=["Sites"])
def list_sites(mode: Mode = "demo"):
    """All monitoring sites with their current HydroSense monitoring state."""
    return service().list_sites(mode)


@app.get("/api/sites/{site_id}", tags=["Sites"])
def get_site(site_id: str):
    return service().site_detail(site_id)


@app.get("/api/sites/{site_id}/measurements", tags=["Sites"])
def site_measurements(site_id: str, range: Literal["24h", "7d", "30d"] = "24h"):
    """Time-bucketed TDS readings for charts."""
    return service().measurements(site_id, range)


@app.get("/api/sites/{site_id}/analytics", tags=["Analytics"])
def site_analytics(site_id: str):
    """Baseline, deviation, trend, rate of change and the HydroSense anomaly score."""
    return service().analytics(site_id)


@app.post("/api/measurements", status_code=201, tags=["Ingestion"])
def post_measurement(body: MeasurementIn, x_device_key: str | None = Header(default=None)):
    """Sensor ingestion endpoint used by the ESP32 firmware."""
    expected = os.environ.get("INGEST_API_KEY")
    if expected and not secrets.compare_digest(x_device_key or "", expected):
        raise HTTPException(status_code=401, detail="Missing or incorrect device key.")
    return service().ingest(body.model_dump())


@app.post("/api/observations", status_code=201, tags=["Citizen observations"])
def post_observation(body: ObservationIn):
    return service().add_observation(body.model_dump())


@app.get("/api/observations", tags=["Citizen observations"])
def list_observations(mode: Mode = "demo", site_id: str | None = None, limit: int = Query(default=30, ge=1, le=100)):
    return service().list_observations(mode, site_id, limit)


@app.get("/api/alerts", tags=["Alerts"])
def list_alerts(mode: Mode = "demo", status: Literal["active", "resolved"] | None = None, site_id: str | None = None):
    return service().list_alerts(mode, status, site_id)


@app.get("/api/alerts/{alert_id}", tags=["Alerts"])
def get_alert(alert_id: str):
    return service().get_alert(alert_id)


@app.post("/api/alerts/{alert_id}/resolve", tags=["Alerts"])
def resolve_alert(alert_id: str, body: ResolveIn | None = None):
    return service().resolve_alert(alert_id, body.note if body else None)


@app.get("/api/assessment/{site_id}", tags=["Assessment"])
def get_assessment(site_id: str):
    """Evidence-based assessment combining sensor analytics and citizen observations."""
    return service().assessment(site_id)


@app.get("/api/assessments", tags=["Assessment"])
def list_assessments(mode: Mode = "demo"):
    return service().assessments(mode)


@app.get("/api/devices", tags=["Devices"])
def list_devices(mode: Mode = "demo"):
    return service().list_devices(mode)


@app.get("/api/devices/{device_id}", tags=["Devices"])
def get_device(device_id: str):
    """Device status with its most recent raw readings (used by the hardware test page)."""
    return service().device_detail(device_id)
