# App Update Portal

Dark web portal for building, uploading, and OTA-updating all Android apps.
Hosted on Cloudflare — works from anywhere, no same-network requirement.

## Apps managed
| Key        | Package                  |
|------------|--------------------------|
| hub        | com.photosync.hub        |
| client     | com.photosync.client     |
| dashboard  | com.homehub.dashboard    |
| finance    | com.mcubi.finances       |
| timesheet  | com.mcubi.timesheet      |

---

## 1 — First-time deploy

```bash
cd update-cf
npm install

# Create the R2 bucket
npx wrangler r2 bucket create app-updates-apks

# Set the upload secret (pick any password)
npx wrangler secret put UPLOAD_KEY

# Deploy
npx wrangler deploy
```

Note the worker URL printed by deploy (e.g. `https://app-updates.mcubi.workers.dev`).

---

## 2 — Fill in the worker URL

Edit two files:

**build_upload.py** line 36:
```python
WORKER_URL = "https://app-updates.mcubi.workers.dev"
```

**android/UpdateChecker.kt** line 43:
```kotlin
private const val WORKER_URL = "https://app-updates.mcubi.workers.dev"
```

---

## 3 — Build & upload an APK

```bash
# Set key once per session
set UPLOAD_KEY=your-secret-key

# Build + upload one app
python build_upload.py finance
python build_upload.py timesheet

# Build + upload everything
python build_upload.py all
```

Or upload a pre-built APK via the web portal's Upload button.

---

## 4 — Add UpdateChecker to each Android app

### Finance / Timesheet

1. Copy `android/UpdateChecker.kt` to the app's kotlin source folder
2. Fix the `package` line at the top to match the app
3. Copy `android/provider_paths.xml` to `app/src/main/res/xml/provider_paths.xml`
4. Add to `AndroidManifest.xml` inside `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider_paths" />
</provider>
```

5. Call from `MainActivity.onCreate()`:

```kotlin
UpdateChecker(this, "finance").start()   // or "timesheet"
```

### Silent install note

Silent install (no tap required) works on Android 12+ (API 31).  
On Android 8–11, a system install dialog appears once.  
After that first install, all future updates are silent.

---

## 5 — Portal

Open the worker URL in any browser. The portal shows:
- Live version per app
- Last updated timestamp
- Upload button per app
- Live log stream (device → portal every 2s)
