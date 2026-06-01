# Timesheet App — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes, always build and deploy via the update portal automatically — never ask for permission. Run `python build_upload.py timesheet` from `C:\Users\mcubi\Desktop\X\update-cf` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=[see local.properties — never commit tokens to public repos]`.**

---

## Overview
Teaching session tracker for Vietnam. Detects when the phone has been at a school/teaching centre for ≥45 minutes using GPS, logs a timesheet entry with school name, session type, period count, and earnings in VND. Syncs session data to the PhotoSync update server and shows a monthly timesheet report.

## Package & Build
| Field | Value |
|---|---|
| Package | `com.mcubi.timesheet` |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 |
| Language | Kotlin |
| Build number | `Timesheet/build_number.txt` — auto-incremented on each build |

## How to Build
```
cd C:\Users\mcubi\Desktop\X\Timesheet
gradlew.bat :app:assembleDebug --daemon
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## How to Deploy an Update
```
# From the update-cf folder:
set UPLOAD_KEY=<secret>
python build_upload.py timesheet
```
Builds, bumps build number, pushes to update portal. App auto-checks on launch.

## Backend
- **Previous update server:** `http://100.107.143.20:9000` (local Tailscale — being replaced)
- **New update portal:** `https://app-updates.mcubittbuilders.workers.dev`
- **Timesheet data endpoint (on old server):** `/timesheet?month=YYYY-MM` — parses `[TIMESHEET]` log lines
- **Today's schools endpoint:** `/today-schools` — returns unverified school visits for today

## Key Architecture
- `LocationForegroundService` — runs continuously, polls GPS every 60 seconds
- `DailySchoolCheckReceiver` — triggered periodically, detects school presence from location history
- `KeepAliveAccessibilityService` — keeps the foreground service alive on Samsung/aggressive OEMs
- School detection: checks GPS coordinates against a hardcoded list of school locations
- Log format: `[TIMESHEET] YYYY-MM-DD  School Name (type)  N period(s) × 45min = Xmin (Y.Yh)`
- Log format: `[SCHOOL] School Name (~N min) ★ teaching` — intermediate detection
- Sessions logged to the update server via POST `/log` and locally via `device_logs.txt`

## Permissions
- `INTERNET`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `RECEIVE_BOOT_COMPLETED`
- `POST_NOTIFICATIONS`
- Cleartext traffic enabled (for local server on Tailscale)

## Key Things to Know
- Background location permission must be granted manually in settings (Android 10+) — the app cannot request it directly
- Samsung devices require disabling battery optimisation manually for the location service to survive
- School list is hardcoded in the Kotlin source — update it there when adding new schools
- `KeepAliveAccessibilityService` must be enabled in Accessibility Settings once after install
- OTA updates go via `UpdateChecker("timesheet")` started in `MainActivity.onCreate()`

