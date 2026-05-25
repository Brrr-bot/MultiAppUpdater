package com.mcubi.timesheet

import android.app.NotificationManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog-style activity launched by the daily school notification (Yes button).
 * Shows 8 period buttons; on tap records the session to timesheet_log.txt.
 */
class PeriodCountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val schoolName = intent.getStringExtra(EXTRA_SCHOOL_NAME) ?: "Unknown school"
        val schoolType = intent.getStringExtra(EXTRA_SCHOOL_TYPE) ?: "secondary"
        val notifId    = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val minsPerPeriod = periodLength(schoolType, schoolName)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(MATCH, WRAP)
        }

        root.addView(TextView(this).apply {
            text     = schoolName
            textSize = 16f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#29B6F6"))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(6) }
        })

        root.addView(TextView(this).apply {
            text     = "How many periods did you teach?  (${minsPerPeriod} min each)"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#646464"))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        for (n in 1..8) {
            val btn = TextView(this).apply {
                text        = "$n"
                textSize    = 14f
                typeface    = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#29B6F6"))
                gravity     = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background  = android.graphics.drawable.GradientDrawable().apply {
                    shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setStroke(dp(1), Color.parseColor("#29B6F6"))
                    setColor(Color.TRANSPARENT)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).also {
                    it.setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener { record(schoolName, schoolType, n, minsPerPeriod, notifId) }
            }
            btnRow.addView(btn)
        }
        root.addView(btnRow)

        root.addView(TextView(this).apply {
            text     = "cancel"
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#646464"))
            gravity  = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(0, dp(16), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setOnClickListener { finish() }
        })

        setContentView(root)
    }

    private fun record(name: String, type: String, periods: Int, minsPerPeriod: Int, notifId: Int) {
        val totalMins = periods * minsPerPeriod
        val date  = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val hours = "%.2f".format(totalMins / 60.0)
        val msg   = "[TIMESHEET] $date $name ($type) " +
                    "$periods period${if (periods != 1) "s" else ""} × ${minsPerPeriod}min " +
                    "= ${totalMins}min (${hours}h)"

        try {
            val ts      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logFile = File(filesDir, "timesheet_log.txt")
            logFile.parentFile?.mkdirs()
            logFile.appendText("$ts [timesheet-app] INFO: $msg\n", Charsets.UTF_8)
        } catch (_: Exception) {}

        if (notifId >= 0) {
            getSystemService(NotificationManager::class.java).cancel(notifId)
        }
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    companion object {
        const val EXTRA_SCHOOL_NAME = "school_name"
        const val EXTRA_SCHOOL_TYPE = "school_type"
        const val EXTRA_NOTIF_ID    = "notif_id"

        fun periodLength(type: String, schoolName: String = ""): Int {
            val nameLower = schoolName.lowercase()
            return when {
                nameLower.contains("stem") -> 60
                type == "tutoring"         -> 60   // custom tutoring venues default to 60 min
                type == "kindergarten" || type == "primary" -> 35
                else -> 45
            }
        }
    }
}
