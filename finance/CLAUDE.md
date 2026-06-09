# Finance App — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes, always build and deploy via the update portal automatically — never ask for permission. Run `python build_upload.py finance` from `C:\Users\mcubi\Desktop\X\update-cf` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=[see local.properties — never commit tokens to public repos]`.**

---

## ⚠️ OTA UPDATE SYSTEM — IMPLEMENT THIS FIRST BEFORE ANY OTHER CHANGES

This app does **not yet have a working OTA update system**. The Timesheet app has a fully working implementation. Before making any other changes to this app, implement OTA updates using the guide below.

### What was already done
- `UpdateChecker` class is referenced in MainActivity — check if it actually exists and works
- The update portal (`https://app-updates.mcubittbuilders.workers.dev`) is live and tracks this app under key `finance`
- `build_upload.py` builds and uploads to the portal correctly

### What needs implementing (copy from Timesheet app)

**Step 1 — Copy `InstallReceiver.kt` from Timesheet**

Copy `C:\Users\mcubi\Desktop\x\Timesheet\app\src\main\kotlin\com\mcubi\timesheet\InstallReceiver.kt` to this app's kotlin package folder and update the package name at the top.

**Step 2 — Add permission to `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Also register the receiver inside `<application>`:
```xml
<receiver
    android:name=".InstallReceiver"
    android:exported="false" />
```

**Step 3 — Implement `checkForUpdate()` and `downloadAndInstall()` in MainActivity**

Add these imports:
```kotlin
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

Add this field at class level:
```kotlin
private var updateInProgress = false
```

