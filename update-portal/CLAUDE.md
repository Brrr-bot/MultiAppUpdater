# App Update Portal — CLAUDE.md

> **Read this file before making any changes. Add a changelog entry at the bottom whenever you modify anything.**
> **After making any code changes to an Android app, always build and deploy via the portal automatically — never ask for permission. Use `python build_upload.py <app>` with `UPLOAD_KEY=Daudiendien` and `GITHUB_TOKEN=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`.**

---

## Overview
Cloudflare Worker that acts as the central OTA update hub for all Android apps. Provides:
- A dark web portal (`/`) showing live status cards for each app
- Version metadata storage (Durable Object + SQLite)
- APK hosting (GitHub Releases, proxied through the worker)
- Device log ingestion and live streaming to the portal
- Authenticated APK upload endpoint

**Portal URL:** `https://app-updates.mcubittbuilders.workers.dev`

## Apps Managed
| Key | Package | Color |
|---|---|---|
| hub | com.photosync.hub | cyan |
| client | com.photosync.client | teal |
| dashboard | com.homehub.dashboard | ice blue |
| finance | com.mcubi.finances | amber |
| timesheet | com.mcubi.timesheet | purple |

## Files
| File | Purpose |
|---|---|
| `worker.js` | Cloudflare Worker — all server logic + embedded portal HTML |
| `wrangler.toml` | Cloudflare config (DO binding, R2 when enabled) |
| `build_upload.py` | CLI: build any app + upload APK to portal |
| `android/UpdateChecker.kt` | Drop-in Kotlin class for each Android app |
| `android/provider_paths.xml` | FileProvider config required for APK installs |

## How to Deploy Changes to the Worker
```
cd C:\Users\mcubi\Desktop\X\update-cf
npx wrangler deploy
```

## API Endpoints
| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Web portal HTML |
| GET | `/api/status` | All app versions + metadata |
| GET | `/api/version/:app` | Version info for one app (polled by Android apps) |
| POST | `/api/version/:app` | Update version metadata (called after upload) |
| POST | `/upload/:app` | Upload APK (requires `X-Upload-Key` header) |
| GET | `/apk/:app` | Download APK (proxied from GitHub Releases) |
| POST | `/api/log` | Android devices post log lines here |
| GET | `/api/logs/:app` | Retrieve recent logs for an app |

## Build & Upload CLI
```bash
# Build one app and upload:
set UPLOAD_KEY=<secret>
python build_upload.py finance
python build_upload.py timesheet
python build_upload.py hub
python build_upload.py client
python build_upload.py dashboard
python build_upload.py all        # builds and uploads everything
```

## Secrets
| Secret | Purpose | Value | Set via |
|---|---|---|---|
| `UPLOAD_KEY` | Authenticates APK uploads | `Daudiendien` | `npx wrangler secret put UPLOAD_KEY` |
| `GITHUB_TOKEN` | Token for GitHub Releases (upload + proxy download) | see GitHub PAT | `npx wrangler secret put GITHUB_TOKEN` |

## How to Run build_upload.py
```
set UPLOAD_KEY=Daudiendien
set GITHUB_TOKEN=<your github PAT>
set PYTHONIOENCODING=utf-8
python build_upload.py client
```

## APK Storage (GitHub Releases)
APKs are stored as assets on a private GitHub repo. The worker proxies downloads using `GITHUB_TOKEN` so devices never need auth. To add a new release:
1. Build the APK
2. Run `build_upload.py <app>` — it uploads to GitHub and updates version metadata in the DO

## R2 Storage (not yet enabled)
R2 is commented out in `wrangler.toml`. To enable:
1. Enable R2 on Cloudflare dashboard (requires payment method — but free tier covers all usage)
2. `npx wrangler r2 bucket create app-updates-apks`
3. Uncomment the `[[r2_buckets]]` section in `wrangler.toml`
4. `npx wrangler deploy`

## Durable Object
`UpdateDO` stores:
- `versions` table: one row per app with version_code, version_name, updated_at
- `logs` table: last 500 log lines per app

## Adding a New App
1. Add entry to `APPS` dict in `worker.js`
2. Add entry to `APPS` dict in `build_upload.py`
3. Add build paths to `build_upload.py`
4. Redeploy: `npx wrangler deploy`
5. Copy `UpdateChecker.kt` to the new app and call `UpdateChecker(this, "newkey").start()`

---

## Changelog
> Add an entry every time you make a change. Format: `YYYY-MM-DD — description`

- 2026-05-14 — Initial deploy. Worker live at app-updates.mcubittbuilders.workers.dev. Durable Object with SQLite for version tracking + logs. R2 commented out pending account enablement. Portal shows 5 cards with live log polling. GitHub Releases chosen for APK storage.
- 2026-05-15 — GitHub Releases integration complete. build_upload.py now uploads APK to private repo Mikeyctrl/MultiAppUpdater as a release asset (tag: {app}-v{build}), stores asset URL in DO, worker proxies downloads via GITHUB_TOKEN. UPLOAD_KEY secret set to "Daudiendien". First successful upload: client v1.0.94.
- 2026-05-19 — Added "aiteaching" app (com.example.aiteachingapp, green #7fff7f) to worker.js APPS and build_upload.py. Deployed worker. Uploaded first APK: aiteaching v1.0.1 (8.6 MB) to GitHub release aiteaching-v1. Portal registered successfully.
