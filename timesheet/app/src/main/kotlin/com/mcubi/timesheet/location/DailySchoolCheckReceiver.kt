package com.mcubi.timesheet.location

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mcubi.timesheet.PeriodCountActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fires every day at 21:00. Runs SchoolMatcher against today's location CSV and
 * posts one notification per school where the phone was present ≥ 45 min.
 *
 * Yes → opens PeriodCountActivity to record how many periods were taught.
 * No  → dismisses silently.
 */
class DailySchoolCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DAILY_CHECK -> {
                val result = goAsync()
                Thread {
                    try { runDailyCheck(context) }
                    finally { result.finish() }
                }.start()
            }
            ACTION_NO -> handleNo(context, intent)
        }
    }

    private fun runDailyCheck(context: Context) {
        val visits = try {
            SchoolMatcher(context).findAllSchoolVisitsToday()
        } catch (_: Exception) { return }
        if (visits.isEmpty()) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Parse [TIMESHEET] entries for today: store school name + wall-clock time logged.
        // This lets us match a verification against the specific visit window it covers.
        data class VerifiedEntry(val schoolLower: String, val loggedAt: Long)
        val verified: List<VerifiedEntry> = try {
            val logFile  = File(context.filesDir, "timesheet_log.txt")
            val tsFmt    = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val linePat  = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
            val schoolPat = Regex("""\[TIMESHEET\]\s+\d{4}-\d{2}-\d{2}\s+(.+?)\s+\(""")
            if (logFile.exists()) {
                logFile.readLines()
                    .filter { "[TIMESHEET]" in it && today in it }
                    .mapNotNull { line ->
                        val ts     = linePat.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                        val school = schoolPat.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                        val loggedAt = try { tsFmt.parse(ts)?.time ?: return@mapNotNull null }
                                       catch (_: Exception) { return@mapNotNull null }
                        VerifiedEntry(school.trim().lowercase(), loggedAt)
                    }
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        // Only notify for visits that are likely-taught and not already covered by a
        // verification logged during or after that specific visit window.
        val teachSchools = visits.filter { v ->
            v.likelyTaught && verified.none { e ->
                val nameLower = v.school.name.lowercase()
                val nameMatch = e.schoolLower.contains(nameLower) || nameLower.contains(e.schoolLower)
                nameMatch && e.loggedAt >= v.arrivedAt
            }
        }
        if (teachSchools.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, nm)

        teachSchools.forEachIndexed { idx, visit ->
            val school  = visit.school
            val notifId = NOTIF_BASE_ID + idx

            val yesIntent = PendingIntent.getActivity(
                context, notifId * 10 + 1,
                Intent(context, PeriodCountActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(PeriodCountActivity.EXTRA_SCHOOL_NAME, school.name)
                    .putExtra(PeriodCountActivity.EXTRA_SCHOOL_TYPE, school.type)
                    .putExtra(PeriodCountActivity.EXTRA_NOTIF_ID,    notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val noIntent = PendingIntent.getBroadcast(
                context, notifId * 10 + 2,
                Intent(context, DailySchoolCheckReceiver::class.java)
                    .setAction(ACTION_NO)
                    .putExtra(EXTRA_NOTIF_ID, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val fromStr = timeFmt.format(Date(visit.arrivedAt))
            val toStr   = timeFmt.format(Date(visit.departedAt))

            val notif = NotificationCompat.Builder(context, SCHOOL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Did you teach at ${school.name}?")
                .setContentText("$fromStr – $toStr  (~${visit.minutesSpent} min)")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("${school.type}  ·  $fromStr – $toStr  (~${visit.minutesSpent} min)\n\nTap Yes to log periods taught."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setColor(0xFF00CFA8.toInt())
                .addAction(android.R.drawable.checkbox_on_background, "✓  Yes", yesIntent)
                .addAction(android.R.drawable.ic_delete,              "✗  No",  noIntent)
                .build()

            nm.notify(notifId, notif)
        }

        // Reschedule for next day
        schedule(context)
    }

    private fun handleNo(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) {
            context.getSystemService(NotificationManager::class.java).cancel(notifId)
        }
    }

    companion object {
        const val ACTION_DAILY_CHECK = "com.mcubi.timesheet.DAILY_SCHOOL_CHECK"

        fun ensureChannel(context: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    SCHOOL_CHANNEL_ID,
                    "Daily School Check",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Ask whether you taught at schools detected today" }
                nm.createNotificationChannel(ch)
            }
        }

        fun fireTeachingNotification(context: Context, school: SchoolMatcher.School, arrivedAt: Long, minutesSpent: Int) {
            val nm      = context.getSystemService(NotificationManager::class.java)
            ensureChannel(context, nm)
            val notifId = NOTIF_BASE_ID + (school.name.hashCode() and 0x7FFF)

            val yesIntent = PendingIntent.getActivity(
                context, notifId * 10 + 1,
                Intent(context, PeriodCountActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(PeriodCountActivity.EXTRA_SCHOOL_NAME, school.name)
                    .putExtra(PeriodCountActivity.EXTRA_SCHOOL_TYPE, school.type)
                    .putExtra(PeriodCountActivity.EXTRA_NOTIF_ID,    notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val noIntent = PendingIntent.getBroadcast(
                context, notifId * 10 + 2,
                Intent(context, DailySchoolCheckReceiver::class.java)
                    .setAction(ACTION_NO)
                    .putExtra(EXTRA_NOTIF_ID, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val fromStr = timeFmt.format(java.util.Date(arrivedAt))
            val nowStr  = timeFmt.format(java.util.Date())

            val notif = NotificationCompat.Builder(context, SCHOOL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Did you teach at ${school.name}?")
                .setContentText("$fromStr – $nowStr  (~${minutesSpent} min)")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("${school.type}  ·  $fromStr – $nowStr  (~${minutesSpent} min)\n\nTap Yes to log periods taught."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setColor(0xFF00CFA8.toInt())
                .addAction(android.R.drawable.checkbox_on_background, "✓  Yes", yesIntent)
                .addAction(android.R.drawable.ic_delete,              "✗  No",  noIntent)
                .build()

            nm.notify(notifId, notif)
        }
        const val ACTION_NO          = "com.mcubi.timesheet.SCHOOL_NO"
        const val EXTRA_NOTIF_ID     = "notif_id"
        const val SCHOOL_CHANNEL_ID  = "school_check"
        const val NOTIF_BASE_ID      = 3000

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = pendingIntent(context)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val triggerAt = cal.timeInMillis

            // On Android 12+ (API 31) exact alarms need a special user-granted permission.
            // Check first and fall back to inexact (±15 min) if not granted — the daily
            // school-check notification doesn't need millisecond precision.
            try {
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    am.canScheduleExactAlarms() else true
                if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (_: SecurityException) {
                // Fallback: best-effort inexact alarm — fires within ~15 min of 21:00
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
            context, 999,
            Intent(context, DailySchoolCheckReceiver::class.java).setAction(ACTION_DAILY_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
