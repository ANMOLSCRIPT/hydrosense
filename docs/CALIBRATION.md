# TDS calibration

HydroSense estimates TDS from probe voltage. The result is an estimate from a low-cost probe, not a laboratory measurement.

## How the estimate is calculated (firmware)

1. 30 samples over 1.2 s using the ESP32's factory-calibrated millivolt reading.
2. Samples are sorted; the lowest and highest 20% are discarded; the rest are averaged.
3. Exponential smoothing between consecutive readings (alpha 0.4).
4. Temperature compensation: `v = volts / (1 + 0.02 × (T − 25))`, with `T = WATER_TEMPERATURE_C` (assumed, default 25 °C; there is no temperature probe in the MVP).
5. Conversion curve: `tds = (133.42·v³ − 255.86·v² + 857.39·v) × 0.5`
6. `tds = tds × TDS_CALIBRATION_FACTOR`

The curve in step 5 is the one published for the common Gravity-style analog TDS meter (0 to 1000 ppm). **Other modules can have a different response, and individual probes vary**, so the default factor of 1.0 is not universally accurate.

## Calibrating your probe

You need a reference: either a calibration solution of known TDS (for example 342 ppm or 1413 µS/cm NaCl solution) or a handheld TDS pen you trust.

1. Flash the firmware with `TDS_CALIBRATION_FACTOR 1.0`.
2. Rinse the probe, place it in the reference solution at about 25 °C, do not let it touch the container, and wait a minute.
3. Read the TDS value from the serial monitor or `/dashboard/devices/test`.
4. `factor = reference ppm ÷ displayed ppm` (example: 342 ÷ 318 = 1.075).
5. Set `TDS_CALIBRATION_FACTOR` in `config.h`, reflash, and confirm the reading now matches.

If you can, repeat with a second solution in a different range. If the factor differs a lot between solutions, your module does not follow the default curve and step 5 above should be replaced with one fitted to your module.

## What calibration does not fix

- Temperature: a fixed 25 °C is assumed. Roughly 2% error per °C of difference. Set `WATER_TEMPERATURE_C` to the typical water temperature, or add a temperature probe (future work).
- Fouling: biofilm on the probe shifts readings over days. Rinse it regularly.
- Range: above about 1000 ppm this class of probe becomes non-linear.

Because HydroSense compares each site with its own baseline, a consistent calibration error has little effect on change detection. It matters when comparing absolute values between sites.
