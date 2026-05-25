package com.homehub.dashboard.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.homehub.dashboard.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val UPDATE_CHECK_URL = "http://100.107.143.20:9000/version.json"
private const val UPDATE_CHANNEL_ID = "home_hub_update"
private const val UPDATE_NOTIFICATION_ID = 42

data class DashVersionManifest(
    @SerializedName("dashVersionCode") val dashVersionCode: Int = 0,
    @SerializedName("dashVersionName") val dashVersionName: String = "",
    @SerializedName("dashApkUrl")      val dashApkUrl: String = ""
)

class UpdateChecker(private val context: Context) {

    private val gson   = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy {
        context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
    }

    fun checkAndNotify() {
        val manifest = fetchManifest() ?: return
        if (manifest.dashVersionCode <= BuildConfig.VERSION_CODE) {
            // Installed — clear any pending flag so future updates are caught
            prefs.edit().remove(PREF_PENDING_VERSION).apply()
            return
        }
        // Don't repeatedly kick off installs for the same version while one is in progress
        val pendingVersion = prefs.getInt(PREF_PENDING_VERSION, 0)
        if (pendingVersion >= manifest.dashVersionCode) return

        val apkFile = downloadApk(manifest) ?: return
        if (silentInstall(apkFile)) {
            prefs.edit().putInt(PREF_PENDING_VERSION, manifest.dashVersionCode).apply()
        } else {
            postInstallNotification(manifest, apkFile)
        }
    }

    private fun fetchManifest(): DashVersionManifest? {
        return try {
            val request = Request.Builder().url(UPDATE_CHECK_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                gson.fromJson(response.body?.string() ?: return null, DashVersionManifest::class.java)
            }
        } catch (_: Exception) { null }
    }

    private fun downloadApk(manifest: DashVersionManifest): File? {
        if (manifest.dashApkUrl.isBlank()) return null
        return try {
            val request = Request.Builder().url(manifest.dashApkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val outFile = File(context.cacheDir, "update-${manifest.dashVersionCode}.apk")
                outFile.writeBytes(bytes)
                outFile
            }
        } catch (_: Exception) { null }
    }

    private fun silentInstall(apkFile: File): Boolean {
        return try {
            val pi = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).also { p ->
                p.setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = pi.createSession(params)
            pi.openSession(sessionId).use { session ->
                session.openWrite("package", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            true
        } catch (_: Exception) { false }
    }

    private fun postInstallNotification(manifest: DashVersionManifest, apkFile: File) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(UPDATE_CHANNEL_ID, "App Updates",
                    NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val tapIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(UPDATE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setContentTitle("HomeHub update available")
                .setContentText("Version ${manifest.dashVersionName} ready — tap to install")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.homehub.dashboard.INSTALL_STATUS"
        private const val PREF_PENDING_VERSION = "pending_install_version"
    }
}
