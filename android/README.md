# HydroSense for Android

Native Android client for the existing HydroSense platform. It uses the **same** FastAPI backend, Supabase database and Supabase Storage bucket as the web app: there is no separate mobile backend and no mobile-only tables.

```text
ESP32 ──▶ FastAPI (Vercel) ──▶ Supabase
                 ▲                 ▲ (photo upload, anon key, RLS)
     ┌───────────┴───────────┐     │
  Web app (React)      Android app (Kotlin) ──┘
```

## Stack

Kotlin 2.2.10 · Jetpack Compose (BOM 2024.09) · Material 3 · Navigation Compose · ViewModel + StateFlow · Coroutines · Retrofit + OkHttp · kotlinx.serialization · Coil · osmdroid (OpenStreetMap). AGP 9.1.0, Gradle 9.3.1, compileSdk 36, minSdk 26, targetSdk 36.

## Build and run

Open the `android/` folder in Android Studio, or:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android
./gradlew :app:assembleDebug                         # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest                     # unit tests (fake server)
./gradlew :app:testDebugUnitTest -Dhydrosense.live=true   # + read-only checks against the deployed backend
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

Only client-safe values are compiled into the app:

| Value | Source |
|---|---|
| `API_BASE_URL` | default `https://hydrosense-beta.vercel.app/`, override in `android/hydrosense.properties` |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | `android/hydrosense.properties`, or the public `VITE_*` values in the repository `.env` |

The build script reads **only** the `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` lines of `.env`. The service-role key, database password and other server secrets are never read and must never be added: an APK can be decompiled.

## Architecture

- `data/Api.kt`: Retrofit interface over the existing REST endpoints.
- `data/Models.kt`: Kotlin data classes mirroring the API's JSON (sites, devices, measurements, observations, alerts, assessments).
- `data/Repository.kt`: reads with an on-device cache for offline use, photo compression and upload to the `observation-photos` bucket, report submission, and a persistent queue for reports made while offline.
- `ui/ViewModels.kt`: one ViewModel per screen exposing `StateFlow<UiState>`.
- `ui/screens/`: Home, Explore (map), Site details, Analytics, Report, Alerts, About.

## Behaviour

- **Demo / Live** toggle in About, same as the web app. Live mode polls every 6 s, demo every 30 s.
- **Reports** go to `POST /api/observations`, so they appear on the web immediately. Photos are compressed to 1600 px JPEG and uploaded with the anon key (RLS allows inserts under `citizen/` only).
- **Offline:** recently viewed data is shown from cache with a banner. A report made offline is stored on the device and sent automatically when the connection returns.
- Alert resolution is intentionally not exposed: it stays in the web monitoring dashboard.
- Assessments are shown as "AI-assisted interpretation" and state whether the wording came from the rule-based engine or a language model.
