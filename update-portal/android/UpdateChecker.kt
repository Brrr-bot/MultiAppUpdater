package YOUR_PACKAGE_HERE   // ← change to match the app

/**
 * Drop-in OTA update checker.
 *
 * Usage — call from MainActivity.onCreate() once:
 *
 *   UpdateChecker(this, "finance").start()
 *
 * The checker runs in a background thread, downloads the APK to the app's
 * cache dir, and installs it silently on Android 12+ (API 31) or shows a
 * system install dialog on older devices.
 *
 * Also reports device log lines to the portal (appears in the live log panel).
 *
 * Required in AndroidManifest.xml inside <application>:
 *   <provider
 *     android:name="androidx.core.content.FileProvider"
 *     android:authorities="${applicationId}.provider"
 *     android:exported="false"
 *     android:grantUriPermissions="true">
 *     <meta-data
 *       android:name="android.support.FILE_PROVIDER_PATHS"
 *       android:resource="@xml/provider_paths" />
 *   </provider>
 *
 * Required permissions in AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.INTERNET" />
 *   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
 *
 * Required res/xml/provider_paths.xml:
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <paths>
 *     <cache-path name="apk" path="." />
 *   </paths>
 */

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val WORKER_URL  = "https://app-updates.mcubittbuilders.workers.dev"
private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
private const val TAG = "UpdateChecker"

class UpdateChecker(
    private val context: Context,
    private val appKey: String,   // "finance" | "timesheet" | "hub" | "client" | "dashboard"
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var running  = false

    fun start() {
        if (running) return
        running = true
        executor.execute { loop() }
    }

    fun stop() { running = false }

    // ── Main loop ──────────────────────────────────────────────────────────────

    private fun loop() {
        remoteLog("INFO", "UpdateChecker started")
        while (running) {
            try {
                checkOnce()
            } catch (e: Exception) {
                Log.w(TAG, "Check failed: $e")
            }
            Thread.sleep(CHECK_INTERVAL_MS)
        }
    }

    private fun checkOnce() {
        val info    = fetchVersionInfo() ?: return
        val remote  = info.getInt("versionCode")
        val current = currentVersionCode()

        if (remote <= current) {
            Log.d(TAG, "Up to date (local=$current remote=$remote)")
            return
        }

        val apkUrl = info.getString("apkUrl")
        val vName  = info.getString("versionName")
        Log.i(TAG, "Update available: v$vName (code $remote) — downloading…")
        remoteLog("INFO", "Update available: v$vName — downloading")

        val apkFile = downloadApk(apkUrl) ?: run {
            remoteLog("ERROR", "APK download failed")
            return
        }

        remoteLog("INFO", "Download complete — installing v$vName")
        installApk(apkFile)
    }

    // ── Version fetch ─────────────────────────────────────────────────────────

    private fun fetchVersionInfo(): JSONObject? {
        val url  = URL("$WORKER_URL/api/version/$appKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        return try {
            conn.connect()
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun currentVersionCode(): Int {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) { 0 }
    }

    // ── APK download ──────────────────────────────────────────────────────────

    private fun downloadApk(apkUrl: String): File? {
        val dest = File(context.cacheDir, "update_$appKey.apk")
        return try {
            val url  = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout    = 120_000
            conn.connect()
            if (conn.responseCode != 200) return null
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Download error: $e")
            null
        }
    }

    // ── Install ───────────────────────────────────────────────────────────────

    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSilent(apkFile)
        } else {
            installLegacy(apkFile)
        }
    }

    /** Silent install via PackageInstaller — Android 12+ only. */
    private fun installSilent(apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = installer.createSession(params)
        val session   = installer.openSession(sessionId)
        try {
            session.openWrite("package", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }

            val intent = Intent(context, context.javaClass).apply {
                action = "UPDATE_INSTALL_RESULT"
            }
            val pi = PendingIntent.getActivity(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
            remoteLog("INFO", "PackageInstaller committed — silent install in progress")
        } catch (e: Exception) {
            session.abandon()
            Log.e(TAG, "Silent install failed: $e")
            remoteLog("ERROR", "Silent install failed: $e — falling back to dialog")
            installLegacy(apkFile)
        }
    }

    /** Fallback for Android < 12: shows system install dialog. */
    private fun installLegacy(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    // ── Remote logging ────────────────────────────────────────────────────────

    fun remoteLog(level: String, msg: String) {
        Log.d(TAG, "[$level] $msg")
        executor.execute {
            try {
                val body   = """{"app":"$appKey","level":"$level","msg":${JSONObject.quote(msg)}}"""
                val url    = URL("$WORKER_URL/api/log")
                val conn   = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput      = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8_000
                conn.readTimeout    = 8_000
                conn.outputStream.bufferedWriter().use { it.write(body) }
                conn.responseCode  // force send
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
}