Add these methods (replace `b.root` with whatever the activity's root binding view is called):
```kotlin
private fun checkForUpdate() {
    if (updateInProgress) return
    android.util.Log.d("UpdateCheck", "starting — installed build ${BuildConfig.VERSION_CODE}")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val req = Request.Builder()
                .url("https://app-updates.mcubittbuilders.workers.dev/api/version/finance")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@launch
            val body = resp.body?.string() ?: return@launch
            val json = org.json.JSONObject(body)
            val serverCode = json.optInt("versionCode", 0)
            val apkUrl     = json.optString("apkUrl", "")
            if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                updateInProgress = true
                withContext(Dispatchers.Main) { promptInstall(serverCode, apkUrl) }
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateCheck", "failed: ${e::class.simpleName}: ${e.message}", e)
        }
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
        Toast.makeText(this, "Go to Settings → Install unknown apps → enable for this app", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")))
        } catch (e: Exception) { }
        return
    }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val req   = Request.Builder().url(apkUrl).build()
            val resp  = client.newCall(req).execute()
            if (!resp.isSuccessful) return@launch
            val bytes = resp.body?.bytes() ?: return@launch

            val pi      = packageManager.packageInstaller
            val params  = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(
                    android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = pi.createSession(params)
            val session   = pi.openSession(sessionId)
            session.openWrite("apk", 0, bytes.size.toLong()).use { out ->
                out.write(bytes)
                session.fsync(out)
            }
            val intent  = Intent(this@MainActivity, InstallReceiver::class.java)
            // FLAG_MUTABLE is required — PackageInstaller fills in EXTRA_STATUS/EXTRA_INTENT
            val pending = android.app.PendingIntent.getBroadcast(
                this@MainActivity, sessionId, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
            // Do NOT call session.close() after commit — it abandons the session
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

After building, install with `pm install -i` so the app is its own installer (required for the install dialog to work properly):
```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $ADB push "app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/app.apk
& $ADB shell pm install -i com.mcubi.finances -r /data/local/tmp/app.apk
& $ADB shell rm /data/local/tmp/app.apk
```

**Step 5 — Deploy a second build to test**

```powershell
$env:UPLOAD_KEY="Daudiendien"; $env:GITHUB_TOKEN="[see local.properties — never commit tokens to public repos]"
python build_upload.py finance
```

Then reopen the app — a Snackbar saying "Update available" should appear. Tap INSTALL, then tap Install on the system dialog. Done.

### Key bugs that WILL bite you (already hit in Timesheet — don't repeat)

1. **`FLAG_IMMUTABLE` on PendingIntent** — PackageInstaller cannot fill in `EXTRA_STATUS` or `EXTRA_INTENT` if the PendingIntent is immutable. The receiver fires with no extras, `userIntent` is null, nothing happens. **Always use `FLAG_MUTABLE`.**

2. **`getParcelableExtra<Intent>()` returns null on Android 13+** — Use the typed version:
   ```kotlin
   val userIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
       intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
   } else {
       @Suppress("DEPRECATION")
       intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
   }
   ```
   This is already handled correctly in `InstallReceiver.kt` — just copy it as-is.

3. **`session.close()` after `session.commit()`** — Calling `close()` immediately after `commit()` abandons the session. The receiver never fires. Do NOT call `close()`.

4. **Silent install always requires one tap on Android 12+** — `USER_ACTION_NOT_REQUIRED` doesn't bypass the system install dialog for sideloaded apps. The user will always need to tap Install once. This is an Android security restriction, not a bug.

5. **Double-install on activity resume** — The install dialog pauses then resumes the activity, which calls `checkForUpdate()` again and starts a second download. The `updateInProgress` flag prevents this.

6. **Snackbar not visible** — Was the original bug in Timesheet. The Snackbar was coded correctly but never fired because of bug #1 above. Once `FLAG_MUTABLE` is set, everything else works.

---

## Overview
Personal finance tracker. Records income and expenses across custom categories, grouped in 10th-to-10th billing cycles. Data lives in a Cloudflare Worker (Durable Object + SQLite).

## Package & Build
| Field | Value |
|---|---|
| Package | `com.mcubi.finances` |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 |
| Language | Kotlin |
| Build tool | Gradle (Kotlin DSL) |
| Build number | `Finance/build_number.txt` — auto-incremented on each build |

## How to Build
```
# Claude runs this — do not run manually unless needed
cd C:\Users\mcubi\Desktop\X\Finance
gradlew.bat :app:assembleDebug --daemon
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## How to Deploy an Update
```
# From the update-cf folder:
set UPLOAD_KEY=<secret>
python build_upload.py finance
```
This builds, bumps the build number, and pushes the APK + version metadata to the update portal.
The app checks for updates automatically on launch via UpdateChecker.

## Backend
- **Cloud backend:** `https://finances.mcubittbuilders.workers.dev`
- **Source:** `C:\Users\mcubi\Desktop\X\finance-cf\worker.js`
- Endpoints: `/api/add`, `/api/entries`, `/api/summary`, `/api/delete/{id}`
- **Update portal:** `https://app-updates.mcubittbuilders.workers.dev`
- **Update portal source:** `C:\Users\mcubi\Desktop\X\update-cf\worker.js`

## Key Architecture
- Single-activity app (`MainActivity.kt`)
- OkHttp for all API calls, Coroutines for async
- Data Binding enabled
- No local database — all data in Cloudflare DO
- Billing cycle runs 10th → 10th of month

## Permissions
- `INTERNET` only

