# Dashboard Tablet (HomeHub) — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes, always build and deploy via the update portal automatically — never ask for permission. Run `python build_upload.py dashboard` from `C:\Users\mcubi\Desktop\X\update-cf` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=[see local.properties — never commit tokens to public repos]`.**

---

## OTA Update Status

Dashboard already has a working OTA path.

Package: `com.homehub.dashboard` — portal key: `dashboard`

Current behaviour:
- `MainActivity` checks the update portal on startup
- When a newer build exists, the app downloads the APK to cache and posts a high-priority notification
- Tapping that notification opens the system package installer via `FileProvider`
- Startup version/device info is sent to the portal log stream

Implementation notes:
- Keep `InstallReceiver.kt` registered — older install code and shared patterns still reference it
- Keep `REQUEST_INSTALL_PACKAGES` and `POST_NOTIFICATIONS`
- The active install flow is the notification + `Intent.ACTION_VIEW` + `FileProvider` path, not the older in-app `PackageInstaller` Snackbar flow

Quick test:
```powershell
$env:UPLOAD_KEY="Daudiendien"; $env:GITHUB_TOKEN="[see local.properties — never commit tokens to public repos]"
python build_upload.py dashboard
```

Reopen the app. If a newer build exists, the update-ready notification should appear and open the system installer when tapped.

---

## Overview
A landscape-only tablet home dashboard. Displays the time, weather, alarm schedule, and integrates with the PhotoSync hub. Runs as a persistent kiosk-style app — always on, always visible on the hub tablet alongside the PhotoSync hub app.

## Package & Build
| Field | Value |
|---|---|
| Package | `com.homehub.dashboard` |
| Min SDK | 29 (Android 10) |
| Target SDK | 34 |
| Language | Kotlin |
| Build number | `Dashboard Tablet/build_number.txt` — auto-incremented on each build |

## How to Build
```
cd "C:\Users\mcubi\Desktop\X\Phone Tablet Sync\Dashboard Tablet"
gradlew.bat :app:assembleDebug --daemon
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## How to Deploy an Update
```
# From update-cf folder:
set UPLOAD_KEY=<secret>
python build_upload.py dashboard
```
Or via ADB (USB or Tailscale):
```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# USB:
& $ADB -s R52N70K3BRL install -r "app\build\outputs\apk\debug\app-debug.apk"
# Tailscale (after TCP ADB enabled):
& $ADB connect 100.126.58.18:5555
& $ADB -s 100.126.58.18:5555 install -r "app\build\outputs\apk\debug\app-debug.apk"
```

## Update Portal
`https://app-updates.mcubittbuilders.workers.dev` — tracks dashboard version.

## Key Architecture
- `MainActivity` — landscape-only, full-screen, persistent display
- `KeepAliveService` (foreground, dataSync) — keeps app alive
- `KeepAliveAccessibilityService` — OEM-proof background persistence
- `AlarmScheduler` / `AlarmReceiver` — manages alarm timing
- `HubRepository` — communicates with the PhotoSync hub HTTP server
- `WeatherRepository` — fetches weather data (dynamic config in settings)
- `BrightnessController` — controls tablet display brightness programmatically
- `SettingsActivity` — configure hub IP, weather location, etc.

## Permissions
- `INTERNET`, `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE` (dataSync type)
- `RECEIVE_BOOT_COMPLETED`, `REQUEST_INSTALL_PACKAGES`
- `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM`
- `USE_FULL_SCREEN_INTENT`

## Key Things to Know
- Runs on the **same tablet as the PhotoSync hub** (serial: `R52N70K3BRL`, Tailscale: `100.126.58.18`)
- Landscape-only — do not add portrait layouts
- `KeepAliveAccessibilityService` must be enabled in Accessibility Settings after first install
- Battery optimisation must be disabled so the service survives when screen is off
- TCP ADB is enabled on port 5555 via Tailscale — future installs don't need USB cable
- Dashboard version is separate from PhotoSync build number — has its own `build_number.txt`
- `build_number.txt` is in the project root: `Dashboard Tablet/build_number.txt`

---

## Changelog
> Add an entry every time you make a change. Format: `YYYY-MM-DD — description`

- 2026-05-14 — Rebuilt to build 43. Deployed via USB cable. TCP ADB enabled on port 5555 for Tailscale installs. Update portal connected (app-updates.mcubittbuilders.workers.dev).
- 2026-05-17 — Finance card: fetches all entries to find current salary period (salary→salary boundary, same logic as Finance app), shows IN/OUT/BAL/SAV spread vertically with weight spacers, full ₫X,XXX,XXX number format. Timesheet card: schedule always renders from hardcoded data (never shows offline), 2-column AM/PM layout (split at 12:00), no scroll, earned/hours updated from server independently. Builds 47 (ADB) and 48 (portal).
- 2026-05-25 — Added sendLog() function: ships log lines to update portal /api/log endpoint (app key "dashboard"). Visible in portal live log stream.
- 2026-05-17 — Implemented OTA update system: added InstallReceiver.kt with typed getParcelableExtra for Android 13+, registered in manifest, added checkForUpdate/promptInstall/downloadAndInstall to MainActivity using new portal endpoint /api/version/dashboard and FLAG_MUTABLE PendingIntent. OTA verified working via logcat (STATUS_PENDING_USER_ACTION handled, userIntent non-null, startActivity OK). Hub card is now half-width (weight 2 in a 4-weight row). Finance card shows IN/OUT/SAV from finances.mcubittbuilders.workers.dev. Timesheet card shows earned this month (computed from sessions × school rates) + today's class schedule (Mon–Sun hardcoded from Timesheet app SCHEDULE). Builds 46 (ADB direct) and 47 (portal OTA).
- 2026-05-27 — Moved CI to Brrr-bot/MultiAppUpdater (Mikeyctrl suspended). Repos are now public. Installed v206 from portal via ADB (no signature conflict — fresh install). TODO: OTA install path likely still uses PackageInstaller which is blocked on Samsung — should switch to Intent.ACTION_VIEW + FileProvider (see timesheet 2026-05-27 entry for full fix).
- 2026-06-10 — Cleaned up duplicate manifest entries (`POST_NOTIFICATIONS` and `FileProvider`) and updated this note to match the current notification-based OTA install flow already present on `main`.
