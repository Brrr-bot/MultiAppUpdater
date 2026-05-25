# Dashboard Tablet (HomeHub) — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes, always build and deploy via the update portal automatically — never ask for permission. Run `python build_upload.py dashboard` from `C:\Users\mcubi\Desktop\X\update-cf` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=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`.**

---

## ⚠️ OTA UPDATE SYSTEM — IMPLEMENT THIS FIRST BEFORE ANY OTHER CHANGES

This app does **not yet have a working OTA update system**. The Timesheet app has a fully working implementation. Before making any other changes to this app, implement OTA updates using the guide below.

Package: `com.homehub.dashboard` — portal key: `dashboard`

### What needs implementing (copy from Timesheet app)

**Step 1 — Copy `InstallReceiver.kt` from Timesheet**

Copy `C:\Users\mcubi\Desktop\x\Timesheet\app\src\main\kotlin\com\mcubi\timesheet\InstallReceiver.kt` to this app's kotlin package folder. Update the package name at the top to `com.homehub.dashboard`.

**Step 2 — Add to `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Inside `<application>`:
```xml
<receiver
    android:name=".InstallReceiver"
    android:exported="false" />
```

**Step 3 — Add OTA methods to MainActivity**

Add field at class level:
```kotlin
private var updateInProgress = false
```

Add methods (replace `b.root` with the activity's root binding view):
```kotlin
private fun checkForUpdate() {
    if (updateInProgress) return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val req = Request.Builder()
                .url("https://app-updates.mcubittbuilders.workers.dev/api/version/dashboard")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@launch
            val json = org.json.JSONObject(resp.body?.string() ?: return@launch)
            val serverCode = json.optInt("versionCode", 0)
            val apkUrl     = json.optString("apkUrl", "")
            if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                updateInProgress = true
                withContext(Dispatchers.Main) { promptInstall(serverCode, apkUrl) }
            }
        } catch (e: Exception) { }
    }
}

private fun promptInstall(serverCode: Int, apkUrl: String) {
    Snackbar.make(b.root, "Update available (build $serverCode)", Snackbar.LENGTH_INDEFINITE)
        .setAction("INSTALL") { downloadAndInstall(apkUrl) }
        .setActionTextColor(Color.parseColor("#FFB300"))
        .show()
}

private fun downloadAndInstall(apkUrl: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !packageManager.canRequestPackageInstalls()) {
        Toast.makeText(this, "Enable 'Install unknown apps' for this app in Settings", Toast.LENGTH_LONG).show()
        try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName"))) } catch (e: Exception) { }
        return
    }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val bytes = client.newCall(Request.Builder().url(apkUrl).build())
                .execute().body?.bytes() ?: return@launch
            val pi      = packageManager.packageInstaller
            val params  = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val sessionId = pi.createSession(params)
            val session   = pi.openSession(sessionId)
            session.openWrite("apk", 0, bytes.size.toLong()).use { out ->
                out.write(bytes); session.fsync(out)
            }
            val intent  = Intent(this@MainActivity, InstallReceiver::class.java)
            // FLAG_MUTABLE required — PackageInstaller fills in EXTRA_STATUS/EXTRA_INTENT
            val pending = android.app.PendingIntent.getBroadcast(
                this@MainActivity, sessionId, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
            // Do NOT call session.close() after commit
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
```

Call `checkForUpdate()` at the end of `onCreate()`.

**Step 4 — First install on device**

```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $ADB push "app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/app.apk
& $ADB shell pm install -i com.homehub.dashboard -r /data/local/tmp/app.apk
& $ADB shell rm /data/local/tmp/app.apk
```

**Step 5 — Test**

```powershell
$env:UPLOAD_KEY="Daudiendien"; $env:GITHUB_TOKEN="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"
python build_upload.py dashboard
```

Reopen the app — Snackbar "Update available" appears → tap INSTALL → tap Install on system dialog.

### Key bugs already hit in Timesheet — do NOT repeat

1. **`FLAG_IMMUTABLE` kills everything** — PackageInstaller can't fill in extras. Receiver fires with empty intent, `userIntent=null`, nothing happens. **Must use `FLAG_MUTABLE`.**

2. **`getParcelableExtra<Intent>()` returns null on Android 13+** — Already fixed in `InstallReceiver.kt`. Copy it as-is, don't rewrite it.

3. **`session.close()` after `session.commit()` abandons the install** — Don't add it.

4. **Silent install always requires one tap on Android 12+** — One tap on the system dialog is unavoidable for sideloaded apps. Not a bug.

5. **Double-install on activity resume** — The `updateInProgress` flag prevents a second download triggering when the activity resumes after the install dialog appears.

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
