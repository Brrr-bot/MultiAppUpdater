package com.homehub.dashboard.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

private val Int.dp get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var app: HomeHubApp
    private lateinit var brightnessController: BrightnessController
    private val handler = Handler(Looper.getMainLooper())
    private var hubJob: Job? = null
    private var financeJob: Job? = null
    private var timesheetJob: Job? = null
    private var clockRunnable: Runnable? = null
    private var updateInProgress = false
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
    }

    override fun onStop() {
        super.onStop()
        hubJob?.cancel()
        financeJob?.cancel()
        timesheetJob?.cancel()
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

    // ── Immersive fullscreen ───────────────────────────────────────────────────

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
                    tvAlarm1.text = "—"
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch all entries + savings in parallel-ish (sequential is fine on IO thread)
                val entriesBody = client.newCall(
                    Request.Builder().url("https://finances.mcubittbuilders.workers.dev/api/entries").build()
                ).execute().body?.string() ?: return@launch
                val savingsBody = client.newCall(
                    Request.Builder().url("https://finances.mcubittbuilders.workers.dev/api/savings").build()
                ).execute().body?.string() ?: return@launch

                val entries  = org.json.JSONArray(entriesBody)
                val savings  = org.json.JSONObject(savingsBody).optLong("total", 0)

                // Find most recent salary date (same logic as Finance app's rebuildSalaryDates)
                val salaryDates = mutableSetOf<String>()
                val dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val zone    = java.time.ZoneId.systemDefault()
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    if (e.optString("direction") == "in" && e.optString("category") == "Salary") {
                        val date = java.time.Instant.ofEpochMilli(e.optLong("ts", 0))
                            .atZone(zone).toLocalDate().format(dateFmt)
                        salaryDates.add(date)
                    }
                }
                val periodStart = salaryDates.maxOrNull()  // most recent salary date
                val periodStartMs = if (periodStart != null)
                    java.time.LocalDate.parse(periodStart).atStartOfDay(zone).toInstant().toEpochMilli()
                else 0L

                // Sum income/expense for the current period
                var income  = 0L
                var expense = 0L
                for (i in 0 until entries.length()) {
                    val e  = entries.getJSONObject(i)
                    val ts = e.optLong("ts", 0)
                    if (ts < periodStartMs) continue
                    val amt = e.optLong("amount", 0)
                    when (e.optString("direction")) {
                        "in"  -> income  += amt
                        "out" -> expense += amt
                    }
                }
                val balance = income - expense

                val periodLabel = if (periodStart != null) {
                    val d = java.time.LocalDate.parse(periodStart)
                    "${d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase()} → NOW"
                } else "ALL TIME"

                withContext(Dispatchers.Main) {
                    binding.tvFinancePeriod.text  = periodLabel
                    binding.tvFinanceIncome.text  = formatVnd(income)
                    binding.tvFinanceExpense.text = formatVnd(expense)
                    binding.tvFinanceBal.text     = formatVnd(balance)
                    binding.tvFinanceBal.setTextColor(
                        if (balance >= 0) 0xFF00E676.toInt() else 0xFFFF5252.toInt())
                    binding.tvFinanceSavings.text = formatVnd(savings)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvFinancePeriod.text = "offline"
                }
            }
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("http://100.107.143.20:9000/timesheet?month=$month")
                    .build()
                val json = org.json.JSONObject(
                    client.newCall(req).execute().body?.string() ?: return@launch)

                val totalHours  = json.optDouble("total_hours", 0.0)
                val sessionsArr = json.optJSONArray("sessions")
                val sessionCount = sessionsArr?.length() ?: 0
                var earned = 0L
                if (sessionsArr != null) {
                    for (i in 0 until sessionsArr.length()) {
                        val s = sessionsArr.getJSONObject(i)
                        earned += (s.optDouble("total_hours", 0.0) * tsSchoolRate(s.optString("school", ""))).toLong()
                    }
                }
                withContext(Dispatchers.Main) {
                    binding.tvTimesheetEarned.text = formatVnd(earned)
                    binding.tvTimesheetHours.text  =
                        "${String.format("%.1f", totalHours)}h  ·  $sessionCount session${if (sessionCount != 1) "s" else ""}"
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvTimesheetEarned.text = "—"
                    binding.tvTimesheetHours.text  = ""
                }
            }
        }
    }

    private fun renderTodaySchedule() {
        val today  = java.time.LocalDate.now()
        val dayKey = today.format(java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        val fullDay = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)).uppercase()
        val slots  = TODAY_SCHEDULE[dayKey] ?: emptyList()

        binding.tvTimesheetTodayLabel.text = fullDay

        val amSlots = slots.filter { it.first < "12:00" }
        val pmSlots = slots.filter { it.first >= "12:00" }

        fun fillColumn(container: LinearLayout, slotList: List<Pair<String, String>>) {
            container.removeAllViews()
            if (slotList.isEmpty()) {
                container.addView(TextView(this).apply {
                    text = "—"
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
        return "${prefix}${nf.format(Math.abs(amount))}₫"
    }

    companion object {
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
        binding.tvHubLogs.text = state.logs.joinToString("\n")
        binding.scrollHubLogs.post { binding.scrollHubLogs.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
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
                val pending = PendingIntent.getActivity(
                    this@MainActivity, UPDATE_NOTIF_ID,
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                NotificationManagerCompat.from(this@MainActivity).notify(UPDATE_NOTIF_ID,
                    NotificationCompat.Builder(this@MainActivity, UPDATE_CH)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Dashboard update ready")
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
