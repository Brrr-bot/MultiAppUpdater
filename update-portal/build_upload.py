"""
build_upload.py  —  Build an Android app, upload APK to GitHub Releases, register with portal.

Usage:
    python build_upload.py <app>
    python build_upload.py hub
    python build_upload.py client
    python build_upload.py dashboard
    python build_upload.py finance
    python build_upload.py timesheet
    python build_upload.py all          # builds & uploads everything

Required env vars (or prompted):
    UPLOAD_KEY    — portal auth key
    GITHUB_TOKEN  — GitHub personal access token (repo scope)
"""

import os
import sys
import subprocess
import urllib.request
import urllib.error
import json
import re
from datetime import datetime

# ── Config ─────────────────────────────────────────────────────────────────────

WORKER_URL   = "https://app-updates.mcubittbuilders.workers.dev"
GITHUB_REPO  = "Mikeyctrl/MultiAppUpdater"
GITHUB_API   = "https://api.github.com"
GITHUB_UPLOAD= "https://uploads.github.com"
BASE         = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))

APPS = {
    "hub": {
        "project":  os.path.join(BASE, "Phone Tablet Sync", "PhotoSync"),
        "gradlew":  os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "gradlew.bat"),
        "task":     ":hubapp:assembleDebug",
        "apk":      os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "hubapp", "build",
                                 "outputs", "apk", "debug", "hubapp-debug.apk"),
        "build_number_file": os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "build_number.txt"),
        "gradle_file": os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "hubapp", "build.gradle.kts"),
    },
    "aiteaching": {
        "project":  os.path.join(os.path.expanduser("~"), "Desktop", "AITeachingApp"),
        "gradlew":  os.path.join(os.path.expanduser("~"), "Desktop", "AITeachingApp", "gradlew.bat"),
        "task":     ":app:assembleDebug",
        "apk":      os.path.join(os.path.expanduser("~"), "Desktop", "AITeachingApp", "app", "build",
                                 "outputs", "apk", "debug", "app-debug.apk"),
        "build_number_file": os.path.join(os.path.expanduser("~"), "Desktop", "AITeachingApp", "build_number.txt"),
        "gradle_file": os.path.join(os.path.expanduser("~"), "Desktop", "AITeachingApp", "app", "build.gradle.kts"),
    },
    "client": {
        "project":  os.path.join(BASE, "Phone Tablet Sync", "PhotoSync"),
        "gradlew":  os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "gradlew.bat"),
        "task":     ":clientapp:assembleDebug",
        "apk":      os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "clientapp", "build",
                                 "outputs", "apk", "debug", "clientapp-debug.apk"),
        "build_number_file": os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "build_number.txt"),
        "gradle_file": os.path.join(BASE, "Phone Tablet Sync", "PhotoSync", "clientapp", "build.gradle.kts"),
    },
    "dashboard": {
        "project":  os.path.join(BASE, "Phone Tablet Sync", "Dashboard Tablet"),
        "gradlew":  os.path.join(BASE, "Phone Tablet Sync", "Dashboard Tablet", "gradlew.bat"),
        "task":     ":app:assembleDebug",
        "apk":      os.path.join(BASE, "Phone Tablet Sync", "Dashboard Tablet", "app", "build",
                                 "outputs", "apk", "debug", "app-debug.apk"),
        "build_number_file": os.path.join(BASE, "Phone Tablet Sync", "Dashboard Tablet", "build_number.txt"),
        "gradle_file": os.path.join(BASE, "Phone Tablet Sync", "Dashboard Tablet", "app", "build.gradle.kts"),
    },
    "finance": {
        "project":  os.path.join(BASE, "Finance"),
        "gradlew":  os.path.join(BASE, "Finance", "gradlew.bat"),
        "task":     ":app:assembleDebug",
        "apk":      os.path.join(BASE, "Finance", "app", "build",
                                 "outputs", "apk", "debug", "app-debug.apk"),
        "build_number_file": os.path.join(BASE, "Finance", "build_number.txt"),
        "gradle_file": os.path.join(BASE, "Finance", "app", "build.gradle.kts"),
    },
    "timesheet": {
        "project":  os.path.join(BASE, "Timesheet"),
        "gradlew":  os.path.join(BASE, "Timesheet", "gradlew.bat"),
        "task":     ":app:assembleDebug",
        "apk":      os.path.join(BASE, "Timesheet", "app", "build",
                                 "outputs", "apk", "debug", "app-debug.apk"),
        "build_number_file": os.path.join(BASE, "Timesheet", "build_number.txt"),
        "gradle_file": os.path.join(BASE, "Timesheet", "app", "build.gradle.kts"),
    },
}

