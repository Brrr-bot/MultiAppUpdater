// ─── App Update Portal ────────────────────────────────────────────────────────
// Cloudflare Worker + Durable Object
// • APK files stored on GitHub Releases (private repo), proxied here via GITHUB_TOKEN
// • Tracks version metadata + device logs in DO SQLite
// • Serves a dark web portal with live per-app log streams (polled every 2s)
// • Upload key protected endpoint registers new versions after GitHub upload

const GITHUB_REPO = "Mikeyctrl/MultiAppUpdater";

const APPS = {
  hub:          { name: "PhotoSync Hub",    pkg: "com.photosync.hub",          color: "#00f5ff" },
  client:       { name: "PhotoSync Client", pkg: "com.photosync.client",       color: "#00cfa8" },
  dashboard:    { name: "Dashboard",        pkg: "com.homehub.dashboard",       color: "#4dd9ff" },
  finance:      { name: "Finance",          pkg: "com.mcubi.finances",          color: "#ffb347" },
  timesheet:    { name: "Timesheet",        pkg: "com.mcubi.timesheet",         color: "#b47fff" },
  aiteaching:   { name: "AI Teaching App",  pkg: "com.example.aiteachingapp",   color: "#7fff7f" },
};

const cors = {
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Upload-Key",
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });

    if (url.pathname.startsWith("/api/")) {
      const id  = env.UPDATE.idFromName("main");
      const obj = env.UPDATE.get(id);
      return obj.fetch(request.clone(), { env });
    }

    // APK download — proxy from public GitHub release (repo is public, no auth needed)
    if (url.pathname.startsWith("/apk/")) {
      const app = url.pathname.slice(5).replace(/\.apk$/, "");
      if (!APPS[app]) return new Response("Not found", { status: 404 });
      const doId    = env.UPDATE.idFromName("main");
      const doObj   = env.UPDATE.get(doId);
      const metaRes = await doObj.fetch(new Request(`https://internal/api/version/${app}`));
      const meta    = await metaRes.json();
      if (!meta.rawApkUrl) return new Response("No APK uploaded yet", { status: 404 });
      // Fetch the public GitHub release asset and stream it to the device
      const ghResp = await fetch(meta.rawApkUrl, { redirect: "follow" });
      if (!ghResp.ok) return new Response("GitHub fetch failed: " + ghResp.status, { status: 502 });
      return new Response(ghResp.body, {
        headers: {
          "Content-Type": "application/vnd.android.package-archive",
          "Content-Disposition": `attachment; filename="${app}.apk"`,
          "Content-Length": ghResp.headers.get("Content-Length") || "",
        },
      });
    }

    // APK upload — JSON metadata with the public browser_download_url
    if (url.pathname.startsWith("/upload/") && request.method === "POST") {
      if (request.headers.get("X-Upload-Key") !== env.UPLOAD_KEY) {
        return new Response("Forbidden", { status: 403 });
      }
      const app = url.pathname.slice(8).replace(/\.apk$/, "");
      if (!APPS[app]) return new Response("Unknown app", { status: 400 });

      const body        = await request.json();
      const versionCode = parseInt(body.versionCode || request.headers.get("X-Version-Code") || "0");
      const versionName = body.versionName || request.headers.get("X-Version-Name") || "";
      const apkUrl      = body.apkUrl || "";

      const id  = env.UPDATE.idFromName("main");
      const obj = env.UPDATE.get(id);
      const inner = new Request(`https://internal/api/version/${app}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ versionCode, versionName, apkUrl }),
      });
      await obj.fetch(inner);
      return new Response(JSON.stringify({ ok: true }), {
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    return new Response(PORTAL_HTML, {
      headers: { "Content-Type": "text/html; charset=utf-8" },
    });
  },
};

// ─── Durable Object ───────────────────────────────────────────────────────────

export class UpdateDO {
  constructor(state) {
    this.state = state;
    this.sql   = state.storage.sql;

    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS versions (
        app          TEXT    PRIMARY KEY,
        version_code INTEGER DEFAULT 0,
        version_name TEXT    DEFAULT '1.0.0',
        apk_url      TEXT    DEFAULT '',
        updated_at   INTEGER DEFAULT 0
      )
    `);
    // Add apk_url column if migrating from older schema
    try { this.sql.exec("ALTER TABLE versions ADD COLUMN apk_url TEXT DEFAULT ''"); } catch (_) {}
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS logs (
        id    INTEGER PRIMARY KEY AUTOINCREMENT,
        app   TEXT    NOT NULL,
        level TEXT    NOT NULL DEFAULT 'INFO',
        msg   TEXT    NOT NULL,
        ts    INTEGER NOT NULL
      )
    `);

    for (const app of Object.keys(APPS)) {
      this.sql.exec(
        `INSERT OR IGNORE INTO versions (app, version_code, version_name, updated_at) VALUES (?, 0, '1.0.0', 0)`,
        app
      );
    }
  }

  async fetch(request) {
    const url    = new URL(request.url);
    const method = request.method;
    const path   = url.pathname;

    // GET /api/status — all app versions
    if (path === "/api/status" && method === "GET") {
      const rows = [...this.sql.exec("SELECT * FROM versions ORDER BY app").toArray()];
      const out  = {};
      for (const r of rows) out[r.app] = r;
      return json(out);
    }

    // POST /api/version/:app — set version (called after upload)
    if (path.startsWith("/api/version/") && method === "POST") {
      const app  = path.slice(13);
      if (!APPS[app]) return new Response("Unknown app", { status: 400 });
      const { versionCode, versionName, apkUrl = "" } = await request.json();
      this.sql.exec(
        `INSERT INTO versions (app, version_code, version_name, apk_url, updated_at)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT(app) DO UPDATE SET version_code=excluded.version_code,
           version_name=excluded.version_name, apk_url=excluded.apk_url,
           updated_at=excluded.updated_at`,
        app, versionCode, versionName, apkUrl, Date.now()
      );
      return json({ ok: true });
    }

    // GET /api/version/:app — device polls this to check for update
    if (path.startsWith("/api/version/") && method === "GET") {
      const app = path.slice(13);
      if (!APPS[app]) return new Response("Unknown app", { status: 404 });
      const rows = [...this.sql.exec("SELECT * FROM versions WHERE app=?", app).toArray()];
      if (!rows.length) return new Response("Not found", { status: 404 });
      const r = rows[0];
      const workerUrl = new URL(request.url).origin;
      return json({
        versionCode: r.version_code,
        versionName: r.version_name,
        apkUrl: r.apk_url ? `${workerUrl}/apk/${app}.apk` : null,
        rawApkUrl: r.apk_url || null,
        updatedAt: r.updated_at,
      });
    }

    // POST /api/log — device posts a log line
    if (path === "/api/log" && method === "POST") {
      const { app, level = "INFO", msg } = await request.json();
      if (!app || !msg) return new Response("Bad request", { status: 400 });
      this.sql.exec(
        "INSERT INTO logs (app, level, msg, ts) VALUES (?, ?, ?, ?)",
        app, level.toUpperCase(), msg, Date.now()
      );
      // Keep last 500 logs per app
      this.sql.exec(
        `DELETE FROM logs WHERE app=? AND id NOT IN
         (SELECT id FROM logs WHERE app=? ORDER BY id DESC LIMIT 500)`,
        app, app
      );
      return json({ ok: true });
    }

    // GET /api/logs/:app?since=<ts>&limit=50
    if (path.startsWith("/api/logs/") && method === "GET") {
      const app   = path.slice(10);
      const since = parseInt(url.searchParams.get("since") || "0");
      const limit = Math.min(parseInt(url.searchParams.get("limit") || "50"), 200);
      const rows  = [...this.sql.exec(
        "SELECT id, app, level, msg, ts FROM logs WHERE app=? AND ts>? ORDER BY ts DESC LIMIT ?",
        app, since, limit
      ).toArray()].reverse();
      return json(rows);
    }

    return new Response("Not found", { status: 404, headers: cors });
  }
}

function json(data) {
  return new Response(JSON.stringify(data, null, 2), {
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

// ─── Portal HTML ──────────────────────────────────────────────────────────────

const PORTAL_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>App Update Portal</title>
<style>
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --bg:     #050510;
    --card:   #0a0a1e;
    --border: #1a1a3a;
    --text:   #ffffff;
    --dim:    #7bbccc;
    --red:    #ff3355;
    --green:  #00cfa8;
    --purple: #00897b;
  }

  body {
    background: var(--bg);
    color: var(--text);
    font-family: 'Segoe UI', system-ui, sans-serif;
    min-height: 100vh;
  }

  header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 20px;
    border-bottom: 1px solid var(--border);
  }
  header h1 {
    font-family: Consolas, monospace;
    font-size: 13px;
    font-weight: bold;
    letter-spacing: 2px;
    color: #00f5ff;
  }
  .srv-dot { font-size: 12px; color: var(--dim); margin-left: auto; }
  .srv-dot.ok { color: var(--green); }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 14px;
    padding: 16px;
  }

  .card {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 6px;
    display: flex;
    flex-direction: column;
  }
  .card-head {
    padding: 14px 16px 10px;
    border-bottom: 1px solid var(--border);
  }
  .card-title {
    font-family: Consolas, monospace;
    font-size: 11px;
    font-weight: bold;
    letter-spacing: 1px;
    margin-bottom: 10px;
  }
  .meta-row {
    display: flex;
    gap: 6px;
    align-items: baseline;
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--dim);
  }
  .meta-row span { color: var(--text); font-family: Consolas, monospace; }
  .meta-row .none { color: #444455; }

  .upload-area {
    margin: 10px 0 0;
    position: relative;
  }
  .upload-btn {
    width: 100%;
    padding: 8px 12px;
    background: #0d0d20;
    border: 1px dashed var(--border);
    border-radius: 4px;
    color: var(--dim);
    font-size: 11px;
    font-family: Consolas, monospace;
    cursor: pointer;
    text-align: center;
    transition: border-color .15s, color .15s;
  }
  .upload-btn:hover { border-color: var(--accent, #00f5ff); color: var(--accent, #00f5ff); }
  .upload-btn.uploading { color: var(--purple); border-color: var(--purple); cursor: default; }
  .upload-btn.ok  { color: var(--green); border-color: var(--green); }
  .upload-btn.err { color: var(--red);   border-color: var(--red); }
  input[type=file] { display: none; }

  .progress {
    height: 2px;
    background: var(--border);
    border-radius: 1px;
    margin-top: 6px;
    overflow: hidden;
  }
  .progress-bar {
    height: 100%;
    width: 0;
    background: var(--accent, #00f5ff);
    transition: width .2s;
  }
  .progress-bar.indeterminate {
    width: 30%;
    animation: slide 1s linear infinite;
  }
  @keyframes slide {
    0%   { transform: translateX(-100%); }
    100% { transform: translateX(400%); }
  }

  .log-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 10px 12px;
  }
  .log-title {
    font-family: Consolas, monospace;
    font-size: 9px;
    font-weight: bold;
    letter-spacing: 1px;
    margin-bottom: 6px;
  }
  .log-box {
    flex: 1;
    min-height: 130px;
    max-height: 180px;
    overflow-y: auto;
    font-family: Consolas, monospace;
    font-size: 10px;
    line-height: 1.5;
    color: var(--dim);
  }
  .log-box::-webkit-scrollbar { width: 4px; }
  .log-box::-webkit-scrollbar-track { background: transparent; }
  .log-box::-webkit-scrollbar-thumb { background: var(--border); border-radius: 2px; }
  .log-line { display: flex; gap: 8px; }
  .log-ts  { color: #333344; flex-shrink: 0; }
  .log-msg.err  { color: var(--red); }
  .log-msg.warn { color: #ffb347; }
  .log-empty { color: #333344; font-style: italic; }

  footer {
    text-align: center;
    padding: 16px;
    font-size: 10px;
    color: #333344;
    font-family: Consolas, monospace;
    border-top: 1px solid var(--border);
  }
</style>
</head>
<body>

<header>
  <h1>APP UPDATE PORTAL</h1>
  <span class="srv-dot" id="dot">● loading…</span>
</header>

<div class="grid" id="grid"></div>

<footer id="footer">last refresh: —</footer>

<script>
const APPS = {
  hub:       { name: "PHOTOSYNC HUB",    color: "#00f5ff" },
  client:    { name: "PHOTOSYNC CLIENT", color: "#00cfa8" },
  dashboard: { name: "DASHBOARD",        color: "#4dd9ff" },
  finance:   { name: "FINANCE",          color: "#ffb347" },
  timesheet: { name: "TIMESHEET",        color: "#b47fff" },
};

// log cursor per app (last ts seen)
const logCursors = {};
const logBoxes   = {};
const verEls     = {};
const ageEls     = {};

function timeAgo(ts) {
  if (!ts) return "never";
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60)  return s + "s ago";
  if (s < 3600) return Math.floor(s/60) + "m ago";
  if (s < 86400) return Math.floor(s/3600) + "h ago";
  return Math.floor(s/86400) + "d ago";
}

function fmtTs(ts) {
  return new Date(ts).toLocaleTimeString([], {hour:"2-digit",minute:"2-digit",second:"2-digit"});
}

function buildGrid() {
  const grid = document.getElementById("grid");
  for (const [key, meta] of Object.entries(APPS)) {
    const card = document.createElement("div");
    card.className = "card";
    card.style.setProperty("--accent", meta.color);
    card.innerHTML = \`
      <div class="card-head">
        <div class="card-title" style="color:\${meta.color}">\${meta.name}</div>
        <div class="meta-row">Version: <span id="ver-\${key}" class="none">—</span></div>
        <div class="meta-row">Updated: <span id="age-\${key}" class="none">—</span></div>
        <div class="upload-area">
          <label>
            <input type="file" accept=".apk" id="file-\${key}" onchange="handleUpload('\${key}', this)">
            <div class="upload-btn" id="upbtn-\${key}" style="--accent:\${meta.color}"
                 onclick="document.getElementById('file-\${key}').click()">
              ⬆  Upload APK
            </div>
          </label>
          <div class="progress"><div class="progress-bar" id="prog-\${key}"></div></div>
        </div>
      </div>
      <div class="log-panel">
        <div class="log-title" style="color:\${meta.color}">\${key.toUpperCase()} LOG</div>
        <div class="log-box" id="log-\${key}">
          <div class="log-empty">no logs yet</div>
        </div>
      </div>
    \`;
    grid.appendChild(card);
    logBoxes[key] = document.getElementById(\`log-\${key}\`);
    verEls[key]   = document.getElementById(\`ver-\${key}\`);
    ageEls[key]   = document.getElementById(\`age-\${key}\`);
    logCursors[key] = 0;
  }
}

async function handleUpload(app, input) {
  const file = input.files[0];
  if (!file) return;
  const btn  = document.getElementById(\`upbtn-\${app}\`);
  const prog = document.getElementById(\`prog-\${app}\`);

  // Parse version from filename like "finance-1.0.5-debug.apk" or just use 0/unknown
  const nameMatch = file.name.match(/(\\d+\\.\\d+\\.\\d+)/);
  const versionName = nameMatch ? nameMatch[1] : "1.0.0";

  btn.className  = "upload-btn uploading";
  btn.textContent = "⏳  Uploading…";
  prog.className  = "progress-bar indeterminate";

  try {
    const resp = await fetch(\`/upload/\${app}\`, {
      method: "POST",
      headers: {
        "X-Upload-Key":   prompt("Upload key:") || "",
        "X-Version-Name": versionName,
        "X-Version-Code": String(Date.now()),
      },
      body: file,
    });
    if (!resp.ok) throw new Error(await resp.text());
    btn.className  = "upload-btn ok";
    btn.textContent = "✓  Uploaded — " + file.name;
    prog.className  = "progress-bar";
    prog.style.width = "100%";
    setTimeout(() => {
      btn.className  = "upload-btn";
      btn.textContent = "⬆  Upload APK";
      prog.style.width = "0";
    }, 3000);
    appendLog(app, "INFO", "APK uploaded from portal: " + file.name);
  } catch (e) {
    btn.className  = "upload-btn err";
    btn.textContent = "✗  " + e.message;
    prog.className  = "progress-bar";
    setTimeout(() => {
      btn.className  = "upload-btn";
      btn.textContent = "⬆  Upload APK";
    }, 4000);
  }
  input.value = "";
}

function appendLog(app, level, msg) {
  const box = logBoxes[app];
  const empty = box.querySelector(".log-empty");
  if (empty) empty.remove();

  const now  = fmtTs(Date.now());
  const line = document.createElement("div");
  line.className = "log-line";
  const cls = level === "ERROR" ? "err" : level === "WARN" ? "warn" : "";
  line.innerHTML = \`<span class="log-ts">\${now}</span><span class="log-msg \${cls}">\${escHtml(msg)}</span>\`;
  box.appendChild(line);

  // Keep at most 200 lines
  while (box.children.length > 200) box.removeChild(box.firstChild);
  box.scrollTop = box.scrollHeight;
}

function escHtml(s) {
  return s.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");
}

async function refreshStatus() {
  try {
    const r    = await fetch("/api/status");
    const data = await r.json();
    const dot  = document.getElementById("dot");
    dot.textContent = "● online";
    dot.className   = "srv-dot ok";

    for (const [app, info] of Object.entries(data)) {
      if (!verEls[app]) continue;
      verEls[app].textContent = info.version_code
        ? \`v\${info.version_name}  (build \${info.version_code})\`
        : "not uploaded";
      verEls[app].className = info.version_code ? "" : "none";
      ageEls[app].textContent = info.updated_at ? timeAgo(info.updated_at) : "never";
      ageEls[app].className   = info.updated_at ? "" : "none";
    }
  } catch {
    const dot = document.getElementById("dot");
    dot.textContent = "● offline";
    dot.className   = "srv-dot";
  }
  document.getElementById("footer").textContent =
    "last refresh: " + new Date().toLocaleTimeString();
}

async function pollLogs() {
  for (const app of Object.keys(APPS)) {
    try {
      const since = logCursors[app];
      const r = await fetch(\`/api/logs/\${app}?since=\${since}&limit=50\`);
      const rows = await r.json();
      if (Array.isArray(rows) && rows.length) {
        for (const row of rows) {
          appendLog(app, row.level, row.msg);
          if (row.ts > logCursors[app]) logCursors[app] = row.ts;
        }
      }
    } catch { /* ignore */ }
  }
}

buildGrid();
refreshStatus();
pollLogs();
setInterval(refreshStatus, 10000);
setInterval(pollLogs, 2000);
</script>
</body>
</html>`;
