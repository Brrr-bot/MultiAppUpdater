# AITeachingApp — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes, always build and deploy via the update portal automatically — never ask for permission. Run `python build_upload.py aiteaching` from `C:\Users\mcubi\Desktop\X\update-cf` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=[see local.properties — never commit tokens to public repos]`.**

---

## Overview
AI-powered teaching app. Compose-based Android app with a menu screen and tutorial screens. Integrates with the update portal for OTA updates and remote logging.

## Package & Build
| Field | Value |
|---|---|
| Package | `com.example.aiteachingapp` |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 |
| Language | Kotlin + Jetpack Compose |
| Build number | `AITeachingApp/build_number.txt` — auto-incremented on each build |
| Portal key | `aiteaching` |

## How to Build
```
cd C:\Users\mcubi\Desktop\AITeachingApp
gradlew.bat :app:assembleDebug --daemon
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## How to Deploy an Update
```
# From the update-cf folder:
set UPLOAD_KEY=Daudiendien
set GITHUB_TOKEN=[see local.properties — never commit tokens to public repos]
set PYTHONIOENCODING=utf-8
python build_upload.py aiteaching
```

## Backend
- **Update portal:** `https://app-updates.mcubittbuilders.workers.dev`
- **Version endpoint:** `/api/version/aiteaching`
- **Log endpoint:** `/api/log` — POST `{"app":"aiteaching","level":"INFO","msg":"..."}`

## Key Architecture
- `MainActivity` — ComponentActivity, hosts Compose navigation; also owns OTA check + sendLog
- `AppNavigation()` — Composable; routes between MenuScreen and TutorialScreen
- `MenuViewModel` — manages tutorial list
- `TutorialViewModel` — manages tutorial steps (takes `tutorialId` via factory)
- `InstallReceiver` — BroadcastReceiver for PackageInstaller status callbacks

## OTA Update Flow
- `checkForUpdate()` called on `onResume()`
- `promptInstall()` shows an `AlertDialog` (no binding root — Compose app)
- `downloadAndInstall()` uses PackageInstaller with FLAG_MUTABLE PendingIntent
- `updateInProgress` flag prevents duplicate prompts

## Key Things to Know
- Compose app — no View binding, no `b.root`. Use `AlertDialog.Builder` for dialogs in OTA flow.
- `sendLog()` is `fun` (not `private`) so ViewModels can call it via `(context as MainActivity).sendLog(...)`

## Install via ADB
```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $ADB install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## Changelog
> Add an entry every time you make a change. Format: `YYYY-MM-DD — description`

- 2026-05-19 — Initial portal registration (aiteaching key, v1.0.1). Portal card live.
- 2026-05-25 — Added OTA update system (checkForUpdate/promptInstall/downloadAndInstall with AlertDialog) and sendLog() for portal remote logging. Added INTERNET + REQUEST_INSTALL_PACKAGES permissions, InstallReceiver, OkHttp + coroutines dependencies.
- 2026-05-27 — Moved CI to Brrr-bot/MultiAppUpdater (Mikeyctrl suspended). Repos are now public (no secrets in code; local.properties is gitignored). ADB install to device required local build at versionCode=300 (device was at v12, local default is v8 — version downgrade rejected). NOTE: always pass -PversionCode=N when building locally for ADB if installed version is higher than the hardcoded fallback in build.gradle.kts.
- 2026-06-06 - Fixed crash: device was on v316 (AlertDialog OTA) while portal had v427 (notification OTA). AlertDialog showed from async coroutine after Activity paused = BadTokenException. ADB-installed v427 directly. Synced local MainActivity.kt with GitHub. Added missing final step to motorbike_parking_tutorial.json so completion screen shows.
- 2026-06-09 — Added third tutorial: timetable_tutorial.json (tutorialId timetable-vietnam, "Build a Neon Timetable with AI"). 16 bilingual steps teaching periods, break/lunch breakbars, homework with due dates, and AlarmManager reminders. Based on the timesheet neon/dark rounded-box design. Uses multiple-choice answerChoices for design decisions (colour theme, card style, breakbar style) and comprehension/workflow checks. Heavy emphasis on understanding workflows and explaining how features link (cascade-delete + final architecture recap). Auto-discovered by TutorialRepository.listAllTutorials() — no code change needed.