## Install via ADB (first time or emergency)
```powershell
$ADB = "C:\Users\mcubi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $ADB install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## Changelog
> Add an entry every time you make a change. Format: `YYYY-MM-DD — description`

- 2026-05-14 — Added UpdateChecker OTA support. Backend URL: finances.mcubittbuilders.workers.dev. Build system connected to update portal.
- 2026-05-16 — Implemented OTA update system: added InstallReceiver.kt, REQUEST_INSTALL_PACKAGES permission + receiver in manifest, checkForUpdate/promptInstall/downloadAndInstall in MainActivity, buildConfig=true for BuildConfig.VERSION_CODE, checkForUpdate() called in onCreate(). Installed v1.0.5 via pm install -i to set correct installer. Key: FLAG_MUTABLE on PendingIntent, no session.close() after commit, updateInProgress guard.
- 2026-05-16 — Added savings total to header. Month periods now start when a "Salary" entry is added (not 10th-to-10th). On each new salary, previous period's leftover balance is automatically moved to savings (credit if positive, debit if negative). Backend: added savings table + GET /api/savings + POST /api/savings/adjust endpoints to worker.js. Removed carryover row from history.
- 2026-05-16 — Updated web portal (worker.js embedded HTML): added savings pill to topbar, replaced calendar-month navigation with salary-period navigation, balance pill and history now use salary-period date ranges, saving a Salary entry settles previous period balance into savings, switched currency display to ₫ VND format with no decimals.
- 2026-05-24 — Added OCR receipt scanning: ML Kit text-recognition (on-device, bundled), SCAN RECEIPT button on Add tab, regex extracts VND (đ/₫/VND) and standard currency amounts. Single amount auto-fills the amount field; multiple amounts show a multi-select dialog to pick which to save — all checked amounts saved as separate entries using current direction/category/description.
- 2026-05-24 — Improved date extraction: more permissive English month name regex (0+ spaces, "22 May 2026" / "22May2026"), added no-year fallback (uses current year), also handles "May 22 2026" order. Vietnamese month patterns and 2-digit year support added in v1.0.20.
- 2026-05-27 — Fixed OTA install: replaced PackageInstaller with Intent.ACTION_VIEW + FileProvider (PackageInstaller's STATUS_PENDING_USER_ACTION is blocked by Samsung — install dialog never appeared). Added res/xml/provider_paths.xml and FileProvider to AndroidManifest.xml. Fixed updateInProgress not resetting on download failure — snackbar would never reappear after a failed attempt until force-kill. NOTE: ADB installs must use local build (gradlew assembleDebug), not the portal APK — GitHub Actions debug keystore differs from local, causing INSTALL_FAILED_UPDATE_INCOMPATIBLE. App was uninstalled then reinstalled fresh during this session (no data loss — data is in Cloudflare DO).
- 2026-06-02 — Added home screen widget: FinanceWidget (AppWidgetProvider), QuickAddActivity (dialog-style popup with amount + category + description), widget_finance.xml (2-button layout), widget_info.xml (2x1 cell). Widget buttons open QuickAddActivity which saves locally via FinanceDb and pushes to cloud — same offline-first pattern as MainActivity.
- 2026-06-09 — Fixed receipt scanning on Android 13+: removed unnecessary media-library permissions and launch the system document picker directly. `GetContent` grants access to the selected URI, so users no longer have to grant broad photo access before scanning a receipt or screenshot.
- 2026-06-09 — Added Grab and Shopee expense categories, including local migration/normalization of historical descriptions containing Grab, Shopee, or Shoppee. Summary now uses the same salary-period boundaries as History/header, assigns every expense to exactly one category using a SQL classification pass, and sorts categories by spend. Redesigned the home widget as a tall rounded, vertically centered, icon-only wallet control and changed widget popup action text to white.
- 2026-06-09 — Made Summary category cards expandable. Tapping a category now shows the exact transactions included in its total, with date, description, and amount; detail queries reuse the same Grab/Shopee classification logic as category aggregation.
- 2026-06-09 — Added transaction editing from History and Summary. Long-pressing a History card or an expanded Summary transaction opens themed Edit/Delete actions; editing preserves its direction/date and allows amount, description, and category changes. Edits update locally immediately, then replace the cloud record and refresh from the server without depending on `/api/add` returning a new ID.
- 2026-06-09 — Reused the widget QuickAdd popup for transaction editing with all existing values prefilled. Added durable merchant keyword rules with automatic category selection and rule management, live History search across descriptions/categories/amounts/dates, and per-category budgets with remaining/over indicators on Summary.
