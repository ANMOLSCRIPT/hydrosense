// HydroSense firmware configuration.
//
// 1. Copy this file to config.h (same folder).
// 2. Fill in your values.
// 3. config.h is in .gitignore: never commit Wi-Fi passwords or device keys.

#pragma once

// --- Wi-Fi (2.4 GHz only; the ESP32 cannot join 5 GHz networks) ---
#define WIFI_SSID "your-wifi-name"
#define WIFI_PASSWORD "your-wifi-password"

// --- HydroSense API: full URL of the measurements endpoint ---
#define API_URL "https://your-app.vercel.app/api/measurements"
// Only needed if INGEST_API_KEY is set on the server. Leave "" otherwise.
#define DEVICE_KEY ""

// --- Identity: the site must already exist in HydroSense ---
#define DEVICE_ID "HS-001"
#define SITE_ID "SITE-001"

// --- Sensor ---
// Use an ADC1 pin (GPIO 32-39). ADC2 pins do not work while Wi-Fi is on.
#define TDS_PIN 34
// Milliseconds between readings sent to the API.
#define READ_INTERVAL 10000
// Multiplier applied to the computed TDS. 1.0 = uncalibrated default.
// See docs/CALIBRATION.md: factor = reference ppm / displayed ppm.
#define TDS_CALIBRATION_FACTOR 1.0
// Assumed water temperature for compensation (no temperature probe in the MVP).
#define WATER_TEMPERATURE_C 25.0
