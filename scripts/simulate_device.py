#!/usr/bin/env python3
"""Pretend to be the ESP32: post TDS readings to the HydroSense API.

Useful for testing the live hardware flow without the physical sensor.

  python scripts/simulate_device.py --api http://localhost:8000 --count 60 --interval 0
  python scripts/simulate_device.py --api https://<your-app>.vercel.app --spike-after 40
"""
import argparse
import os
import random
import time

import httpx

p = argparse.ArgumentParser()
p.add_argument("--api", default="http://localhost:8000")
p.add_argument("--device", default="HS-001")
p.add_argument("--site", default="SITE-001")
p.add_argument("--base", type=float, default=285, help="usual TDS in ppm")
p.add_argument("--count", type=int, default=30)
p.add_argument("--interval", type=float, default=10, help="seconds between readings")
p.add_argument("--spike-after", type=int, default=0, help="after N readings, jump to --spike-to (e.g. salt added)")
p.add_argument("--spike-to", type=float, default=520)
p.add_argument("--backfill", type=float, default=0, help="spread readings over the past N seconds using age_seconds")
args = p.parse_args()

headers = {"X-Device-Key": os.environ["INGEST_API_KEY"]} if os.environ.get("INGEST_API_KEY") else {}
with httpx.Client(timeout=20) as http:
    for i in range(args.count):
        level = args.spike_to if args.spike_after and i >= args.spike_after else args.base
        tds = round(level + random.gauss(0, level * 0.01), 1)
        volts = tds / 430
        body = {"device_id": args.device, "site_id": args.site, "tds_ppm": tds, "voltage": round(volts, 3),
                "raw_value": round(volts / 3.3 * 4095 + random.gauss(0, 5)), "filtered_value": round(volts / 3.3 * 4095),
                "firmware_version": "sim-1.0", "wifi_rssi": -58}
        if args.backfill:
            body["age_seconds"] = round(args.backfill * (1 - (i + 1) / args.count), 1)
        r = http.post(f"{args.api}/api/measurements", json=body, headers=headers)
        print(i + 1, tds, r.status_code, r.json())
        if args.interval:
            time.sleep(args.interval)
