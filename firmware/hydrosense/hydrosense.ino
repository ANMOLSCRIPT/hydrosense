/*
 * HydroSense sensor node firmware (ESP32 + analog TDS probe)
 *
 * Reads a TDS probe, filters the signal, estimates TDS in ppm and posts it to
 * the HydroSense API over Wi-Fi. Readings taken while offline are kept in a
 * small buffer and sent, oldest first, when the connection returns.
 *
 * Builds with the Arduino IDE (ESP32 board package) or PlatformIO.
 * Configuration lives in config.h (copy config.example.h).
 *
 * TDS is an estimate from a low-cost probe. It is not laboratory grade.
 */
#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>

#include "config.h"   // copy config.example.h to config.h and fill it in

#define FIRMWARE_VERSION "1.0.0"
#define STATUS_LED 2            // on-board LED on most ESP32 dev boards
#define SAMPLE_COUNT 30         // analog samples per reading
#define SAMPLE_DELAY_MS 40      // 30 x 40 ms = 1.2 s sampling window
#define TRIM_FRACTION 0.2       // drop the lowest and highest 20% as outliers
#define SMOOTHING_ALPHA 0.4     // exponential smoothing between readings (1 = off)
#define BUFFER_SIZE 120         // readings kept while offline (20 min at 10 s)
#define WIFI_RETRY_MS 10000
#define HTTP_TIMEOUT_MS 8000
#define MAX_SEND_PER_LOOP 5     // drain the backlog gradually

struct Reading {
  uint32_t takenAt;   // millis() when the reading was taken
  float tds;          // ppm
  float volts;        // filtered probe voltage
  uint16_t rawAdc;    // mean raw ADC count
  uint16_t filteredAdc;
};

Reading buffer[BUFFER_SIZE];
uint16_t head = 0, count = 0;   // ring buffer: oldest item at `head`

uint32_t lastReadAt = 0, lastWifiAttempt = 0, nextSendAt = 0;
uint32_t sendBackoffMs = 0;
float smoothedMv = -1;
bool wifiWasConnected = false;

// ---------------------------------------------------------------------------
// Sensor
// ---------------------------------------------------------------------------

// Take SAMPLE_COUNT samples, discard outliers and return the filtered voltage.
float readFilteredMillivolts(uint16_t &rawMean) {
  static uint16_t mv[SAMPLE_COUNT];
  uint32_t rawSum = 0;
  for (int i = 0; i < SAMPLE_COUNT; i++) {
    rawSum += analogRead(TDS_PIN);
    mv[i] = analogReadMilliVolts(TDS_PIN);   // uses the chip's factory ADC calibration
    delay(SAMPLE_DELAY_MS);
  }
  rawMean = rawSum / SAMPLE_COUNT;

  // insertion sort (30 values) so the extremes can be trimmed
  for (int i = 1; i < SAMPLE_COUNT; i++) {
    uint16_t v = mv[i];
    int j = i - 1;
    while (j >= 0 && mv[j] > v) { mv[j + 1] = mv[j]; j--; }
    mv[j + 1] = v;
  }
  int trim = SAMPLE_COUNT * TRIM_FRACTION;
  uint32_t sum = 0;
  for (int i = trim; i < SAMPLE_COUNT - trim; i++) sum += mv[i];
  float trimmedMean = (float)sum / (SAMPLE_COUNT - 2 * trim);

  // smooth between consecutive readings to damp residual noise
  if (smoothedMv < 0) smoothedMv = trimmedMean;
  else smoothedMv = SMOOTHING_ALPHA * trimmedMean + (1 - SMOOTHING_ALPHA) * smoothedMv;
  return smoothedMv;
}

// Convert probe voltage to a TDS estimate in ppm.
// Curve published for the common "Gravity"-style analog TDS meter (0-1000 ppm).
// Other modules may need a different curve: see docs/CALIBRATION.md.
float voltageToTds(float volts) {
  float compensation = 1.0 + 0.02 * (WATER_TEMPERATURE_C - 25.0);
  float v = volts / compensation;
  float tds = (133.42 * v * v * v - 255.86 * v * v + 857.39 * v) * 0.5;
  tds *= TDS_CALIBRATION_FACTOR;
  return tds < 0 ? 0 : tds;
}

void takeReading() {
  uint16_t rawMean = 0;
  float mvFiltered = readFilteredMillivolts(rawMean);
  Reading r;
  r.takenAt = millis();
  r.volts = mvFiltered / 1000.0;
  r.tds = voltageToTds(r.volts);
  r.rawAdc = rawMean;
  r.filteredAdc = (uint16_t)(mvFiltered / 3300.0 * 4095.0);

  if (count == BUFFER_SIZE) {            // buffer full: drop the oldest reading
    head = (head + 1) % BUFFER_SIZE;
    count--;
    Serial.println("[buffer] full, dropped oldest reading");
  }
  buffer[(head + count) % BUFFER_SIZE] = r;
  count++;
  Serial.printf("[sensor] raw=%u filtered=%u volts=%.3f tds=%.1f ppm (queued: %u)\n",
                r.rawAdc, r.filteredAdc, r.volts, r.tds, count);
  if (r.rawAdc < 5) Serial.println("[sensor] reading is ~0: is the probe in water and wired to TDS_PIN?");
  if (r.rawAdc > 4090) Serial.println("[sensor] ADC saturated: check wiring and supply voltage");
}

