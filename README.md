# Savr - native Android (Kotlin, Jetpack Compose)

MVVM - Compose Material 3 - Room - Retrofit/OkHttp - WorkManager (foreground, resumable) - Media3 - Coil - DataStore.

## Run
1. Open this folder in Android Studio (Koala+). Uses Gradle 8.9 / AGP 8.5.2 / JDK 17+ (Android Studio's bundled JBR works).
2. Set `API_BASE_URL` in `app/build.gradle.kts`.
3. Rename: `FOLDER` in `data/Models.kt` (gallery folder), `android:label` in the manifest, and `applicationId`.
4. Replace the placeholder launcher icon.

## AdMob
- SDK: `play-services-ads` + UMP (`user-messaging-platform`) for GDPR/US-state consent. Code: `ads/Ads.kt`.
- Flow: `MainActivity` -> `Ads.init()` gathers consent, then initializes the SDK off the main thread. No ad loads before consent.
- Placements: adaptive banner on Home (under the Download button), Downloads and Status (under the filter). Interstitial after a
  download is started or a status is saved, capped to every 2nd action and at most once per 60 s. No ads on player/onboarding/settings.
- Settings shows "Ad privacy choices" when the consent SDK says it is required.
- IDs: debug builds always use Google's test IDs. For release, set in `gradle.properties`:
  `ADMOB_APP_ID=ca-app-pub-xxx~yyy`, `ADMOB_BANNER_ID=ca-app-pub-xxx/zzz`, `ADMOB_INTERSTITIAL_ID=ca-app-pub-xxx/www`
  (if left blank, release falls back to test IDs). Also publish `app-ads.txt` on your developer website and set up the
  GDPR message in AdMob > Privacy & messaging.
- Tune caps in `Ads.INTERSTITIAL_EVERY` / `INTERSTITIAL_COOLDOWN_MS`.

## Layout
- `ui/theme` palette, light/dark, typography (34/17/13sp)
- `ui/components` iosCard, PrimaryButton (spring press + haptics), PillChip, Skeleton, EmptyState, LargeTitleScaffold, SegmentedControl
- `ui/screens` Main (onboarding, nav + bottom bar, home, share sheet), Library (downloads, player, captions), StatusSettings
- `data` models, Retrofit client, Room, DataStore, MediaStore saver, SAF status repo
- `work/DownloadWorker` HTTP Range resume, progress notification, wifi-only via WorkManager constraints
- `vm` ViewModels

## Backend (Instagram / Facebook / YouTube)
`backend/server.py` implements the contract below with yt-dlp, relays media through `/v1/stream` (Range/resume supported)
and merges separate video+audio streams with ffmpeg via `/v1/merge` (YouTube HD).
```
python -m venv backend/.venv
backend\.venv\Scripts\pip install -r backend/requirements.txt
backend\.venv\Scripts\python backend\server.py        # 0.0.0.0:8000, needs ffmpeg on PATH for HD merges
```
- Set `API_BASE_URL` in `gradle.properties` to the server (e.g. `http://<PC Wi-Fi IP>:8000/`). Debug builds allow plain http
  (`src/debug/AndroidManifest.xml`); release builds need https, so deploy the server behind TLS for production.
- Windows asks to allow Python through the firewall the first time: allow it on **Private** networks so the phone can connect.
- Many Instagram posts need a login: export your browser cookies (Netscape format) to `backend/cookies.txt`.
- Keep yt-dlp updated (`pip install -U yt-dlp`): platforms change often and old versions stop working.
- Downloading from these platforms is against their terms of service; Play Store may reject apps that do it.

## Putting the backend online
Local PC hosting only works on your Wi-Fi while the PC is on. For real users, deploy `backend/` to a cloud host:
1. Push this project to a GitHub repo (private is fine).
2. **Render** (easiest): render.com > New > Blueprint > select the repo. `render.yaml` builds `backend/Dockerfile`
   (Python + ffmpeg) and gives an https URL. Alternatives: Railway / Fly.io (same Dockerfile), or a VPS
   (DigitalOcean, Hetzner) running Docker behind Caddy/nginx for https.
3. Set `API_BASE_URL=https://<your-service>/` in `gradle.properties`, rebuild the app.
4. Instagram: add your cookies as a secret file `cookies.txt` (Render: Environment > Secret Files, path `/app/cookies.txt`).
Notes: cloud/datacenter IPs are often challenged by YouTube ("confirm you're not a bot") and rate-limited by Instagram;
cookies help, heavy use may need a residential proxy (yt-dlp `proxy` option). Merged HD files are cached on local
disk, so give the service enough disk or keep the free tier for testing only.

## Backend contract
`POST /v1/extract {"url": "..."}` ->
`{"title","caption","hashtags":[],"thumbnail","duration":42,"type":"video","qualities":[{"label":"HD|SD|Audio","url":"..."}]}`
Errors: 400 invalid, 422 unsupported, 403/404 private.

## Permissions by Android version
- API 26-28: WRITE_EXTERNAL_STORAGE (requested in onboarding)
- API 29+: none to save (MediaStore). API 33+: POST_NOTIFICATIONS. API 34+: FOREGROUND_SERVICE_DATA_SYNC (declared)
- WhatsApp statuses: SAF folder grant (persisted) - needed on Android 11+; also used on 10.

## Known gaps
- Bottom bar is translucent, not true blur: add the Haze library for backdrop blur.
- Language picker stores the choice only; add per-app locales + string resources to localize.
- Default font is system sans; add Inter to res/font for the exact look.
