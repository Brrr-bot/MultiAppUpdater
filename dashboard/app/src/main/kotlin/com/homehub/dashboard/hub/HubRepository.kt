package com.homehub.dashboard.hub

import com.google.gson.Gson
import com.homehub.dashboard.data.AppSettingsRepository
import com.homehub.dashboard.util.RemoteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HubRepository(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val settingsRepository: AppSettingsRepository
) {
    suspend fun fetchDashboard(): HubDashboardResponse = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        RemoteLogger.i("hub fetch → ${settings.hubHost}:8767/dashboard")
        val ts = System.currentTimeMillis()
        val device = "home-hub-dashboard"
        val request = Request.Builder()
            .url("http://${settings.hubHost}:8767/dashboard")
            .header("X-PhotoSync-Timestamp", ts.toString())
            .header("X-PhotoSync-Device", device)
            .header("X-PhotoSync-HMAC", sign("$ts:$device"))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                RemoteLogger.e("hub fetch failed: HTTP ${response.code}")
                error("Hub status ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            RemoteLogger.i("hub ok, body length=${body.length}")
            gson.fromJson(body, HubDashboardResponse::class.java)
        }
    }

    fun mapToUi(response: HubDashboardResponse): HubUiState {
        val progress = if (response.progressTotal > 0) {
            "${response.progressCurrent}/${response.progressTotal}  ${response.currentFile}"
        } else {
            "Idle"
        }
        val compression = if (response.compressionTotal > 0) {
            "${response.compressionCurrent}/${response.compressionTotal}"
        } else {
            "Idle"
        }
        val logs = response.recentLogs
            .map(String::trim)
            .filter(String::isNotEmpty)
        return HubUiState(
            connected = true,
            statusLine = "Phone connected",
            lastUpdated = formatAge(response.updatedAt),
            transferLine = progress,
            compressionLine = compression,
            logs = logs.ifEmpty { listOf(response.lastSyncSummary.ifBlank { "No recent activity" }) }
        )
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SHARED_SECRET.toByteArray(), "HmacSHA256"))
        return android.util.Base64.encodeToString(
            mac.doFinal(payload.toByteArray()),
            android.util.Base64.NO_WRAP
        )
    }

    private fun formatAge(timestamp: Long): String {
        if (timestamp <= 0L) return "Updated never"
        val deltaSeconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        val age = when {
            deltaSeconds < 60 -> "${deltaSeconds}s ago"
            deltaSeconds < 3600 -> "${deltaSeconds / 60}m ago"
            else -> "${deltaSeconds / 3600}h ago"
        }
        return "Updated $age"
    }

    companion object {
        const val SHARED_SECRET = "PhotoSync_ChangeMe_32CharSecretKey"
    }
}