# ── Java detection ─────────────────────────────────────────────────────────────

def find_java_home():
    if os.environ.get("JAVA_HOME"):
        return os.environ["JAVA_HOME"]
    candidates = [
        r"C:\Program Files\Android\Android Studio\jbr",
        r"C:\Program Files\Android\Android Studio\jre",
    ]
    for c in candidates:
        if os.path.exists(os.path.join(c, "bin", "java.exe")):
            return c
    return None

# ── Helpers ────────────────────────────────────────────────────────────────────

def log(msg, tag=""):
    ts   = datetime.now().strftime("%H:%M:%S")
    tags = {"ok": "\033[92m✓", "err": "\033[91m✗", "build": "\033[95m⚙", "up": "\033[96m⬆"}
    pfx  = tags.get(tag, " ")
    print(f"{ts}  {pfx}  {msg}\033[0m")

def read_build_number(path):
    try:
        with open(path) as f:
            return int(f.read().strip())
    except Exception:
        return 1

def bump_build_number(path):
    n = read_build_number(path) + 1
    with open(path, "w") as f:
        f.write(str(n) + "\n")
    return n

def patch_gradle_version(gradle_file, version_code, version_name):
    """Update versionCode and versionName in build.gradle.kts to match the portal build number."""
    if not gradle_file or not os.path.exists(gradle_file):
        return
    with open(gradle_file, encoding="utf-8") as f:
        text = f.read()
    text = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {version_code}', text)
    text = re.sub(r'versionName\s*=\s*"[^"]*"', f'versionName = "{version_name}"', text)
    with open(gradle_file, "w", encoding="utf-8") as f:
        f.write(text)

# ── Build ──────────────────────────────────────────────────────────────────────

def build(app_key):
    cfg      = APPS[app_key]
    gradlew  = cfg["gradlew"]
    cwd      = cfg["project"]
    task     = cfg["task"]
    bnf      = cfg["build_number_file"]

    if not os.path.exists(gradlew):
        log(f"gradlew not found: {gradlew}", "err")
        return False

    n = bump_build_number(bnf)
    log(f"[{app_key}] build number → {n}  (v1.0.{n})", "build")
    patch_gradle_version(cfg.get("gradle_file"), n, f"1.0.{n}")


    env = os.environ.copy()
    jh  = find_java_home()
    if jh:
        env["JAVA_HOME"] = jh
        env["PATH"] = os.path.join(jh, "bin") + os.pathsep + env.get("PATH", "")
        log(f"[{app_key}] JAVA_HOME: {jh}", "build")
    else:
        log(f"[{app_key}] WARNING: JAVA_HOME not found", "err")

    log(f"[{app_key}] Running {task}…", "build")
    proc = subprocess.Popen(
        [gradlew, task, "--daemon"], cwd=cwd, env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    )
    for line in proc.stdout:
        line = line.rstrip()
        if line:
            print(f"          {line}")
    proc.wait()

    if proc.returncode != 0:
        log(f"[{app_key}] Build FAILED", "err")
        return False

    log(f"[{app_key}] Build successful  →  v1.0.{n}", "ok")
    return True

# ── GitHub Releases helpers ────────────────────────────────────────────────────

def gh_request(method, path, github_token, data=None, extra_headers=None):
    url = path if path.startswith("https://") else f"{GITHUB_API}{path}"
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"token {github_token}")
    req.add_header("Accept",        "application/vnd.github+json")
    req.add_header("User-Agent",    "build_upload/1.0")
    if extra_headers:
        for k, v in extra_headers.items():
            req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=900) as resp:
        return json.loads(resp.read())

