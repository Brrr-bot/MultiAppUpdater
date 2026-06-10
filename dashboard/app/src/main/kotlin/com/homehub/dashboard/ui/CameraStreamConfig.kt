package com.homehub.dashboard.ui

import android.net.Uri
import androidx.core.net.toUri
import com.homehub.dashboard.BuildConfig

object CameraStreamConfig {
    val streamPaths: List<String> = listOf("stream2", "stream1")

    fun isConfigured(): Boolean =
        BuildConfig.CAMERA_HOST.isNotBlank() &&
            BuildConfig.CAMERA_USER.isNotBlank() &&
            BuildConfig.CAMERA_PASSWORD.isNotBlank()

    fun buildUri(streamPath: String): Uri {
        val encodedUser = Uri.encode(BuildConfig.CAMERA_USER.trim())
        val encodedPassword = Uri.encode(BuildConfig.CAMERA_PASSWORD.trim())
        val host = BuildConfig.CAMERA_HOST.trim()
        return "rtsp://$encodedUser:$encodedPassword@$host:554/$streamPath".toUri()
    }
}
