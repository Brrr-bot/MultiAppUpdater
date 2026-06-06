package com.example.aiteachingapp

import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiteachingapp.ui.MenuScreen
import com.example.aiteachingapp.ui.MenuViewModel
import com.example.aiteachingapp.ui.TutorialScreen
import com.example.aiteachingapp.ui.TutorialViewModel
import com.example.aiteachingapp.ui.theme.AITeachingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Factory that passes a tutorialId string into TutorialViewModel. */
class TutorialViewModelFactory(
    private val application: Application,
    private val tutorialId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TutorialViewModel(application, tutorialId) as T
}

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private var updateInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createUpdateChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001)
        setContent {
            AITeachingTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) on ${android.os.Build.MODEL}")
        checkForUpdate()
    }

    // ── OTA update ────────────────────────────────────────────────────────────

    private fun createUpdateChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(UPDATE_CH, "App Updates", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun checkForUpdate() {
        if (updateInProgress) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/version/aiteaching")
                    .build()).execute()
                if (!resp.isSuccessful) return@launch
                val json = org.json.JSONObject(resp.body?.string() ?: return@launch)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    downloadAndNotify(serverCode, apkUrl)
                } else {
                    // Already up to date — clear any stale "update ready" notification.
                    NotificationManagerCompat.from(this@MainActivity).cancel(UPDATE_NOTIF_ID)
                }
            } catch (_: Exception) {}
        }
    }

    private fun downloadAndNotify(buildNum: Int, apkUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder().url(apkUrl).build()).execute()
                if (!resp.isSuccessful) { updateInProgress = false; return@launch }
                val bytes = resp.body?.bytes() ?: run { updateInProgress = false; return@launch }
                val apkFile = File(cacheDir, "update.apk")
                apkFile.writeBytes(bytes)
                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", apkFile)
                else Uri.fromFile(apkFile)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                packageManager.queryIntentActivities(installIntent, 0)
                    .map { it.activityInfo.packageName }
                    .firstOrNull { it.contains("packageinstaller", ignoreCase = true) }
                    ?.let { installIntent.setPackage(it) }
                val pending = PendingIntent.getActivity(
                    this@MainActivity, UPDATE_NOTIF_ID, installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                NotificationManagerCompat.from(this@MainActivity).notify(UPDATE_NOTIF_ID,
                    NotificationCompat.Builder(this@MainActivity, UPDATE_CH)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("AI Teaching update ready")
                        .setContentText("Build $buildNum downloaded — tap to install")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) { updateInProgress = false }
        }
    }

    companion object {
        private const val UPDATE_CH       = "app_update"
        private const val UPDATE_NOTIF_ID = 9001
    }

    // ── Portal logging ────────────────────────────────────────────────────────

    fun sendLog(msg: String, level: String = "INFO") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("app", "aiteaching"); put("level", level); put("msg", msg)
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/log")
                    .post(body).build()).execute().close()
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun AppNavigation() {
    // null = menu screen; non-null = tutorial screen with (tutorialId, startStepIndex)
    var openProject by remember { mutableStateOf<Pair<String, Int>?>(null) }

    if (openProject == null) {
        val menuVm: MenuViewModel = viewModel()
        MenuScreen(
            viewModel = menuVm,
            onOpenProject = { tutorialId, stepIndex ->
                openProject = tutorialId to stepIndex
            }
        )
    } else {
        val (tutorialId, stepIndex) = openProject!!
        val app = LocalContext.current.applicationContext as Application
        val tutorialVm: TutorialViewModel = viewModel(
            key = tutorialId,
            factory = TutorialViewModelFactory(app, tutorialId)
        )

        // Jump to the saved step once on first open
        LaunchedEffect(openProject) {
            tutorialVm.startAt(stepIndex)
        }

        TutorialScreen(
            viewModel = tutorialVm,
            onBack = { openProject = null }
        )
    }
}
