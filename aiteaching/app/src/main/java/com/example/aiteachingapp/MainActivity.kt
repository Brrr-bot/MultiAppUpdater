package com.example.aiteachingapp

import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        setContent {
            AITeachingTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        checkForUpdate()
    }

    // ── OTA update ────────────────────────────────────────────────────────────

    private fun checkForUpdate() {
        if (updateInProgress) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(
                    Request.Builder()
                        .url("https://app-updates.mcubittbuilders.workers.dev/api/version/aiteaching")
                        .build()
                ).execute()
                if (!resp.isSuccessful) return@launch
                val body = resp.body?.string() ?: return@launch
                val json = org.json.JSONObject(body)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    withContext(Dispatchers.Main) { promptInstall(serverCode, apkUrl) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun promptInstall(serverCode: Int, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Build $serverCode is available. Install now?")
            .setPositiveButton("INSTALL") { _, _ -> downloadAndInstall(apkUrl) }
            .setNegativeButton("LATER") { _, _ -> updateInProgress = false }
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Enable install from unknown sources for this app in Settings", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder().url(apkUrl).build()).execute()
                if (!resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Download failed: ${resp.code}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val bytes = resp.body?.bytes() ?: return@launch
                val pi = packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(
                        android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val sessionId = pi.createSession(params)
                val session = pi.openSession(sessionId)
                session.openWrite("apk", 0, bytes.size.toLong()).use { out ->
                    out.write(bytes)
                    session.fsync(out)
                }
                val intent = Intent(this@MainActivity, InstallReceiver::class.java)
                val pending = android.app.PendingIntent.getBroadcast(
                    this@MainActivity, sessionId, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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
