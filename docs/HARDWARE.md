# Hardware: wiring, flashing and testing

## Parts

- ESP32 development board (ESP32-DevKitC / "ESP32 Dev Module", 30 or 38 pin)
- Analog TDS sensor module with probe ("Gravity"-style board: 3.3 to 5.5 V supply, analog output 0 to about 2.3 V)
- 3 jumper wires, USB cable

## Wiring (matches the firmware defaults)

```text
TDS sensor board        ESP32
----------------        -----
VCC  (+)           →    3V3
GND  (−)           →    GND
A    (analog out)  →    GPIO 34   (TDS_PIN)
```

Why this configuration:

- **Power from 3V3, not 5V.** ESP32 GPIO pins are not 5 V tolerant; the absolute maximum on an input is 3.6 V. Boards of this type run from 3.3 V and their output stays well below it, so powering from 3V3 is the safe default.
- **If your module's specification is unknown, or it requires 5 V:** measure the analog output with a multimeter while the probe is in your saltiest sample before connecting it to the ESP32. If it can exceed 3.3 V, add a voltage divider (for example 10 kΩ from output to GPIO 34 and 20 kΩ from GPIO 34 to GND) and account for the ratio through `TDS_CALIBRATION_FACTOR`.
- **GPIO 34 is on ADC1.** ADC2 pins cannot be read while Wi-Fi is active. Any of GPIO 32 to 39 works; change `TDS_PIN` to match.
- The probe is not designed for permanent immersion in flowing or dirty water, and must not be used in water above 55 °C. Keep the electronics dry.

## Firmware setup

1. `cd firmware/hydrosense && cp config.example.h config.h`
2. Edit `config.h`: `WIFI_SSID`, `WIFI_PASSWORD` (2.4 GHz network), `API_URL` (for example `https://hydrosense-beta.vercel.app/api/measurements`), `DEVICE_ID`, `SITE_ID`.
3. `config.h` is git-ignored. Never commit it.

## Flashing

**PlatformIO (recommended)**

```bash
pip install platformio
cd firmware
pio run                 # compile
pio run -t upload       # flash (hold BOOT if the board does not enter download mode)
pio device monitor      # serial log at 115200 baud
```

**Arduino IDE**

1. Boards Manager: install "esp32 by Espressif Systems".
2. Open `firmware/hydrosense/hydrosense.ino`.
3. Tools → Board → "ESP32 Dev Module", select the port, Upload.
4. Serial Monitor at 115200 baud.

No extra libraries are needed.

## What you should see

```text
HydroSense node HS-001 (site SITE-001), firmware 1.0.0
[wifi] connected, ip=192.168.1.42 rssi=-58 dBm
[sensor] raw=812 filtered=806 volts=0.650 tds=243.7 ppm (queued: 1)
[api] POST 201 {"ok":true,"id":1234,...}
```

On-board LED: solid = online and up to date, slow blink = sending backlog, fast blink = no Wi-Fi.

Open `/dashboard/devices/test` in HydroSense (switch to Live Mode) to watch raw ADC, filtered value, TDS and upload time update every 3 seconds.

## Behaviour when things go wrong

| Situation | Behaviour |
|---|---|
| Wi-Fi drops | Sensing continues. Up to 120 readings are buffered and sent oldest first on reconnect, each with its true age, so timestamps stay correct. |
| API unreachable or 5xx | Reading is kept; retry with exponential backoff from 2 s to 60 s. |
| API rejects a reading (400, 404, 422) | Reading is discarded and the reason is logged (usually a wrong `SITE_ID`). |
| Buffer full | Oldest reading is dropped. |

The node needs no clock: it sends `age_seconds` and the server computes the timestamp.

## Testing without hardware

```bash
python scripts/simulate_device.py --api https://hydrosense-beta.vercel.app --count 60 --interval 2 --spike-after 40
```
