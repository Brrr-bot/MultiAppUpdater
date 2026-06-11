package com.homehub.dashboard.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.snackbar.Snackbar
import com.homehub.dashboard.BuildConfig
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.InstallReceiver
import com.homehub.dashboard.R
import com.homehub.dashboard.brightness.BrightnessController
import com.homehub.dashboard.util.RemoteLogger
import com.homehub.dashboard.data.AlarmEntity
import com.homehub.dashboard.databinding.ActivityMainBinding
import com.homehub.dashboard.hub.HubUiState
import com.homehub.dashboard.service.KeepAliveService
import com.homehub.dashboard.settings.SettingsActivity
import com.homehub.dashboard.weather.DailyForecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private val Int.dp get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

private data class FinanceSnapshot(
    val periodLabel: String,
    val income: Double,
    val expense: Double,
    val balance: Double,
    val savings: Double
)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var app: HomeHubApp
    private lateinit var brightnessController: BrightnessController
    private val handler = Handler(Looper.getMainLooper())
    private var hubJob: Job? = null
    private var financeJob: Job? = null
    private var timesheetJob: Job? = null
    private var clockRunnable: Runnable? = null
    private var cameraPlayer: ExoPlayer? = null
    private var activeCameraStreamPath: String? = null
    private val attemptedCameraStreamPaths = linkedSetOf<String>()
    private var isCameraMuted = true
    private var updateInProgress = false
    private val financePrefs by lazy { getSharedPreferences("finance_card_cache", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as HomeHubApp
        brightnessController = BrightnessController(this, app.settingsRepository)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()
        startForegroundService(Intent(this, KeepAliveService::class.java))

        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnCameraMute.setOnClickListener { toggleCameraMute() }
        binding.cameraPreviewContainer.setOnClickListener { openCameraFullscreen() }
        binding.playerCamera.setOnClickListener { openCameraFullscreen() }
        binding.tvCameraOverlay.setOnClickListener { openCameraFullscreen() }
        updateCameraMuteIcon()
        loadFinanceSnapshot()?.let(::renderFinanceSnapshot)
        loadTimesheetSnapshot()?.let { (earned, _) ->
            binding.tvTimesheetStatus.text = "CACHED"
            binding.tvTimesheetEarned.text = formatVnd(earned)
        }

        bindAlarms()
        startClock()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) on ${android.os.Build.MODEL}")
        createUpdateChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001)
        checkForUpdate()
    }

    override fun onStart() {
        super.onStart()
        brightnessController.start()
        refreshWeather()
        startHubPolling()
        startFinancePolling()
        startTimesheetPolling()
        renderTodaySchedule()
        startCameraFeed()
    }

    override fun onStop() {
        super.onStop()
        hubJob?.cancel()
        financeJob?.cancel()
        timesheetJob?.cancel()
        stopCameraFeed()
        brightnessController.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockRunnable?.let(handler::removeCallbacks)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        brightnessController.onUserInteraction()
    }

    // Immersive fullscreen

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun bindAlarms() {
        lifecycleScope.launch {
            app.alarmRepository.observeAll().collectLatest { alarms ->
                renderDayCards(alarms)
            }
        }
    }

    private fun renderDayCards(alarms: List<AlarmEntity>) {
        val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val grouped = alarms.groupBy { it.weekday }
        binding.dayCardContainer.removeAllViews()
        weekdays.forEachIndexed { index, label ->
            val sorted = grouped[index].orEmpty()
                .sortedWith(compareBy(AlarmEntity::hour, AlarmEntity::minute))
            val card = layoutInflater.inflate(R.layout.item_day_card, binding.dayCardContainer, false)
            card.findViewById<TextView>(R.id.tv_day_name).text = label

            val tvAlarm1 = card.findViewById<TextView>(R.id.tv_alarm_1)
            val tvAlarm2 = card.findViewById<TextView>(R.id.tv_alarm_2)
            val tvCount  = card.findViewById<TextView>(R.id.tv_day_count)

            fun alarmText(a: AlarmEntity) = formatAlarm(a)

            fun alarmColor(a: AlarmEntity) =
                if (a.enabled) 0xFFE0E0E0.toInt() else 0xFFFF4444.toInt()

            fun bindAlarmSlot(tv: TextView, alarm: AlarmEntity?) {
                if (alarm == null) { tv.text = ""; tv.isClickable = false; return }
                tv.text = alarmText(alarm)
                tv.setTextColor(alarmColor(alarm))
                tv.isClickable = true
                tv.isFocusable = true
                tv.setBackgroundResource(android.R.drawable.list_selector_background)
                tv.setOnClickListener {
                    lifecycleScope.launch {
                        val updated = alarm.copy(enabled = !alarm.enabled)
                        app.alarmRepository.update(updated)
                        if (updated.enabled) app.alarmScheduler.schedule(updated)
                        else app.alarmScheduler.cancel(updated.id)
                    }
                }
            }

            when (sorted.size) {
                0 -> {
                    tvAlarm1.text = "+"          // add-alarm affordance, centered in the card
                    tvAlarm1.setTextColor(0xFF3A4A5A.toInt())
                    tvAlarm1.textSize = 30f
                    tvAlarm1.gravity = android.view.Gravity.CENTER
                    tvAlarm1.isClickable = false
                    tvAlarm2.text = ""
                    tvCount.text  = ""
                }
                1 -> {
                    bindAlarmSlot(tvAlarm1, sorted[0])
                    tvAlarm2.text = ""
                    tvCount.text  = ""
                }
                2 -> {
                    bindAlarmSlot(tvAlarm1, sorted[0])
                    bindAlarmSlot(tvAlarm2, sorted[1])
                    tvCount.text  = ""
                }
                else -> {
                    bindAlarmSlot(tvAlarm1, sorted[0])
                    bindAlarmSlot(tvAlarm2, sorted[1])
                    tvCount.text  = "+${sorted.size - 2} more"
                }
            }

            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (index < weekdays.lastIndex) {
                params.marginEnd = resources.getDimensionPixelSize(R.dimen.gap_medium)
            }
            card.layoutParams = params
            card.setOnClickListener {
                startActivity(
                    Intent(this, DayAlarmActivity::class.java)
                        .putExtra(DayAlarmActivity.EXTRA_WEEKDAY, index)
                        .putExtra(DayAlarmActivity.EXTRA_DAY_NAME, label)
                )
            }
            binding.dayCardContainer.addView(card)
        }
    }

    private fun refreshWeather() {
        lifecycleScope.launch {
            val result = runCatching { app.weatherRepository.fetch() }
            val state = result.getOrNull()
            val err = result.exceptionOrNull()
            binding.tvWeatherLocation.text = when {
                err != null -> "Error: ${err.javaClass.simpleName}: ${err.message?.take(60)}"
                state != null -> state.locationName
                else -> "No data"
            }
            binding.weatherRow.removeAllViews()
            RemoteLogger.i("weather cards count=${state?.cards?.size ?: 0}, weatherRow childCount before=${binding.weatherRow.childCount}")
            state?.cards?.forEachIndexed { i, forecast ->
                binding.weatherRow.addView(makeForecastCard(forecast, isLast = i == (state.cards.size - 1)))
            }
            RemoteLogger.i("weather weatherRow childCount after=${binding.weatherRow.childCount}")
        }
    }

    private fun makeForecastCard(forecast: DailyForecast, isLast: Boolean = false): android.view.View {
        val icon = when (forecast.weatherCode) {
            0          -> "\u2600"
            in 1..3    -> "\u26C5"
            in 45..48  -> "\u2248"
            in 51..67  -> "\u2614"
            in 71..77  -> "\u2744"
            in 80..82  -> "\u2614"
            in 95..99  -> "\u26C8"
            else       -> "\u2601"
        }
        val maxStr = "${forecast.tempMaxC.toInt()}\u00B0"
        val minStr = "${forecast.tempMinC.toInt()}\u00B0"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dp, 8.dp, 6.dp, 8.dp)
            setBackgroundResource(R.drawable.neon_card_purple)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = if (isLast) 0 else 3.dp
            }
        }

        val tvDay = TextView(this).apply {
            text = forecast.dayLabel.uppercase()
            setTextColor(0xFF00897B.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Row: icon left, temps stacked right
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
                setPadding(4.dp, 0, 4.dp, 0)
            }
        }

        val tvIcon = TextView(this).apply {
            text = icon
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val temps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6.dp
            }
        }

        val tvMax = TextView(this).apply {
            text = maxStr
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvMin = TextView(this).apply {
            text = minStr
            setTextColor(0xFF7BBCCC.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 2.dp
            }
        }

        temps.addView(tvMax)
        temps.addView(tvMin)
        row.addView(tvIcon)
        row.addView(temps)
        card.addView(tvDay)
        card.addView(row)
        return card
    }

    private fun startHubPolling() {
        hubJob?.cancel()
        hubJob = lifecycleScope.launch {
            while (true) {
                val state = runCatching {
                    app.hubRepository.mapToUi(app.hubRepository.fetchDashboard())
                }.getOrElse {
                    HubUiState()
                }
                renderHub(state)
                delay(5000L)
            }
        }
    }

    private fun startFinancePolling() {
        financeJob?.cancel()
        financeJob = lifecycleScope.launch {
            while (true) {
                fetchFinance()
                delay(60_000L)
            }
        }
    }

    private fun fetchFinance() {
        lifecycleScope.launch {
            try {
                // The finance phone app pushes its current-period snapshot to the hub; we read it
                // back over the local network (same HMAC channel as /dashboard). No cloud involved.
                val body = app.hubRepository.fetchSidecar("finance")
                val o = org.json.JSONObject(body)
                if (!o.has("periodLabel")) throw IllegalStateException("no finance snapshot yet")
                val snapshot = FinanceSnapshot(
                    periodLabel = o.optString("periodLabel", "ALL TIME"),
                    income = o.optDouble("income", 0.0),
                    expense = o.optDouble("expense", 0.0),
                    balance = o.optDouble("balance", o.optDouble("income", 0.0) - o.optDouble("expense", 0.0)),
                    savings = o.optDouble("savings", 0.0)
                )
                saveFinanceSnapshot(snapshot)
                renderFinanceSnapshot(snapshot)
            } catch (e: Exception) {
                val cached = loadFinanceSnapshot()
                if (cached != null) {
                    renderFinanceSnapshot(cached)
                    binding.tvFinancePeriod.text = "CACHED"
                } else {
                    binding.tvFinancePeriod.text = "OFFLINE"
                    binding.tvFinanceIncome.text = "--"
                    binding.tvFinanceExpense.text = "--"
                    binding.tvFinanceBal.text = "--"
                    binding.tvFinanceSavings.text = "--"
                }
                RemoteLogger.e("finance card fetch failed: ${e.message.orEmpty()}")
            }
        }
    }

    private fun renderFinanceSnapshot(snapshot: FinanceSnapshot) {
        binding.tvFinancePeriod.text = snapshot.periodLabel
        binding.tvFinanceIncome.text = formatVnd(snapshot.income)
        binding.tvFinanceExpense.text = formatVnd(snapshot.expense)
        binding.tvFinanceBal.text = formatVnd(snapshot.balance)
        binding.tvFinanceBal.setTextColor(
            if (snapshot.balance >= 0) 0xFF00E676.toInt() else 0xFFFF5252.toInt()
        )
        binding.tvFinanceSavings.text = formatVnd(snapshot.savings)
    }

    private fun saveFinanceSnapshot(snapshot: FinanceSnapshot) {
        financePrefs.edit()
            .putString("periodLabel", snapshot.periodLabel)
            .putFloat("income", snapshot.income.toFloat())
            .putFloat("expense", snapshot.expense.toFloat())
            .putFloat("balance", snapshot.balance.toFloat())
            .putFloat("savings", snapshot.savings.toFloat())
            .apply()
    }

    private fun loadFinanceSnapshot(): FinanceSnapshot? {
        val periodLabel = financePrefs.getString("periodLabel", null) ?: return null
        return FinanceSnapshot(
            periodLabel = periodLabel,
            income = financePrefs.getFloat("income", 0f).toDouble(),
            expense = financePrefs.getFloat("expense", 0f).toDouble(),
            balance = financePrefs.getFloat("balance", 0f).toDouble(),
            savings = financePrefs.getFloat("savings", 0f).toDouble()
        )
    }

    private fun startTimesheetPolling() {
        timesheetJob?.cancel()
        timesheetJob = lifecycleScope.launch {
            while (true) {
                fetchTimesheet()
                delay(120_000L)
            }
        }
    }

    private fun fetchTimesheet() {
        val month = java.time.YearMonth.now().toString()
        lifecycleScope.launch {
            try {
                // The timesheet phone app mirrors its session log to the hub; we parse the lines for
                // the current month here (same computation the old laptop server did). No cloud/laptop.
                val body = app.hubRepository.fetchSidecar("timesheet")
                val arr = org.json.JSONObject(body).optJSONArray("sessions")
                    ?: throw IllegalStateException("no timesheet snapshot yet")
                val pattern = Regex(
                    """\[TIMESHEET\]\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+\((\w+)\)\s+(\d+)\s+period.*?×\s*(\d+)min\s*=\s*(\d+)min"""
                )
                var totalHours = 0.0
                var sessionCount = 0
                var earned = 0L
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i)
                    if (!line.contains(month)) continue
                    val m = pattern.find(line) ?: continue
                    val date = m.groupValues[1]
                    if (!date.startsWith(month)) continue
                    val school = m.groupValues[2].trim()
                    val tMins = m.groupValues[6].toIntOrNull() ?: continue
                    val hours = tMins / 60.0
                    totalHours += hours
                    sessionCount += 1
                    earned += (hours * tsSchoolRate(school)).toLong()
                }
                val hoursLabel =
                    "${String.format("%.1f", totalHours)}h  ·  $sessionCount session${if (sessionCount != 1) "s" else ""}"
                saveTimesheetSnapshot(earned, hoursLabel)
                binding.tvTimesheetStatus.text = "ONLINE"
                binding.tvTimesheetEarned.text = formatVnd(earned)
            } catch (e: Exception) {
                val cached = loadTimesheetSnapshot()
                if (cached != null) {
                    binding.tvTimesheetStatus.text = "CACHED"
                    binding.tvTimesheetEarned.text = formatVnd(cached.first)
                } else {
                    binding.tvTimesheetEarned.text = "--"
                    binding.tvTimesheetStatus.text = "OFFLINE"
                }
                RemoteLogger.e("timesheet card fetch failed: ${e.message.orEmpty()}")
            }
        }
    }

    private val timesheetPrefs by lazy { getSharedPreferences("timesheet_card_cache", MODE_PRIVATE) }

    private fun saveTimesheetSnapshot(earned: Long, hoursLabel: String) {
        timesheetPrefs.edit()
            .putLong("earned", earned)
            .putString("hoursLabel", hoursLabel)
            .apply()
    }

    private fun loadTimesheetSnapshot(): Pair<Long, String>? {
        if (!timesheetPrefs.contains("earned")) return null
        return timesheetPrefs.getLong("earned", 0L) to timesheetPrefs.getString("hoursLabel", "").orEmpty()
    }

    private fun renderTodaySchedule() {
        val today  = java.time.LocalDate.now()
        val dayKey = today.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        val fullDay = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)).uppercase()
        val slots  = TODAY_SCHEDULE[dayKey] ?: emptyList()

        binding.tvTimesheetTodayLabel.text = fullDay
        binding.tvTimesheetHours.text =
            "${slots.size} session${if (slots.size != 1) "s" else ""}"

        val amSlots = slots.filter { it.first < "12:00" }
        val pmSlots = slots.filter { it.first >= "12:00" }

        fun fillColumn(container: LinearLayout, slotList: List<Pair<String, String>>) {
            container.removeAllViews()
            if (slotList.isEmpty()) {
                container.addView(TextView(this).apply {
                    text = "--"
                    setTextColor(0xFF646464.toInt())
                    textSize = 9f
                    typeface = android.graphics.Typeface.MONOSPACE
                })
                return
            }
            for ((time, label) in slotList) {
                container.addView(TextView(this).apply {
                    text = "$time $label"
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 10f
                    typeface = android.graphics.Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = 1.dp }
                })
            }
        }

        fillColumn(binding.llTimesheetAm, amSlots)
        fillColumn(binding.llTimesheetPm, pmSlots)
    }

    private fun startCameraFeed() {
        val host = BuildConfig.CAMERA_HOST.trim()
        val user = BuildConfig.CAMERA_USER.trim()
        val password = BuildConfig.CAMERA_PASSWORD.trim()
        if (host.isEmpty() || user.isEmpty() || password.isEmpty()) {
            binding.tvCameraStatus.text = "camera not configured"
            binding.tvCameraOverlay.visibility = View.VISIBLE
            return
        }
        if (cameraPlayer != null) return

        binding.tvCameraStatus.text = "connecting to $host"
        binding.tvCameraOverlay.visibility = View.VISIBLE
        binding.playerCamera.useController = false
        attemptedCameraStreamPaths.clear()

        val player = ExoPlayer.Builder(this).build().also { exo ->
            exo.playWhenReady = true
            exo.volume = if (isCameraMuted) 0f else 1f
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.tvCameraStatus.text = "live view"
                            binding.tvCameraOverlay.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.tvCameraStatus.text = "live view"
                            binding.tvCameraOverlay.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            binding.tvCameraStatus.text = "stream ended"
                            binding.tvCameraOverlay.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val failedPath = activeCameraStreamPath
                    val retryPath = CameraStreamConfig.streamPaths.firstOrNull { it !in attemptedCameraStreamPaths }
                    if (retryPath != null && failedPath != null) {
                        binding.tvCameraStatus.text = "live view"
                        binding.tvCameraOverlay.visibility = View.VISIBLE
                        RemoteLogger.e("camera rtsp error on $failedPath: ${error.errorCodeName} ${error.message.orEmpty()}")
                        playCameraStream(exo, retryPath)
                        return
                    }
                    binding.tvCameraStatus.text = "camera error: ${error.errorCodeName.lowercase(Locale.US)}"
                    binding.tvCameraOverlay.visibility = View.VISIBLE
                    RemoteLogger.e("camera rtsp error: ${error.errorCodeName} ${error.message.orEmpty()}")
                }
            })
            playCameraStream(exo, CameraStreamConfig.streamPaths.first())
        }
        cameraPlayer = player
        binding.playerCamera.player = player
    }

    private fun stopCameraFeed() {
        binding.playerCamera.player = null
        cameraPlayer?.release()
        cameraPlayer = null
        activeCameraStreamPath = null
        attemptedCameraStreamPaths.clear()
    }

    private fun playCameraStream(player: ExoPlayer, streamPath: String) {
        activeCameraStreamPath = streamPath
        attemptedCameraStreamPaths += streamPath
        binding.tvCameraStatus.text = "live view"
        player.setMediaItem(MediaItem.fromUri(CameraStreamConfig.buildUri(streamPath)))
        player.prepare()
        RemoteLogger.i("camera rtsp start -> ${BuildConfig.CAMERA_HOST.trim()}/$streamPath")
    }

    private fun toggleCameraMute() {
        isCameraMuted = !isCameraMuted
        cameraPlayer?.volume = if (isCameraMuted) 0f else 1f
        updateCameraMuteIcon()
    }

    private fun updateCameraMuteIcon() {
        binding.btnCameraMute.setImageResource(
            if (isCameraMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
        binding.btnCameraMute.contentDescription =
            if (isCameraMuted) "Unmute camera" else "Mute camera"
    }

    private fun openCameraFullscreen() {
        if (!CameraStreamConfig.isConfigured()) return
        startActivity(
            Intent(this, FullscreenCameraActivity::class.java)
                .putExtra(FullscreenCameraActivity.EXTRA_START_MUTED, isCameraMuted)
        )
    }

    private fun tsSchoolRate(name: String): Long {
        val l = name.lowercase()
        return when {
            l.contains("stem")  -> 600_000L
            l.contains("lotus") -> 460_000L
            else                -> 520_000L
        }
    }

    private fun formatVnd(amount: Long): String {
        val nf = java.text.NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
        val prefix = if (amount < 0) "-" else ""
        return "${prefix}${nf.format(Math.abs(amount))}\u20AB"
    }

    private fun formatVnd(amount: Double): String {
        val nf = java.text.NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
        val prefix = if (amount < 0) "-" else ""
        return "${prefix}${nf.format(abs(amount))}\u20AB"
    }

    companion object {
        private const val UPDATE_CH       = "app_update"
        private const val UPDATE_NOTIF_ID = 9001
        // Compact schedule: Pair(time-range, class+school-abbrev)
        // Mirrors the hardcoded SCHEDULE in the Timesheet app
        val TODAY_SCHEDULE: Map<String, List<Pair<String, String>>> = mapOf(
            "Mon" to listOf(
                Pair("13:45", "7/1  TTH"),
                Pair("14:30", "7/1  TTH"),
                Pair("15:45", "7/2  TTH")
            ),
            "Tue" to listOf(
                Pair("07:30", "12A7 TQB"),
                Pair("09:30", "10C9 TQB"),
                Pair("10:30", "10C9 TQB"),
                Pair("13:30", "10C1 TQB"),
                Pair("14:20", "10C1 TQB"),
                Pair("15:20", "12A7 TQB")
            ),
            "Wed" to listOf(
                Pair("07:30", "10C10 TQB"),
                Pair("09:30", "10C5 TQB"),
                Pair("10:30", "10C5 TQB"),
                Pair("12:30", "8.9  AL"),
                Pair("13:15", "8.9  AL"),
                Pair("14:00", "8.1  AL"),
                Pair("15:15", "8.1  AL"),
                Pair("19:30", "STEM")
            ),
            "Thu" to listOf(
                Pair("09:30", "11B11 TQB"),
                Pair("10:30", "11B11 TQB"),
                Pair("13:25", "7.2  LVV"),
                Pair("14:10", "7.4  LVV"),
                Pair("15:15", "7.4  LVV"),
                Pair("16:00", "8.2  LVV")
            ),
            "Fri" to listOf(
                Pair("07:15", "9/6  TTH"),
                Pair("08:00", "9/9  TTH"),
                Pair("09:15", "9/9  TTH"),
                Pair("10:00", "9/3  TTH"),
                Pair("10:45", "9/5  TTH"),
                Pair("19:30", "STEM*")
            ),
            "Sat" to listOf(
                Pair("07:30", "Lotus*"),
                Pair("08:15", "Lotus*"),
                Pair("09:15", "Lotus*"),
                Pair("10:00", "Lotus*"),
                Pair("10:45", "Lotus*"),
                Pair("13:30", "Lotus*"),
                Pair("14:15", "Lotus*"),
                Pair("15:15", "Lotus*"),
                Pair("16:00", "Lotus*"),
                Pair("16:45", "Lotus*")
            ),
            "Sun" to listOf(
                Pair("07:30", "Lotus*"),
                Pair("08:15", "Lotus*"),
                Pair("09:15", "Lotus*"),
                Pair("10:00", "Lotus*"),
                Pair("10:45", "Lotus*"),
                Pair("13:30", "Lotus*"),
                Pair("14:15", "Lotus*")
            )
        )
    }

    private fun renderHub(state: HubUiState) {
        binding.tvHubStatus.text = state.statusLine
        binding.tvHubLastUpdated.text = state.lastUpdated
        binding.tvHubTransfer.text = buildString {
            append(state.transferLine)
            if (state.compressionLine != "Idle") append("  |  compression: ${state.compressionLine}")
        }
        // Colour-code each log line the same way the PhotoSync client/hub apps do (LogStyle).
        val sb = android.text.SpannableStringBuilder()
        state.logs.forEachIndexed { i, line ->
            val start = sb.length
            sb.append(line)
            sb.setSpan(
                android.text.style.ForegroundColorSpan(hubLogColor(line)),
                start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (i < state.logs.lastIndex) sb.append("\n")
        }
        binding.tvHubLogs.text = sb
        binding.scrollHubLogs.post { binding.scrollHubLogs.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    /** Mirrors com.photosync.shared.LogStyle.colorFor so LIVE HUB log lines match the client/hub
     *  apps: errors red, restore amber, video purple, image/WebP cyan, transfer blue,
     *  maintenance yellow, success green, default grey. */
    private fun hubLogColor(line: String): Int {
        val s = line.lowercase(Locale.getDefault())
        return when {
            "✗" in line || "error" in s || "fail" in s || "unreachable" in s ||
                "timed out" in s || "timeout" in s || "unauthorized" in s || "missing" in s -> 0xFFFF4444.toInt()
            "↺" in line || "restore" in s -> 0xFFFFAA00.toInt()
            "▶" in line || "videospace" in s || "videodaterepair" in s ||
                "poster" in s || "transcod" in s -> 0xFFBF00FF.toInt()
            "◇" in line || "webp" in s || "compress" in s -> 0xFF00E5FF.toInt()
            "⬆" in line || "⬇" in line || "uploading" in s || "saved to usb" in s ||
                "synced" in s || "syncing" in s || "handshake" in s || "download" in s -> 0xFF33B5FF.toInt()
            "localfix" in s || "date" in s || "exif" in s || "reorg" in s ||
                "dedup" in s || "cleanup" in s || "repair" in s || "manifest" in s -> 0xFFFFD54F.toInt()
            "✓" in line || "complete" in s || "done" in s || "ready" in s -> 0xFF00FF88.toInt()
            else -> 0xFF888888.toInt()
        }
    }

    private fun startClock() {
        val formatter = DateTimeFormatter.ofPattern("EEE  dd MMM   HH:mm", Locale.getDefault())
        var lastMinute = -1
        clockRunnable = object : Runnable {
            override fun run() {
                val now = LocalDateTime.now()
                binding.tvClock.text = now.format(formatter).uppercase()
                // Re-evaluate dim schedule once per minute (catches the 22:00 crossover)
                val minute = now.minute
                if (minute != lastMinute) {
                    lastMinute = minute
                    brightnessController.tick()
                }
                handler.postDelayed(this, 1000L)
            }
        }.also(handler::post)
    }

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
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/version/dashboard")
                    .build()).execute()
                if (!resp.isSuccessful) return@launch
                val json = org.json.JSONObject(resp.body?.string() ?: return@launch)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl     = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    downloadAndNotify(serverCode, apkUrl)
                } else {
                    // Already up to date; clear any stale "update ready" notification.
                    NotificationManagerCompat.from(this@MainActivity).cancel(UPDATE_NOTIF_ID)
                }
            } catch (_: Exception) { }
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
                        .setContentTitle("Dashboard update ready")
                        .setContentText("Build $buildNum downloaded - tap to install")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) { updateInProgress = false }
        }
    }

    fun sendLog(msg: String, level: String = "INFO") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("app", "dashboard"); put("level", level); put("msg", msg)
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/log")
                    .post(body).build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun formatAlarm(alarm: AlarmEntity): String {
        return String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    }
}

