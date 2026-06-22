package com.mcubi.timesheet.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mcubi.timesheet.BuildConfig
import com.mcubi.timesheet.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocationForegroundService : Service() {

    private lateinit var tracker: LocationTracker
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var isTeaching = false
    @Volatile private var teachingSchool = ""

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        tracker = LocationTracker(
            context            = this,
            onLog              = { msg -> log(msg) },
            onTeachingDetected = { school, arrivedAt, mins ->
                isTeaching = true
                teachingSchool = school.name
                refreshNotification()
                DailySchoolCheckReceiver.fireTeachingNotification(this, school, arrivedAt, mins)
            },
            onTeachingEnded    = { _, _ ->
                isTeaching = false
                teachingSchool = ""
                refreshNotification()
            }
        )
        tracker.start()
        scheduler.scheduleAtFixedRate({
            try { tracker.logAndSend() } catch (_: Exception) {}
        }, 0, 60, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scheduler.shutdownNow()
        tracker.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Logging ──────────────────────────────────────────────────────────────────

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        addLog("$time  $message")
        if (isTeaching) refreshNotification()
    }

    private fun refreshNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {}
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Tracks location for school detection" }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)

        return if (isTeaching) {
            val lines  = getRecentLogs().takeLast(LOG_LINES_IN_NOTIF)
            val latest = lines.lastOrNull() ?: ""
            builder
                .setContentTitle("🏫 Teaching: $teachingSchool")
                .setContentText(latest)
                .setColor(0xFF22d3ee.toInt())
                .setColorized(true)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("🏫 $teachingSchool")
                        .bigText(lines.joinToString("\n"))
                )
                .build()
        } else {
            builder
                .setContentTitle("Timesheet  v${BuildConfig.VERSION_NAME}")
                .setColor(0xFF070d18.toInt())
                .build()
        }
    }

    companion object {
        private const val NOTIF_ID           = 1001
        private const val CHANNEL_ID         = "location_tracking"
        private const val LOG_LINES_IN_NOTIF = 6

        private val recentLogs = ArrayDeque<String>(100)

        fun getRecentLogs(): List<String> = synchronized(recentLogs) { recentLogs.toList() }

        private fun addLog(line: String) = synchronized(recentLogs) {
            if (recentLogs.size >= 100) recentLogs.removeFirst()
            recentLogs.addLast(line)
        }

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