def github_upload_apk(app_key, apk_path, version_name, version_code, github_token):
    tag      = f"{app_key}-v{version_code}"
    filename = f"{app_key}-{version_name}.apk"
    size_kb  = os.path.getsize(apk_path) // 1024

    # Delete existing release with this exact tag if it exists (idempotent retry)
    try:
        rel = gh_request("GET", f"/repos/{GITHUB_REPO}/releases/tags/{tag}", github_token)
        gh_request("DELETE", f"/repos/{GITHUB_REPO}/releases/{rel['id']}", github_token)
        log(f"[{app_key}] Removed existing release {tag}", "build")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    try:
        gh_request("DELETE", f"/repos/{GITHUB_REPO}/git/refs/tags/{tag}", github_token)
    except urllib.error.HTTPError:
        pass  # tag may not exist yet, that's fine

    # Create new release
    payload = json.dumps({
        "tag_name":   tag,
        "name":       f"{app_key} {version_name}",
        "body":       f"Build {version_code}",
        "draft":      False,
        "prerelease": False,
    }).encode()
    rel = gh_request("POST", f"/repos/{GITHUB_REPO}/releases", github_token, data=payload,
                     extra_headers={"Content-Type": "application/json"})
    release_id = rel["id"]
    log(f"[{app_key}] Created release {tag}", "build")

    # Upload APK asset
    log(f"[{app_key}] Uploading {filename}  ({size_kb} KB)  to GitHub…", "up")
    upload_url = f"{GITHUB_UPLOAD}/repos/{GITHUB_REPO}/releases/{release_id}/assets?name={filename}"
    with open(apk_path, "rb") as f:
        apk_data = f.read()
    asset = gh_request("POST", upload_url, github_token, data=apk_data,
                        extra_headers={"Content-Type": "application/vnd.android.package-archive"})
    asset_url = asset["browser_download_url"]  # public CDN URL — no auth needed (public repo)
    log(f"[{app_key}] GitHub upload ✓  →  asset id {asset['id']}", "ok")
    return asset_url

# ── Upload ─────────────────────────────────────────────────────────────────────

def upload(app_key, upload_key, github_token):
    cfg          = APPS[app_key]
    apk_path     = cfg["apk"]
    bnf          = cfg["build_number_file"]
    version_code = read_build_number(bnf)
    version_name = f"1.0.{version_code}"

    if not os.path.exists(apk_path):
        log(f"[{app_key}] APK not found: {apk_path}", "err")
        return False

    try:
        asset_url = github_upload_apk(app_key, apk_path, version_name, version_code, github_token)
    except Exception as e:
        log(f"[{app_key}] GitHub upload failed: {e}", "err")
        return False

    # Register version + public asset URL with the portal
    log(f"[{app_key}] Registering v{version_name} with portal…", "up")
    url     = f"{WORKER_URL}/upload/{app_key}"
    payload = json.dumps({
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl":      asset_url,
    }).encode()
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("X-Upload-Key",   upload_key)
    req.add_header("X-Version-Code", str(version_code))
    req.add_header("X-Version-Name", version_name)
    req.add_header("Content-Type",   "application/json")
    req.add_header("User-Agent",     "build_upload/1.0")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read())
            if body.get("ok"):
                log(f"[{app_key}] Portal updated ✓  →  v{version_name}", "ok")
                return True
            else:
                log(f"[{app_key}] Portal update failed: {body}", "err")
                return False
    except urllib.error.HTTPError as e:
        log(f"[{app_key}] HTTP {e.code}: {e.read().decode()}", "err")
        return False
    except Exception as e:
        log(f"[{app_key}] Upload error: {e}", "err")
        return False

# ── Main ───────────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    arg = sys.argv[1].lower()
    keys = list(APPS.keys()) if arg == "all" else [arg]

    for k in keys:
        if k not in APPS:
            log(f"Unknown app '{k}'. Valid: {', '.join(APPS)} or 'all'", "err")
            sys.exit(1)

    upload_key    = os.environ.get("UPLOAD_KEY")    or input("Upload key: ").strip()
    github_token  = os.environ.get("GITHUB_TOKEN")  or input("GitHub token: ").strip()
    if not upload_key:
        log("UPLOAD_KEY required", "err")
        sys.exit(1)
    if not github_token:
        log("GITHUB_TOKEN required", "err")
        sys.exit(1)

    failed = []
    for k in keys:
        if build(k) and upload(k, upload_key, github_token):
            pass
        else:
            failed.append(k)

    print()
    if failed:
        log(f"Failed: {', '.join(failed)}", "err")
        sys.exit(1)
    else:
        log("All done", "ok")

if __name__ == "__main__":
    main()