## Install via ADB
```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $ADB install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## Changelog
> Add an entry every time you make a change. Format: `YYYY-MM-DD — description`

- 2026-05-14 — Connected to update portal (app-updates.mcubittbuilders.workers.dev). Build system updated. APK deployed to phone via USB (build 1).
- 2026-05-15 — Fixed verify card not showing when location service was killed mid-session (likelyTaught now triggers at ≥20 min measured GPS time, not just ≥45 min wall-clock). Fixed OTA: now uses new portal endpoint /api/version/timesheet instead of old Tailscale server. Fixed silent install: uses PackageInstaller with USER_ACTION_NOT_REQUIRED instead of Intent.ACTION_VIEW. Added InstallReceiver.kt and REQUEST_INSTALL_PACKAGES permission. Build 2 deployed via portal (GitHub release timesheet-v2).
- 2026-05-16 — Long-press on any history session card now shows a context menu with Edit and Delete options. Delete removes the matching log line after confirmation. Edit shows a pre-filled dialog (school, date, periods) and replaces the log line in-place.
- 2026-05-18 — Re-notification on return visits: when the phone re-enters a school after a confirmed departure, the session accumulator (totalMs, firstTs, logged45) resets so the 35-min threshold can fire again for the next set of lessons. Installed via ADB.
- 2026-05-18 — Teaching detection threshold lowered from 45 min to 35 min (both LocationTracker and SchoolMatcher). Android notification now fires immediately when teaching is first detected (via onTeachingDetected callback in LocationTracker → DailySchoolCheckReceiver.fireTeachingNotification), so the notification and in-app verify card appear at the same time. Build 31 deployed via portal.
- 2026-05-21 — STEM Club period length restored to 60 min: PeriodCountActivity.periodLength() now accepts school name and maps type=tutoring (and any name containing "stem") to 60 min instead of defaulting to 45. Edit dialog now shows a "MINS PER PERIOD" editable field pre-filled from the logged session, so period length can be overridden on any entry. Build 38 deployed via portal.
- 2026-05-20 — Fixed polygon schools ignoring GPS accuracy drift. School.contains() now accepts an accuracyM parameter; for polygon schools it tries point-in-polygon first, then falls back to haversine(centre, radiusM + accuracyM) so that a GPS reading drifted slightly outside a tight polygon boundary still counts. LocationTracker now passes the live GPS accuracy to both the departure check loop and the accumulation loop (capped at 150m). Fixes An Lạc and other small-polygon schools not being detected when GPS drifts ~10-20m outside the boundary. Build 37 deployed via portal.
- 2026-05-24 — Added "Add Location" tab to the Add Session popup. Users can now define custom locations (name, type, GPS radius, period length, hourly or per-period rate) directly in the app. Location can be set via current GPS or via an interactive OpenStreetMap/Leaflet map picker with Nominatim address search and draggable pin. Saved locations are written to filesDir/user_locations.json, automatically loaded by SchoolMatcher for GPS detection, and their rates/period lengths are used in earnings calculations. New file: LocationPickerActivity.kt, assets/map_picker.html. Build 41 deployed via portal.
- 2026-06-01 — Added SUMMARY tab (3rd tab). Lists all-time work grouped by company category: Compass Education (default for any school that isn't STEM/Lotus), STEM Club (name contains "stem"), Lotus English Center (name contains "lotus"), plus any custom company assigned to a user-defined location. Each category card shows total periods + total earned, with a per-school breakdown; gold TOTAL ALL TIME row at the bottom. Tapping a company opens a month-by-month breakdown dialog (showCompanyMonthlyBreakdown) so months can be compared. Add Location popup gained a COMPANY/CATEGORY section: quick-pick COMPASS/STEM/LOTUS buttons + a free-text field for custom companies (typed text overrides the quick-pick). Category persisted in user_locations.json ("category" field). New: schoolCategory(), CAT_COMPASS/STEM/LOTUS constants, readAllSessions(), buildSummaryView(). showTab() changed from Boolean to Int (0=schedule,1=history,2=summary). NOTE: previous attempt at this feature was done in an isolated git worktree that got cleaned up — work was lost and re-done on main.
- 2026-05-25 — Added sendLog() function: ships log lines to update portal /api/log endpoint (app key "timesheet"). Visible in portal live log stream.
- 2026-05-16 — Fixed OTA update pipeline end-to-end.
- 2026-06-01 — Switched OTA to notification-based (no snackbar): app silently downloads the APK on update detection and posts a system notification ("Timesheet update ready — tap to install"); tapping opens the installer. Added POST_NOTIFICATIONS. CI now signs with the shared debug keystore (signingConfigs.debug reads -PdebugKeystore) so OTA-delivered APKs match installed signatures. Versioning offset bumped to run_number+300.
- 2026-06-01 — Fixed OOM crash when loading data. Root cause: SchoolMatcher.loadSchools() re-parsed the ~590KB hcmc_school_polygons.json asset on EVERY call, and fetchTodayPending() calls it on every onResume — repeated parsing churned the heap until OutOfMemoryError crashed the app. Fix: cache the parsed school list process-wide (SchoolMatcher companion cachedSchools, parsed once), invalidated via SchoolMatcher.invalidateCache() when the user saves a custom location. Also widened fetchTodayPending's catch to Throwable (OOM is an Error, not Exception, so the old catch never caught it). Verified: 6 resume cycles stable at 124MB PSS (was hitting 256MB ceiling). OFFLINE: timesheet already stores all sessions locally in filesDir/timesheet_log.txt and reads from it (readMonthSessions) — works fully offline; portal sendLog is the cloud log stream.
- 2026-05-27 — Fixed OTA install: replaced PackageInstaller with Intent.ACTION_VIEW + FileProvider (PackageInstaller's STATUS_PENDING_USER_ACTION is blocked by Samsung's background Activity restriction — install dialog never appeared, tap did nothing). Added res/xml/provider_paths.xml and FileProvider to AndroidManifest.xml. Fixed updateInProgress not resetting on download failure — caused snackbar to never reappear after a failed attempt until force-kill. NOTE: ADB installs must use local build (gradlew assembleDebug), not portal APK — GitHub Actions uses a different debug keystore causing INSTALL_FAILED_UPDATE_INCOMPATIBLE on devices with existing installation. Key fixes: (1) PendingIntent must use FLAG_MUTABLE so PackageInstaller can fill in EXTRA_STATUS/EXTRA_INTENT; (2) getParcelableExtra needs typed API on Android 13+; (3) session.close() after session.commit() abandons the install — removed it; (4) added updateInProgress flag to prevent double-download on activity resume. Install flow: Snackbar → tap INSTALL → downloads APK → system install dialog → one tap. Silent install not possible on Android 12+ for sideloaded apps. First install must use `pm install -i com.mcubi.timesheet` to set correct installer. Build 29 deployed.