// ---------------------------------------------------------------------------
// Network
// ---------------------------------------------------------------------------
void maintainWifi() {
  bool connected = WiFi.status() == WL_CONNECTED;
  if (connected != wifiWasConnected) {
    wifiWasConnected = connected;
    if (connected) Serial.printf("[wifi] connected, ip=%s rssi=%d dBm\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    else Serial.println("[wifi] connection lost, readings will be buffered");
  }
  if (!connected && millis() - lastWifiAttempt >= WIFI_RETRY_MS) {
    lastWifiAttempt = millis();
    Serial.printf("[wifi] connecting to %s ...\n", WIFI_SSID);
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }
}

// Returns the HTTP status code, or a negative number on a transport error.
int postReading(const Reading &r) {
  char body[320];
  float ageSeconds = (millis() - r.takenAt) / 1000.0;
  snprintf(body, sizeof(body),
           "{\"device_id\":\"%s\",\"site_id\":\"%s\",\"tds_ppm\":%.1f,\"age_seconds\":%.1f,"
           "\"raw_value\":%u,\"filtered_value\":%u,\"voltage\":%.3f,"
           "\"firmware_version\":\"%s\",\"wifi_rssi\":%d}",
           DEVICE_ID, SITE_ID, r.tds, ageSeconds, r.rawAdc, r.filteredAdc, r.volts,
           FIRMWARE_VERSION, (int)WiFi.RSSI());

  HTTPClient http;
  WiFiClientSecure secure;
  WiFiClient plain;
  bool https = String(API_URL).startsWith("https");
  if (https) {
    // Encrypts traffic but does not verify the server certificate. Acceptable
    // for a prototype; pin a root CA with secure.setCACert() for deployment.
    secure.setInsecure();
    http.begin(secure, API_URL);
  } else {
    http.begin(plain, API_URL);
  }
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.addHeader("Content-Type", "application/json");
  if (strlen(DEVICE_KEY) > 0) http.addHeader("X-Device-Key", DEVICE_KEY);
  int code = http.POST((uint8_t *)body, strlen(body));
  if (code > 0) Serial.printf("[api] POST %d %s\n", code, http.getString().substring(0, 120).c_str());
  else Serial.printf("[api] POST failed: %s\n", http.errorToString(code).c_str());
  http.end();
  return code;
}

void sendQueued() {
  if (count == 0 || WiFi.status() != WL_CONNECTED || millis() < nextSendAt) return;
  for (int sent = 0; sent < MAX_SEND_PER_LOOP && count > 0; sent++) {
    int code = postReading(buffer[head]);
    bool delivered = code >= 200 && code < 300;
    // 400/404/422 mean the server rejected this reading: retrying will not help.
    bool rejected = code == 400 || code == 404 || code == 422;
    if (delivered || rejected) {
      head = (head + 1) % BUFFER_SIZE;
      count--;
      sendBackoffMs = 0;
      if (rejected) Serial.println("[api] reading rejected by server, discarded (check DEVICE_ID / SITE_ID)");
    } else {
      // network error, 401, 429 or 5xx: keep the reading and retry with backoff
      sendBackoffMs = sendBackoffMs == 0 ? 2000 : min(sendBackoffMs * 2, (uint32_t)60000);
      nextSendAt = millis() + sendBackoffMs;
      Serial.printf("[api] will retry in %lu s (%u queued)\n", (unsigned long)(sendBackoffMs / 1000), count);
      return;
    }
  }
}

// LED: solid = online and up to date, slow blink = sending backlog, fast blink = no Wi-Fi
void updateLed() {
  bool connected = WiFi.status() == WL_CONNECTED;
  if (!connected) digitalWrite(STATUS_LED, (millis() / 150) % 2);
  else if (count > 1) digitalWrite(STATUS_LED, (millis() / 600) % 2);
  else digitalWrite(STATUS_LED, HIGH);
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.printf("\nHydroSense node %s (site %s), firmware %s\n", DEVICE_ID, SITE_ID, FIRMWARE_VERSION);
  Serial.printf("TDS pin GPIO%d, interval %d ms, calibration factor %.3f\n", TDS_PIN, READ_INTERVAL, (float)TDS_CALIBRATION_FACTOR);
  pinMode(STATUS_LED, OUTPUT);
  pinMode(TDS_PIN, INPUT);
  analogReadResolution(12);
  analogSetPinAttenuation(TDS_PIN, ADC_11db);   // measure the full 0-3.3 V range
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttempt = millis();
  lastReadAt = millis() - READ_INTERVAL;        // take the first reading immediately
}

void loop() {
  maintainWifi();
  if (millis() - lastReadAt >= READ_INTERVAL) {  // sensing never waits for Wi-Fi
    lastReadAt = millis();
    takeReading();
  }
  sendQueued();
  updateLed();
  delay(20);
}
