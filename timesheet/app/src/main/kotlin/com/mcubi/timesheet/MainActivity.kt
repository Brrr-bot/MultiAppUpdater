package com.mcubi.timesheet

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.app.NotificationChannel
import android.app.NotificationManager
import java.io.File
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mcubi.timesheet.databinding.ActivityMainBinding
import com.mcubi.timesheet.location.DailySchoolCheckReceiver
import com.mcubi.timesheet.location.LocationForegroundService
import com.mcubi.timesheet.location.SchoolMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

// ─── Timetable ────────────────────────────────────────────────────────────────
// Each Slot = one individual period (no grouping).
// mapsQuery → passed to Google Maps geo search when school name tapped.

data class Slot(
    val time: String,       // display time e.g. "07:30–08:15"
    val cls: String,        // class label e.g. "10C9"
    val school: String,     // school key matching SCHOOL_ACC etc.
    val grade: String,      // grade number as string
    val note: String = ""   // optional note e.g. "alt weeks"
)

// Every period is its own Slot — no ×2 grouping.
val SCHEDULE: Map<String, List<Slot>> = mapOf(
    "Mon" to listOf(
        Slot("13:45–14:30", "7/1",   "Tân Thới Hòa",        "7"),
        Slot("14:30–15:15", "7/1",   "Tân Thới Hòa",        "7"),
        Slot("15:45–16:30", "7/2",   "Tân Thới Hòa",        "7")
    ),
    "Tue" to listOf(
        Slot("07:30–08:15", "12A7",  "Tạ Quang Bửu",        "12"),
        Slot("09:30–10:15", "10C9",  "Tạ Quang Bửu",        "10"),
        Slot("10:30–11:15", "10C9",  "Tạ Quang Bửu",        "10"),
        Slot("13:30–14:15", "10C1",  "Tạ Quang Bửu",        "10"),
        Slot("14:20–15:05", "10C1",  "Tạ Quang Bửu",        "10"),
        Slot("15:20–16:05", "12A7",  "Tạ Quang Bửu",        "12")
    ),
    "Wed" to listOf(
        Slot("07:30–08:15", "10C10", "Tạ Quang Bửu",        "10"),
        Slot("09:30–10:15", "10C5",  "Tạ Quang Bửu",        "10"),
        Slot("10:30–11:15", "10C5",  "Tạ Quang Bửu",        "10"),
        Slot("12:30–13:15", "8.9",   "An Lạc",              "8"),
        Slot("13:15–14:00", "8.9",   "An Lạc",              "8"),
        Slot("14:00–14:45", "8.1",   "An Lạc",              "8"),
        Slot("15:15–16:00", "8.1",   "An Lạc",              "8"),
        Slot("19:30–20:30", "STEM",  "STEM Club",           "–")
    ),
    "Thu" to listOf(
        Slot("09:30–10:15", "11B11", "Tạ Quang Bửu",        "11"),
        Slot("10:30–11:15", "11B11", "Tạ Quang Bửu",        "11"),
        Slot("13:25–14:10", "7.2",   "Lê Văn Việt",         "7"),
        Slot("14:10–14:55", "7.4",   "Lê Văn Việt",         "7"),
        Slot("15:15–16:00", "7.4",   "Lê Văn Việt",         "7"),
        Slot("16:00–16:45", "8.2",   "Lê Văn Việt",         "8")
    ),
    "Fri" to listOf(
        Slot("07:15–08:00", "9/6",   "Tân Thới Hòa",        "9"),
        Slot("08:00–08:45", "9/9",   "Tân Thới Hòa",        "9"),
        Slot("09:15–10:00", "9/9",   "Tân Thới Hòa",        "9"),
        Slot("10:00–10:45", "9/3",   "Tân Thới Hòa",        "9"),
        Slot("10:45–11:30", "9/5",   "Tân Thới Hòa",        "9"),
        Slot("19:30–20:30", "STEM",  "STEM Club",           "–", note = "alt weeks")
    ),
    "Sat" to listOf(
        // Morning session 07:30–11:30  (alt weekends, next: 16 May)
        Slot("07:30–08:15", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("08:15–09:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("09:15–10:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("10:00–10:45", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("10:45–11:30", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        // Afternoon session 13:30–17:30
        Slot("13:30–14:15", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("14:15–15:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("15:15–16:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("16:00–16:45", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("16:45–17:30", "Lotus", "Lotus English Center", "–", note = "alt wknd")
    ),
    "Sun" to listOf(
        // Morning session 07:30–11:30  (alt weekends, next: 17 May)
        Slot("07:30–08:15", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("08:15–09:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("09:15–10:00", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("10:00–10:45", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("10:45–11:30", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        // Afternoon session 13:30–15:30
        Slot("13:30–14:15", "Lotus", "Lotus English Center", "–", note = "alt wknd"),
        Slot("14:15–15:00", "Lotus", "Lotus English Center", "–", note = "alt wknd")
    )
)

val DAYS_ORDERED = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// ─── School colours — AiALVN dark palette ─────────────────────────────────────

private val SCHOOL_ACC = mapOf(
    "Tân Thới Hòa"         to Color.parseColor("#29B6F6"),
    "Tan Thoi Hoa"         to Color.parseColor("#29B6F6"),
    "Lê Văn Việt"          to Color.parseColor("#00E676"),
    "Le Van Viet"          to Color.parseColor("#00E676"),
    "An Lạc"               to Color.parseColor("#FFB300"),
    "An Lac"               to Color.parseColor("#FFB300"),
    "Tạ Quang Bửu"         to Color.parseColor("#69F0AE"),
    "Ta Quang Buu"         to Color.parseColor("#69F0AE"),
    "Hòa Bình"             to Color.parseColor("#CE93D8"),
    "Hoa Binh"             to Color.parseColor("#CE93D8"),
    "STEM Club"            to Color.parseColor("#00E5FF"),
    "Lotus English Center" to Color.parseColor("#FF80AB"),
    "Mầm Non 30-4"         to Color.parseColor("#FF6B9D"),
    "Mam Non 30-4"         to Color.parseColor("#FF6B9D"),
    "30-4"                 to Color.parseColor("#FF6B9D")
)
private val SCHOOL_BG = mapOf(
    "Tân Thới Hòa"         to Color.parseColor("#021829"),
    "Tan Thoi Hoa"         to Color.parseColor("#021829"),
    "Lê Văn Việt"          to Color.parseColor("#011409"),
    "Le Van Viet"          to Color.parseColor("#011409"),
    "An Lạc"               to Color.parseColor("#140E00"),
    "An Lac"               to Color.parseColor("#140E00"),
    "Tạ Quang Bửu"         to Color.parseColor("#011A0D"),
    "Ta Quang Buu"         to Color.parseColor("#011A0D"),
    "Hòa Bình"             to Color.parseColor("#0E0716"),
    "Hoa Binh"             to Color.parseColor("#0E0716"),
    "STEM Club"            to Color.parseColor("#001A1F"),
    "Lotus English Center" to Color.parseColor("#1A0010"),
    "Mầm Non 30-4"         to Color.parseColor("#1A0010"),
    "Mam Non 30-4"         to Color.parseColor("#1A0010"),
    "30-4"                 to Color.parseColor("#1A0010")
)

// Google Maps search query per school
private val SCHOOL_MAPS = mapOf(
    "Tân Thới Hòa"         to "Trường THCS Tân Thới Hòa, Bình Tân, Ho Chi Minh City",
    "Tan Thoi Hoa"         to "Trường THCS Tân Thới Hòa, Bình Tân, Ho Chi Minh City",
    "Lê Văn Việt"          to "Trường THCS Lê Văn Việt, Quận 9, Ho Chi Minh City",
    "Le Van Viet"          to "Trường THCS Lê Văn Việt, Quận 9, Ho Chi Minh City",
    "An Lạc"               to "Trường THCS An Lạc, Bình Tân, Ho Chi Minh City",
    "An Lac"               to "Trường THCS An Lạc, Bình Tân, Ho Chi Minh City",
    "Tạ Quang Bửu"         to "Trường THPT Tạ Quang Bửu, Quận 8, Ho Chi Minh City",
    "Ta Quang Buu"         to "Trường THPT Tạ Quang Bửu, Quận 8, Ho Chi Minh City",
    "STEM Club"            to "Hung Vuong 2 apartment, Phu My Hung, District 7, Ho Chi Minh City",
    "Lotus English Center" to "Lotus English Center, Vung Liem, Vinh Long"
)

// Hourly rate per school (VND)
private val SCHOOL_RATE = mapOf(
    "Tân Thới Hòa"         to 520_000L,
    "Tan Thoi Hoa"         to 520_000L,
    "Lê Văn Việt"          to 520_000L,
    "Le Van Viet"          to 520_000L,
    "An Lạc"               to 520_000L,
    "An Lac"               to 520_000L,
    "Tạ Quang Bửu"         to 520_000L,
    "Ta Quang Buu"         to 520_000L,
    "STEM Club"            to 600_000L,
    "Lotus English Center" to 460_000L
)

// All lookups use fuzzy contains-match so both English and Vietnamese names resolve correctly
private fun schoolAccColor(school: String): Int {
    val lower = school.lowercase()
    SCHOOL_ACC.forEach { (k, v) -> if (lower.contains(k.lowercase()) || k.lowercase().contains(lower)) return v }
    // Fallback by fragment
    return when {
        lower.contains("thới") || lower.contains("thoi") -> Color.parseColor("#29B6F6")
        lower.contains("việt") || lower.contains("viet") -> Color.parseColor("#00E676")
        lower.contains("lạc")  || lower.contains("lac")  -> Color.parseColor("#FFB300")
        lower.contains("bửu")  || lower.contains("buu")  -> Color.parseColor("#69F0AE")
        lower.contains("hòa bình") || lower.contains("hoa binh") -> Color.parseColor("#CE93D8")
        lower.contains("stem")                            -> Color.parseColor("#00E5FF")
        lower.contains("lotus")                           -> Color.parseColor("#FF80AB")
        else -> deterministicColor(lower)
    }
}
private fun deterministicColor(key: String): Int {
    val palette = arrayOf("#29B6F6","#00E676","#FFB300","#69F0AE","#CE93D8","#00E5FF",
                          "#FF80AB","#FF6B9D","#FFCA28","#80CBC4","#EF9A9A","#B39DDB")
    return Color.parseColor(palette[(key.hashCode() and 0x7FFFFFFF) % palette.size])
}
private fun schoolBgColor(school: String): Int {
    val lower = school.lowercase()
    SCHOOL_BG.forEach { (k, v) -> if (lower.contains(k.lowercase()) || k.lowercase().contains(lower)) return v }
    return when {
        lower.contains("thới") || lower.contains("thoi") -> Color.parseColor("#021829")
        lower.contains("việt") || lower.contains("viet") -> Color.parseColor("#011409")
        lower.contains("lạc")  || lower.contains("lac")  -> Color.parseColor("#140E00")
        lower.contains("bửu")  || lower.contains("buu")  -> Color.parseColor("#011A0D")
        lower.contains("hòa bình") || lower.contains("hoa binh") -> Color.parseColor("#0E0716")
        lower.contains("stem")                            -> Color.parseColor("#001A1F")
        lower.contains("lotus")                           -> Color.parseColor("#1A0010")
        else                                              -> Color.parseColor("#0D0D0D")
    }
}
private fun schoolMapsQuery(school: String): String {
    val lower = school.lowercase()
    SCHOOL_MAPS.forEach { (k, v) -> if (lower.contains(k.lowercase()) || k.lowercase().contains(lower)) return v }
    return school
}
private fun schoolRate(name: String): Long {
    val lower = name.lowercase()
    // A company-level profile (set in the company profile editor) overrides everything.
    companyProfile(schoolCategory(name))?.let { return it.rateVnd }
    for ((key, rate) in SCHOOL_RATE) if (lower.contains(key.lowercase()) || key.lowercase().contains(lower)) return rate
    for (u in userLocations) if (lower.contains(u.name.lowercase()) || u.name.lowercase().contains(lower)) return u.rateVnd
    return 520_000L
}

// ─── Company / category grouping ────────────────────────────────────────────────
// Built-in companies. Every school that isn't STEM or Lotus rolls up to Compass.
const val CAT_COMPASS = "Compass Education"
const val CAT_STEM    = "STEM Club"
const val CAT_LOTUS   = "Lotus English Center"

// Categories that get a colour swatch in the summary
private val CATEGORY_COLORS = mapOf(
    CAT_COMPASS to "#29B6F6",
    CAT_STEM    to "#00E5FF",
    CAT_LOTUS   to "#FF80AB"
)
private fun categoryColor(cat: String): Int =
    Color.parseColor(CATEGORY_COLORS[cat] ?: "#FFB300")

// Map a school/venue name to its company category. STEM and Lotus match by name;
// a user-defined location uses whatever category was assigned when it was added;
// everything else falls under Compass Education.
private fun schoolCategory(name: String): String {
    val lower = name.lowercase()
    if (lower.contains("stem"))  return CAT_STEM
    if (lower.contains("lotus")) return CAT_LOTUS
    for (u in userLocations)
        if (u.category.isNotBlank() &&
            (lower.contains(u.name.lowercase()) || u.name.lowercase().contains(lower)))
            return u.category
    return CAT_COMPASS
}

// ─── Company profiles (editable hourly rate + period length per company) ─────────
// Persisted to filesDir/company_profiles.json. A profile overrides the built-in
// per-school rate for every school that rolls up to that company.
data class CompanyProfile(val name: String, val rateVnd: Long, val minsPerPeriod: Int)
private var companyProfiles: List<CompanyProfile> = emptyList()

private val COMPANY_DEFAULT_RATE = mapOf(
    CAT_COMPASS to 520_000L, CAT_STEM to 600_000L, CAT_LOTUS to 460_000L
)
private fun companyDefaultRate(cat: String): Long = COMPANY_DEFAULT_RATE[cat] ?: 520_000L
private fun companyProfile(cat: String): CompanyProfile? =
    companyProfiles.firstOrNull { it.name.equals(cat, ignoreCase = true) }
private fun companyRate(cat: String): Long = companyProfile(cat)?.rateVnd ?: companyDefaultRate(cat)
private fun companyMins(cat: String): Int =
    companyProfile(cat)?.minsPerPeriod?.takeIf { it > 0 }
        ?: when (cat) { CAT_STEM -> 60; else -> 45 }

// For session history stripe
private val SESSION_COLORS = mapOf(
    "tân thới hòa" to "#29B6F6", "tan thoi hoa" to "#29B6F6",
    "lê văn việt"  to "#00E676", "le van viet"  to "#00E676",
    "an lạc"       to "#FFB300", "an lac"       to "#FFB300",
    "tạ quang bửu" to "#69F0AE", "ta quang buu" to "#69F0AE",
    "hòa bình"     to "#CE93D8", "hoa binh"     to "#CE93D8",
    "stem club"    to "#00E5FF",
    "lotus"        to "#FF80AB"
)
private fun sessionColor(name: String): Int {
    val lower = name.lowercase()
    for ((key, hex) in SESSION_COLORS) if (lower.contains(key)) return Color.parseColor(hex)
    return Color.parseColor("#646464")
}
private fun shortName(full: String): String =
    full.replace(Regex("^Trường\\s+(Tiểu\\s+học|Trung\\s+học\\s+cơ\\s+sở|THCS|THPT|TH)\\s+", RegexOption.IGNORE_CASE), "").trim().ifBlank { full }

// ─── Data models ───────────────────────────────────────────────────────────────

data class TimesheetSession(
    val date: String, val school: String, val type: String, val periods: Int,
    @SerializedName("mins_per_period") val minsPerPeriod: Int,
    @SerializedName("total_mins")  val totalMins: Int,
    @SerializedName("total_hours") val totalHours: Double
)
data class TimesheetResponse(
    val month: String,
    @SerializedName("total_hours") val totalHours: Double,
    @SerializedName("total_mins")  val totalMins: Int,
    val sessions: List<TimesheetSession>
)

// ─── Lightweight Canvas grid (one View draws the whole timetable) ────────────────
// Building hundreds of TextViews for the timekeeping grid was slow/janky. This
// draws every cell + grid line in a single onDraw, so the popup opens instantly.
data class GridCell(
    val text: String, val textColor: Int, val bg: Int,
    val sizePx: Float, val bold: Boolean = false, val alignStart: Boolean = false
)

class GridSection(
    context: Context,
    private val colW: IntArray,
    private val rowH: Int,
    private val rows: List<List<GridCell>>,
    private val gridColor: Int,
    private val padStartPx: Float
) : View(context) {
    private val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val bp = android.graphics.Paint()
    private val lp = android.graphics.Paint().apply { color = gridColor; strokeWidth = 1f }
    private val totalW = colW.sum()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(totalW, rowH * rows.size)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        var y = 0f
        for (row in rows) {
            var x = 0f
            for (i in row.indices) {
                val cell = row[i]
                val cw = colW[i].toFloat()
                bp.color = cell.bg
                canvas.drawRect(x, y, x + cw, y + rowH, bp)
                canvas.drawLine(x, y, x, y + rowH, lp)                 // left border
                if (cell.text.isNotEmpty()) {
                    tp.color = cell.textColor
                    tp.isFakeBoldText = cell.bold
                    tp.textSize = cell.sizePx
                    val baseline = y + rowH / 2f - (tp.descent() + tp.ascent()) / 2f
                    if (cell.alignStart) {
                        tp.textAlign = android.graphics.Paint.Align.LEFT
                        canvas.save(); canvas.clipRect(x, y, x + cw, y + rowH)
                        canvas.drawText(cell.text, x + padStartPx, baseline, tp)
                        canvas.restore()
                    } else {
                        tp.textAlign = android.graphics.Paint.Align.CENTER
                        canvas.drawText(cell.text, x + cw / 2f, baseline, tp)
                    }
                }
                x += cw
            }
            canvas.drawLine(0f, y, totalW.toFloat(), y, lp)           // row top
            y += rowH
        }
        canvas.drawLine(0f, y, totalW.toFloat(), y, lp)              // bottom border
        canvas.drawLine(totalW.toFloat(), 0f, totalW.toFloat(), y, lp) // right border
    }
}

// ─── Formatting ────────────────────────────────────────────────────────────────

private val vndFmt: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
private fun formatVnd(amount: Long) = "₫ ${vndFmt.format(amount)}"
private fun formatHours(h: Double): String {
    val r = (h * 100).roundToInt() / 100.0
    return if (r == r.toLong().toDouble()) "${r.toLong()} hrs" else "$r hrs"
}
private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DATE_FMT  = DateTimeFormatter.ofPattern("EEE d MMM",  Locale.ENGLISH)

// Cloud session backup endpoint (mirror of the local log on the update portal)
private const val TS_BACKUP_URL = "https://app-updates.mcubittbuilders.workers.dev/api/timesheet/backup"
// Home-hub sidecar — same local/Tailscale channel the PhotoSync client uses. The dashboard reads
// the timesheet card from the hub, so it stays live with no cloud/laptop dependency.
private const val HUB_SIDECAR_TIMESHEET = "http://100.126.58.18:8767/sidecar/timesheet"

// (Session list built programmatically in buildHistoryView — no adapter needed)

// ─── Pending school for in-app verification ────────────────────────────────────

data class PendingSchool(val school: String, val minutes: Int)

// ─── User-defined location config (persisted to filesDir/user_locations.json) ──

data class UserLocationConfig(val name: String, val minsPerPeriod: Int, val rateVnd: Long, val category: String = "")

private var userLocations: List<UserLocationConfig> = emptyList()

// Period length per school (minutes) — checks named rules first, then user locations
private fun periodMins(school: String): Int {
    // An explicit company profile period length overrides the defaults below.
    companyProfile(schoolCategory(school))?.minsPerPeriod?.takeIf { it > 0 }?.let { return it }
    val lower = school.lowercase()
    when {
        lower.contains("stem")   -> return 60
        lower.contains("lotus")  -> return 45
        lower.contains("tiểu") || lower.contains("mầm") ||
        lower.contains("primary") || lower.contains("kinder") -> return 35
    }
    for (u in userLocations)
        if (lower.contains(u.name.lowercase()) || u.name.lowercase().contains(lower))
            return u.minsPerPeriod
    return 45
}

// ─── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val gson = Gson()
    private var currentMonth = YearMonth.now()

    // ── Location picker state (shared between dialog and ActivityResult callback) ─
    private var pendingPickedLat: Double? = null
    private var pendingPickedLng: Double? = null
    private var pendingCoordView: TextView? = null

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val lat = result.data?.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, Double.NaN) ?: return@registerForActivityResult
            val lng = result.data?.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, Double.NaN) ?: return@registerForActivityResult
            if (lat.isNaN() || lng.isNaN()) return@registerForActivityResult
            pendingPickedLat = lat
            pendingPickedLng = lng
            pendingCoordView?.text = "%.6f,  %.6f".format(lat, lng)
        }
    }

    // Today's day abbreviation, e.g. "Tue"
    private val todayDay: String get() =
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))

    // Container for pending-verification cards (added programmatically)
    // pendingContainer wired to the XML pendingVerifySection (visible on both tabs)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Tab switching
        b.btnTabSchedule.setOnClickListener { showTab(0) }
        b.btnTabHistory.setOnClickListener  { showTab(1); fetchTodayPending() }
        b.btnTabSummary.setOnClickListener  { showTab(2) }
        b.btnAddSession.setOnClickListener  { showManualAddDialog() }

        // Month nav (history only)
        b.btnPrevMonth.setOnClickListener { currentMonth = currentMonth.minusMonths(1); fetchData() }
        b.btnNextMonth.setOnClickListener { currentMonth = currentMonth.plusMonths(1);  fetchData() }

        b.swipeRefresh.setColorSchemeColors(Color.parseColor("#29B6F6"), Color.parseColor("#FFB300"))
        b.swipeRefresh.setOnRefreshListener {
            // All data is local — just re-read it. (The old laptop-server sync was removed;
            // that server is retired and every call blocked on a network timeout.)
            fetchData()
            fetchTodayPending(force = true)   // explicit refresh bypasses the throttle
            b.swipeRefresh.isRefreshing = false
        }

        b.btnRetry.setOnClickListener { fetchData() }

        refreshUserLocations()
        refreshCompanyProfiles()
        buildScheduleView()
        showTab(1)
        fetchData()
        loadDismissedSchools()
        fetchTodayPending()
        syncTimesheetBackup()   // pull cloud backup / restore on reinstall, then mirror up
        requestLocationPermissions()
    }

    override fun onResume() {
        super.onResume()
        fetchTodayPending()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) on ${android.os.Build.MODEL}")
        createUpdateChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        checkForUpdate()
    }

    // ── OTA update check ───────────────────────────────────────────────────────

    private var updateInProgress = false

    private fun createUpdateChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(UPDATE_CH, "App Updates", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun checkForUpdate() {
        if (updateInProgress) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/version/timesheet")
                    .build()).execute()
                if (!resp.isSuccessful) return@launch
                val json = org.json.JSONObject(resp.body?.string() ?: return@launch)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl     = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    downloadAndNotify(serverCode, apkUrl)
                } else {
                    // Already up to date — clear any stale "update ready" notification left
                    // over from a previous version (e.g. if the user updated via ADB without
                    // tapping it, setAutoCancel never fired).
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
                // Force the system package installer so Android doesn't show an installer chooser.
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
                        .setContentTitle("Timesheet update ready")
                        .setContentText("Build $buildNum downloaded — tap to install")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) { updateInProgress = false }
        }
    }


    // ── Today pending verification ─────────────────────────────────────────────

    private var lastPendingCheckMs = 0L

    private fun fetchTodayPending(force: Boolean = false) {
        // Throttle: the GPS visit analysis is the heaviest thing the app does. Don't re-run it
        // on every onResume/tab-tap — once every 60s is plenty for "today's pending sessions".
        val now = System.currentTimeMillis()
        if (!force && now - lastPendingCheckMs < 60_000L) return
        lastPendingCheckMs = now
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today   = LocalDate.now().toString()
                val visits  = SchoolMatcher(this@MainActivity).findAllSchoolVisitsToday()

                // Parse every [TIMESHEET] entry for today: record school name + the wall-clock
                // time the entry was written so we can match it to a specific visit window.
                data class VerifiedEntry(val schoolLower: String, val loggedAt: Long)
                val verified = mutableListOf<VerifiedEntry>()
                val tsFmt    = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val linePat  = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
                val schoolPat = Regex("""\[TIMESHEET\]\s+\d{4}-\d{2}-\d{2}\s+(.+?)\s+\(""")
                if (logFile.exists()) {
                    logFile.forEachLine { line ->
                        if ("[TIMESHEET]" !in line || today !in line) return@forEachLine
                        val ts     = linePat.find(line)?.groupValues?.get(1) ?: return@forEachLine
                        val school = schoolPat.find(line)?.groupValues?.get(1) ?: return@forEachLine
                        val loggedAt = try { tsFmt.parse(ts)?.time ?: return@forEachLine }
                                       catch (_: Exception) { return@forEachLine }
                        verified.add(VerifiedEntry(school.trim().lowercase(), loggedAt))
                    }
                }

                // A visit is still pending if no verified entry for that school was logged
                // *during or after* this visit window started (handles same-school return visits).
                val pending = visits
                    .filter { it.likelyTaught }
                    .filter { v ->
                        val nameLower = v.school.name.lowercase()
                        verified.none { e ->
                            val nameMatch = e.schoolLower.contains(nameLower) ||
                                            nameLower.contains(e.schoolLower)
                            nameMatch && e.loggedAt >= v.arrivedAt
                        }
                    }
                    .map { PendingSchool(it.school.name, it.minutesSpent) }

                withContext(Dispatchers.Main) { showPendingCards(pending) }
            } catch (_: Throwable) {}   // never let today-pending detection crash the app
        }
    }

    private val dismissedPendingSchools = mutableSetOf<String>()
    private val dismissPrefs by lazy { getSharedPreferences("pending_dismiss", MODE_PRIVATE) }
    private fun loadDismissedSchools() {
        val today = LocalDate.now().toString()
        val saved = dismissPrefs.getString("dismiss_date", "")
        if (saved == today) {
            dismissedPendingSchools.addAll(dismissPrefs.getStringSet("dismissed_schools", emptySet()) ?: emptySet())
        } else {
            dismissedPendingSchools.clear()
        }
    }
    private fun saveDismissedSchools() {
        dismissPrefs.edit()
            .putStringSet("dismissed_schools", dismissedPendingSchools.toSet())
            .putString("dismiss_date", LocalDate.now().toString())
            .apply()
    }

    private fun showPendingCards(pending: List<PendingSchool>) {
        loadDismissedSchools()
        val container = b.pendingVerifySection
        container.removeAllViews()
        val filtered = pending.filter { it.school !in dismissedPendingSchools }
        if (filtered.isEmpty()) {
            showPendingSection(false)
            return
        }
        showPendingSection(true)

        val sky     = Color.parseColor("#29B6F6")
        val gold    = Color.parseColor("#FFB300")
        val cardBg  = Color.parseColor("#0D0D0D")
        val dimText = Color.parseColor("#646464")
        val white   = Color.WHITE

        // Section header + dismiss button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(this).apply {
            text          = "▸  VERIFY TODAY"
            textSize      = 11f
            typeface      = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(gold)
            letterSpacing = 0.1f
            setPadding(dp(16), dp(12), dp(8), dp(4))
            layoutParams  = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text        = "✕"
            textSize    = 15f
            typeface    = android.graphics.Typeface.MONOSPACE
            setTextColor(dimText)
            setPadding(dp(16), dp(10), dp(16), dp(4))
            isClickable = true; isFocusable = true
            setOnClickListener {
                filtered.forEach { dismissedPendingSchools.add(it.school) }
                saveDismissedSchools()
                showPendingSection(false)
            }
        })
        container.addView(headerRow)

        for (ps in pending) {
            val acc    = schoolAccColor(ps.school)
            val slotBg = schoolBgColor(ps.school)
            val pMins  = periodMins(ps.school)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(slotBg)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(dp(12), dp(4), dp(12), dp(4))
                }
            }

            // Accent stripe
            card.addView(View(this).apply {
                setBackgroundColor(acc)
                layoutParams = LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT)
            })

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            // School name + detected time
            cardContent.addView(TextView(this).apply {
                text     = ps.school
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(acc)
            })
            cardContent.addView(TextView(this).apply {
                text     = "detected ~${ps.minutes} min today  ·  ${pMins}min/period"
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(dimText)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(0, dp(2), 0, dp(8))
                }
            })

            // Period buttons 1–8  (neon outline style)
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            for (n in 1..8) {
                val outline = android.graphics.drawable.GradientDrawable().apply {
                    shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setStroke(dp(1), acc)
                    setColor(Color.TRANSPARENT)
                }
                btnRow.addView(TextView(this).apply {
                    text        = "$n"
                    textSize    = 12f
                    typeface    = android.graphics.Typeface.MONOSPACE
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(acc)
                    background  = outline
                    gravity     = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also {
                        it.setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener {
                        // Flash fill on tap
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = dp(3).toFloat()
                            setColor(acc)
                        }
                        verifySession(ps.school, n, pMins)
                    }
                })
            }
            cardContent.addView(btnRow)
            card.addView(cardContent)
            container.addView(card)
        }

        // Bottom spacer for breathing room
        container.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16))
        })

        // Switch to history tab so the cards are visible
        if (b.layoutHistory.visibility != View.VISIBLE) {
            showTab(1)
        }
    }

    private fun verifySession(school: String, periods: Int, minsPerPeriod: Int) {
        dismissedPendingSchools.remove(school)
        saveDismissedSchools()
        val today     = LocalDate.now().toString()
        val totalMins = periods * minsPerPeriod
        val hours     = totalMins / 60.0
        val msg = "[TIMESHEET] $today $school (secondary) " +
                  "$periods period${if (periods != 1) "s" else ""} × ${minsPerPeriod}min " +
                  "= ${totalMins}min (${String.format("%.2f", hours)}h)"

        CoroutineScope(Dispatchers.IO).launch {
            appendToLog(msg)
            withContext(Dispatchers.Main) {
                fetchTodayPending()
                fetchData()
            }
        }
    }

    // ── Location permissions & service ────────────────────────────────────────

    private fun requestLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val needed = mutableListOf<String>()
        if (!fineGranted) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_LOCATION)
        } else {
            // Fine location already granted — start service and check background separately
            onLocationPermissionGranted()
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // On Android 11+ this opens the app's permission settings page where the user
            // can choose "Allow all the time" for background location.
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND_LOC
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            // Match by name, not index, since the array order can vary
            val map = permissions.zip(grantResults.toTypedArray()).toMap()
            val fineGranted = map[Manifest.permission.ACCESS_FINE_LOCATION] ==
                              PackageManager.PERMISSION_GRANTED
            if (fineGranted) {
                onLocationPermissionGranted()
                requestBackgroundLocationIfNeeded()
            }
        }
    }

    private fun onLocationPermissionGranted() {
        LocationForegroundService.start(this)
        DailySchoolCheckReceiver.schedule(this)
    }

    // ── Schedule builder ────────────────────────────────────────────────────────

    private fun buildScheduleView() {
        val today = todayDay
        val container = b.scheduleContainer
        container.removeAllViews()

        val spaceBg  = Color.parseColor("#03040A")
        val cardBg   = Color.parseColor("#0D0D0D")
        val white    = Color.WHITE
        val dimText  = Color.parseColor("#646464")
        val labelClr = Color.parseColor("#969696")
        val sky      = Color.parseColor("#29B6F6")
        val gold     = Color.parseColor("#FFB300")

        for (day in DAYS_ORDERED) {
            val slots   = SCHEDULE[day] ?: emptyList()
            val isToday = day == today

            // Day card — dark, no elevation shadow, border via accent stripe
            val dayCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                    it.setMargins(dp(8), dp(4), dp(8), dp(16))
                }
            }

            // Day header — accent bar on left, dark bg
            val headerAcc = if (isToday) gold else sky
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                    it.bottomMargin = dp(6)
                }
            }
            // Accent stripe on left of header
            headerRow.addView(View(this).apply {
                setBackgroundColor(headerAcc)
                layoutParams = LinearLayout.LayoutParams(dp(3), MATCH)
            })
            val fullDay = when (day) {
                "Mon" -> "MONDAY"; "Tue" -> "TUESDAY"; "Wed" -> "WEDNESDAY"
                "Thu" -> "THURSDAY"; "Fri" -> "FRIDAY"; else -> day
            }
            headerRow.addView(TextView(this).apply {
                text      = if (isToday) "▶  $fullDay  · today" else fullDay
                textSize  = 12f
                setTextColor(if (isToday) gold else sky)
                typeface  = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
                setPadding(dp(12), dp(10), dp(8), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            val slotCount = slots.size
            headerRow.addView(TextView(this).apply {
                text      = "$slotCount pd"
                textSize  = 10f
                setTextColor(dimText)
                typeface  = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, dp(12), 0)
            })

            dayCard.addView(headerRow)

            if (slots.isEmpty()) {
                dayCard.addView(TextView(this).apply {
                    text     = "no classes"
                    textSize = 11f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(dimText)
                    gravity  = Gravity.CENTER
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                })
            } else {
                val slotsContainer = LinearLayout(this).apply {
                    orientation  = LinearLayout.VERTICAL
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                }

                // Group consecutive slots by school into coloured subcards
                data class SlotGroup(val school: String, val slots: List<Slot>)
                val groups = mutableListOf<SlotGroup>()
                var curSchool = slots.first().school
                var curSlots  = mutableListOf<Slot>()
                for (s in slots) {
                    if (s.school == curSchool) { curSlots.add(s) }
                    else { groups.add(SlotGroup(curSchool, curSlots.toList())); curSchool = s.school; curSlots = mutableListOf(s) }
                }
                groups.add(SlotGroup(curSchool, curSlots.toList()))

                for (group in groups) {
                    val acc    = schoolAccColor(group.school)
                    val slotBg = schoolBgColor(group.school)

                    // School subcard with coloured border — full width, no side gap
                    val subCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            setColor(slotBg)
                            setStroke(dp(1), acc)
                            cornerRadius = dp(10).toFloat()
                        }
                        clipToOutline = true
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                            it.setMargins(0, 0, 0, dp(8))
                        }
                    }
                    // School name header inside subcard
                    subCard.addView(TextView(this).apply {
                        text = group.school
                        textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(acc); letterSpacing = 0.04f
                        setPadding(dp(14), dp(10), dp(14), dp(4))
                    })

                    for (slot in group.slots) {
                    val slotContent = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(8), dp(14), dp(10))
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    }

                    // Time
                    val noteTag = if (slot.note.isNotEmpty()) "  [${slot.note}]" else ""
                    slotContent.addView(TextView(this).apply {
                        text          = "${slot.time}$noteTag"
                        textSize      = 10f
                        typeface      = android.graphics.Typeface.MONOSPACE
                        setTextColor(dimText)
                        letterSpacing = 0.03f
                    })

                    // Class badge + full school name (tappable → Maps)
                    val badgeRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity     = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                            it.setMargins(0, dp(3), 0, 0)
                        }
                    }

                    badgeRow.addView(TextView(this).apply {
                        text     = slot.cls
                        textSize = 13f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(acc)
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also {
                            it.setMargins(0, 0, dp(8), 0)
                        }
                    })

                    // Full school name — clickable, opens Google Maps
                    val gradeStr = if (slot.grade == "–") "" else " · G${slot.grade}"
                    val mapsQuery = schoolMapsQuery(slot.school)
                    badgeRow.addView(TextView(this).apply {
                        text          = "${slot.school}$gradeStr"
                        textSize      = 11f
                        typeface      = android.graphics.Typeface.MONOSPACE
                        setTextColor(acc)
                        isClickable   = true
                        isFocusable   = true
                        background    = android.util.TypedValue().let { tv ->
                            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                            ContextCompat.getDrawable(context, tv.resourceId)
                        }
                        setOnClickListener {
                            val uri = Uri.parse("geo:0,0?q=${Uri.encode(mapsQuery)}")
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    })

                    slotContent.addView(badgeRow)
                    subCard.addView(slotContent)
                    } // end for slot in group.slots
                    slotsContainer.addView(subCard)
                } // end for group in groups
                dayCard.addView(slotsContainer)
            }

            container.addView(dayCard)
        }

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(24))
        })
    }

    // ── Tab management ──────────────────────────────────────────────────────────

    // 0 = Schedule, 1 = History, 2 = Summary
    private fun showTab(tab: Int) {
        val sky     = Color.parseColor("#29B6F6")
        val dimText = Color.parseColor("#646464")

        b.layoutSchedule.visibility = if (tab == 0) View.VISIBLE else View.GONE
        b.layoutHistory.visibility  = if (tab == 1) View.VISIBLE else View.GONE
        b.layoutSummary.visibility  = if (tab == 2) View.VISIBLE else View.GONE
        // Hide inactive GlowCardLayout wrappers so their margins disappear
        findViewById<GlowCardLayout>(R.id.glow_card_schedule)?.visibility = if (tab == 0) View.VISIBLE else View.GONE
        val histGlow = findViewById<GlowCardLayout>(R.id.glow_card_history)
        histGlow?.visibility  = if (tab == 1 || tab == 2) View.VISIBLE else View.GONE
        // Kill glow on summary tab — it wraps both tabs but the ambient bloom looks wrong on summary
        if (tab == 2) histGlow?.setGlowColor(android.graphics.Color.TRANSPARENT)
        else if (tab == 1) histGlow?.setGlowColor(android.graphics.Color.argb(50, 0x2e, 0xe6, 0xa6))
        // Month nav only applies to the History tab
        b.monthNav.visibility       = if (tab == 1) View.VISIBLE else View.GONE

        b.btnTabSchedule.setTextColor(if (tab == 0) sky else dimText)
        b.btnTabHistory.setTextColor (if (tab == 1) sky else dimText)
        b.btnTabSummary.setTextColor (if (tab == 2) sky else dimText)
        b.tabIndicatorSchedule.visibility = if (tab == 0) View.VISIBLE else View.INVISIBLE
        b.tabIndicatorHistory.visibility  = if (tab == 1) View.VISIBLE else View.INVISIBLE
        b.tabIndicatorSummary.visibility  = if (tab == 2) View.VISIBLE else View.INVISIBLE

        if (tab == 2) buildSummaryView()
    }

    // ── Data fetch ──────────────────────────────────────────────────────────────

    // ── Local log file ─────────────────────────────────────────────────────────

    private val logFile get() = java.io.File(filesDir, "timesheet_log.txt")

    private fun appendToLog(msg: String) {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())
        logFile.parentFile?.mkdirs()
        logFile.appendText("$ts [timesheet-app] INFO: $msg\n", Charsets.UTF_8)
        pushBackup()
    }

    // ── Cloud backup ────────────────────────────────────────────────────────────
    // Sessions are mirrored to the update portal so a reinstall restores them. The
    // local log is always the source of truth; the cloud is a best-effort mirror,
    // so the app keeps working with no internet at all.

    private var cloudBackupReady = false

    /** Every [TIMESHEET] session line currently in the local log. */
    private fun sessionLinesFromLog(): List<String> =
        if (!logFile.exists()) emptyList() else logFile.readLines().filter { "[TIMESHEET]" in it }

    /** date|school key used to dedup sessions, or null if the line isn't a session. */
    private fun sessionKeyOf(line: String): String? {
        val m = Regex("""\[TIMESHEET\]\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+\(""").find(line) ?: return null
        val (date, school) = m.destructured
        return "$date|$school"
    }

    /** On startup: pull the cloud backup, restore any sessions missing locally, then back up. */
    private fun syncTimesheetBackup() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder().url(TS_BACKUP_URL).build()).execute()
                if (!resp.isSuccessful) return@launch
                val arr = org.json.JSONObject(resp.body?.string() ?: "{}").optJSONArray("sessions")
                    ?: org.json.JSONArray()
                val cloudLines = (0 until arr.length()).map { arr.getString(it) }
                cloudBackupReady = true

                val localKeys = sessionLinesFromLog().mapNotNull { sessionKeyOf(it) }.toSet()
                // Append any session the cloud has that this device is missing (restore on reinstall).
                val missing = cloudLines.filter { sessionKeyOf(it)?.let { k -> k !in localKeys } == true }
                if (missing.isNotEmpty()) {
                    logFile.parentFile?.mkdirs()
                    logFile.appendText(missing.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                    withContext(Dispatchers.Main) { fetchData() }
                }
                pushBackup()   // back up the current local set (incl. anything just restored)
            } catch (_: Exception) {}
        }
    }

    /** Overwrite the cloud mirror with this device's current sessions (propagates edits/deletes).
     *  Never runs until we've confirmed the cloud state, so an empty fresh install can't wipe it. */
    /** Mirror sessions to the home hub (local network) for the dashboard's timesheet card. */
    private fun pushSessionsToHub() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("sessions", org.json.JSONArray(sessionLinesFromLog()))
                    put("updatedAt", System.currentTimeMillis())
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder().url(HUB_SIDECAR_TIMESHEET).post(body).build())
                    .execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun pushBackup() {
        pushSessionsToHub()   // local hub mirror — independent of the cloud backup
        if (!cloudBackupReady) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("sessions", org.json.JSONArray(sessionLinesFromLog()))
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder().url(TS_BACKUP_URL).post(body).build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun readMonthSessions(monthStr: String): TimesheetResponse {
        val pattern = Regex(
            """\[TIMESHEET\]\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+\((\w+)\)\s+(\d+)\s+period.*?×\s*(\d+)min\s*=\s*(\d+)min"""
        )
        val sessions = mutableListOf<TimesheetSession>()
        var totalMins = 0
        if (logFile.exists()) {
            logFile.forEachLine { line ->
                if ("[TIMESHEET]" !in line) return@forEachLine
                val m = pattern.find(line) ?: return@forEachLine
                val (date, school, type, periods, mpp, tmins) = m.destructured
                if (!date.startsWith(monthStr)) return@forEachLine
                val tMins = tmins.toInt()
                totalMins += tMins
                sessions.add(TimesheetSession(date, school, type, periods.toInt(), mpp.toInt(), tMins, tMins / 60.0))
            }
        }
        return TimesheetResponse(monthStr, totalMins / 60.0, totalMins, sessions)
    }

    /** Silently sync sessions from the laptop server — runs on every launch and swipe-to-refresh.
     *  Sessions already in the local log (keyed by date+school) are skipped to avoid duplicates.
     *  When not connected to the laptop this is a silent no-op; existing local data is unaffected. */
    private fun mergeFromLaptopIfReachable() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Build dedup key set from existing local log (date|school)
                val seenKeys = mutableSetOf<String>()
                val keyPat = Regex("""\[TIMESHEET\]\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+\(""")
                if (logFile.exists()) {
                    logFile.forEachLine { line ->
                        val m = keyPat.find(line) ?: return@forEachLine
                        val (date, school) = m.destructured
                        seenKeys.add("$date|$school")
                    }
                }

                // Fetch last 12 months from laptop (covers full history on first install)
                val now = YearMonth.now()
                val sb  = StringBuilder()
                for (i in 0..11) {
                    val monthStr = now.minusMonths(i.toLong())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    val req  = Request.Builder()
                        .url("http://100.107.143.20:9000/timesheet?month=$monthStr").build()
                    val resp = client.newCall(req).execute()
                    if (!resp.isSuccessful) continue
                    val data = gson.fromJson(resp.body?.string() ?: "", TimesheetResponse::class.java)
                    for (s in data.sessions) {
                        val key = "${s.date}|${s.school}"
                        if (key in seenKeys) continue          // already recorded locally
                        seenKeys.add(key)                       // prevent intra-batch dupes
                        val h = s.totalMins / 60.0
                        sb.append("${s.date} 00:00:00 [import] INFO: [TIMESHEET] ${s.date} ${s.school} " +
                            "(${s.type}) ${s.periods} period${if (s.periods != 1) "s" else ""} " +
                            "× ${s.minsPerPeriod}min = ${s.totalMins}min (${"%.2f".format(h)}h)\n")
                    }
                }

                if (sb.isNotEmpty()) {
                    logFile.parentFile?.mkdirs()
                    logFile.appendText(sb.toString(), Charsets.UTF_8)  // append only new entries
                    withContext(Dispatchers.Main) { fetchData() }
                }
            } catch (_: Exception) {}   // laptop unreachable — silently skip, local data intact
        }
    }

    private fun fetchData() {
        val monthStr = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        b.tvMonthLabel.text = currentMonth.format(MONTH_FMT).uppercase()
        // No loading spinner — the data is a small local file, read it and render directly.
        CoroutineScope(Dispatchers.IO).launch {
            val data = readMonthSessions(monthStr)
            withContext(Dispatchers.Main) { renderData(data) }
        }
    }

    private fun renderData(data: TimesheetResponse) {
        b.swipeRefresh.isRefreshing = false
        b.layoutLoading.visibility = View.GONE
        showErrorSection(false)

        val earnings = data.sessions.sumOf { s -> (s.totalHours * schoolRate(s.school)).toLong() }

        b.tvTopBarEarnings.text  = formatVnd(earnings)
        b.tvSummaryMonth.text    = currentMonth.format(MONTH_FMT)
        b.tvSummaryHours.text    = formatHours(data.totalHours)
        b.tvSummaryPeriods.text  = "${data.sessions.sumOf { it.periods }} p"
        b.tvSummaryEarnings.text = formatVnd(earnings)

        val pct = min(100, ((data.totalHours / 80.0) * 100).toInt())
        b.progressHours.progress = pct
        b.tvProgressLabel.text   = "${"%.2f".format(data.totalHours)} / 80 hrs"

        val sorted = data.sessions.sortedByDescending { it.date }
        if (sorted.isEmpty()) {
            b.tvEmpty.visibility      = View.VISIBLE
            b.historyContainer.visibility = View.GONE
        } else {
            b.tvEmpty.visibility      = View.GONE
            b.historyContainer.visibility = View.VISIBLE
            buildHistoryView(sorted)
        }
    }

    private fun buildHistoryView(sessions: List<TimesheetSession>) {
        val container = b.historyContainer
        container.removeAllViews()

        val sky     = Color.parseColor("#29B6F6")
        val gold    = Color.parseColor("#FFB300")
        val cardBg  = Color.parseColor("#0D0D0D")
        val dimText = Color.parseColor("#646464")

        // Group by date descending
        val byDate = sessions.groupBy { it.date }
            .entries.sortedByDescending { it.key }

        val dateHeaderFmt = DateTimeFormatter.ofPattern("EEE  d  MMM", Locale.ENGLISH)

        for ((date, daySessions) in byDate) {
            val parsedDate   = LocalDate.parse(date)
            val isToday      = parsedDate == LocalDate.now()
            val headerAccent = if (isToday) gold else sky

            // ── Date header ────────────────────────────────────────────────────
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setBackgroundColor(cardBg)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                    it.setMargins(dp(12), dp(10), dp(12), 0)
                }
            }
            headerRow.addView(View(this).apply {
                setBackgroundColor(headerAccent)
                layoutParams = LinearLayout.LayoutParams(dp(3), MATCH)
            })
            val dayLabel    = parsedDate.format(dateHeaderFmt).uppercase()
            val dayPeriods  = daySessions.sumOf { it.periods }
            val periodLabel = "$dayPeriods period${if (dayPeriods != 1) "s" else ""}"

            val labelCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            labelCol.addView(TextView(this).apply {
                text          = if (isToday) "▶  $dayLabel  · today" else dayLabel
                textSize      = 11f
                typeface      = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (isToday) gold else sky)
                letterSpacing = 0.08f
            })
            labelCol.addView(TextView(this).apply {
                text     = periodLabel
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also {
                    it.topMargin = dp(2)
                }
            })
            headerRow.addView(labelCol)

            // Day total earnings on the right
            val dayEarnings = daySessions.sumOf { s ->
                (s.minsPerPeriod / 60.0 * schoolRate(s.school) * s.periods).toLong()
            }
            headerRow.addView(TextView(this).apply {
                text      = formatVnd(dayEarnings)
                textSize  = 11f
                typeface  = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(gold)
                setPadding(dp(8), dp(8), dp(12), dp(8))
            })
            container.addView(headerRow)

            // ── One card per individual period ─────────────────────────────────
            for (s in daySessions) {
                val acc              = sessionColor(s.school)
                val hoursPerPeriod   = s.minsPerPeriod / 60.0
                val earningsPerPeriod = (hoursPerPeriod * schoolRate(s.school)).toLong()

                repeat(s.periods) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundColor(cardBg)
                        isLongClickable = true
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                            it.setMargins(dp(12), dp(1), dp(12), 0)
                        }
                    }

                    // Colour stripe
                    card.addView(View(this).apply {
                        setBackgroundColor(acc)
                        layoutParams = LinearLayout.LayoutParams(dp(3), MATCH)
                    })

                    val content = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity     = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    }

                    // Left: school name + period duration
                    val leftCol = LinearLayout(this).apply {
                        orientation  = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    }
                    leftCol.addView(TextView(this).apply {
                        text      = s.school
                        textSize  = 13f
                        typeface  = android.graphics.Typeface.MONOSPACE
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(acc)
                    })
                    leftCol.addView(TextView(this).apply {
                        text      = "1 period  ·  ${s.minsPerPeriod} min"
                        textSize  = 10f
                        typeface  = android.graphics.Typeface.MONOSPACE
                        setTextColor(dimText)
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also {
                            it.setMargins(0, dp(2), 0, 0)
                        }
                    })

                    // Right: hours + earnings for this one period
                    val rightCol = LinearLayout(this).apply {
                        orientation  = LinearLayout.VERTICAL
                        gravity      = Gravity.END
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                    }
                    rightCol.addView(TextView(this).apply {
                        text      = formatHours(hoursPerPeriod)
                        textSize  = 13f
                        typeface  = android.graphics.Typeface.MONOSPACE
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(sky)
                        gravity   = Gravity.END
                    })
                    rightCol.addView(TextView(this).apply {
                        text      = formatVnd(earningsPerPeriod)
                        textSize  = 11f
                        typeface  = android.graphics.Typeface.MONOSPACE
                        setTextColor(gold)
                        gravity   = Gravity.END
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also {
                            it.setMargins(0, dp(2), 0, 0)
                        }
                    })

                    content.addView(leftCol)
                    content.addView(rightCol)
                    card.addView(content)

                    card.setOnLongClickListener {
                        showSessionContextMenu(s)
                        true
                    }

                    container.addView(card)
                }
            }

            // Thin separator below each day group
            container.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).also {
                    it.setMargins(dp(12), dp(6), dp(12), 0)
                }
            })
        }
    }

    // ── Manual add session dialog ───────────────────────────────────────────────

    /** Strip Vietnamese tones/diacritics so "ta quang buu" matches "Tạ Quang Bửu". */
    private fun removeTones(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')

    /** ArrayAdapter whose filter matches anywhere in the string (not just prefix),
     *  tone-insensitive so unaccented queries find Vietnamese school names. */
    private fun containsAdapter(items: List<String>): ArrayAdapter<String> {
        val ctx  = this
        val all  = items.toList()
        // Pre-compute normalised versions for fast filtering
        val allNorm = all.map { removeTones(it).lowercase(Locale.ROOT) }
        return object : ArrayAdapter<String>(ctx, R.layout.dropdown_item, all.toMutableList()) {
            private val myFilter = object : android.widget.Filter() {
                override fun performFiltering(c: CharSequence?): android.widget.Filter.FilterResults {
                    val q   = removeTones(c?.toString() ?: "").lowercase(Locale.ROOT)
                    val res = if (q.isEmpty()) all
                              else all.indices.filter { i -> allNorm[i].contains(q) }.map { i -> all[i] }
                    val fr  = android.widget.Filter.FilterResults()
                    fr.values = res
                    fr.count  = res.size
                    return fr
                }
                @Suppress("UNCHECKED_CAST")
                override fun publishResults(c: CharSequence?, fr: android.widget.Filter.FilterResults?) {
                    val res = fr?.values as? List<String> ?: emptyList()
                    clear(); addAll(res); notifyDataSetChanged()
                }
            }
            override fun getFilter(): android.widget.Filter = myFilter
        }
    }

    // ── Summary tab (all-time, grouped by company) ──────────────────────────────

    /** All [TIMESHEET] sessions in the local log, across every month. */
    private fun readAllSessions(): List<TimesheetSession> {
        val pattern = Regex(
            """\[TIMESHEET\]\s+(\d{4}-\d{2}-\d{2})\s+(.+?)\s+\((\w+)\)\s+(\d+)\s+period.*?×\s*(\d+)min\s*=\s*(\d+)min"""
        )
        val sessions = mutableListOf<TimesheetSession>()
        if (logFile.exists()) {
            logFile.forEachLine { line ->
                if ("[TIMESHEET]" !in line) return@forEachLine
                val m = pattern.find(line) ?: return@forEachLine
                val (date, school, type, periods, mpp, tmins) = m.destructured
                val tMins = tmins.toInt()
                sessions.add(TimesheetSession(date, school, type, periods.toInt(), mpp.toInt(), tMins, tMins / 60.0))
            }
        }
        return sessions
    }

    private fun sessionEarnings(s: TimesheetSession): Long = (s.totalHours * schoolRate(s.school)).toLong()

    private fun buildSummaryView() {
        val container = b.summaryContainer
        container.removeAllViews()

        val white   = Color.WHITE
        val dim      = Color.parseColor("#969696")
        val dimmer   = Color.parseColor("#646464")
        val gold     = Color.parseColor("#FFB300")
        val cardBg   = Color.parseColor("#0a1322")
        val mono     = android.graphics.Typeface.MONOSPACE

        // Task 1: current month only
        val monthStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
        val monthLabel = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)).uppercase()
        val sessions = readAllSessions().filter { it.date.startsWith(monthStr) }
        if (sessions.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No sessions logged yet"
                setTextColor(dimmer); textSize = 13f; typeface = mono
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(48), dp(32), dp(32))
            })
            return
        }

        // Month header — prominent title at top
        container.addView(TextView(this).apply {
            text = monthLabel
            setTextColor(gold); textSize = 20f; typeface = mono
            setTypeface(typeface, android.graphics.Typeface.BOLD); letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(dp(14), dp(14), dp(14), dp(2)) }
        })
        container.addView(TextView(this).apply {
            text = "Tap for monthly grid  ·  long-press to edit rate"
            setTextColor(dimmer); textSize = 10f; typeface = mono
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(dp(14), dp(2), dp(14), dp(8)) }
        })

        val byCat   = sessions.groupBy { schoolCategory(it.school) }
        val builtin = listOf(CAT_COMPASS, CAT_STEM, CAT_LOTUS)
        val others  = byCat.keys.filter { it !in builtin }.sorted()
        val orderedCats = (builtin + others).filter { byCat.containsKey(it) }

        var grandPeriods = 0
        var grandEarn    = 0L

        for (cat in orderedCats) {
            val catSessions = byCat[cat]!!
            val accent      = categoryColor(cat)
            val catPeriods  = catSessions.sumOf { it.periods }
            val catEarn     = catSessions.sumOf { sessionEarnings(it) }
            grandPeriods += catPeriods
            grandEarn    += catEarn

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(cardBg)
                    setStroke(dp(1), accent)
                    cornerRadius = dp(8).toFloat()
                }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                    it.setMargins(dp(12), dp(8), dp(12), dp(4))
                }
            }
            // No left stripe needed — border IS the accent colour
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                isClickable = true; isFocusable = true
                setOnClickListener { showCompanyMonthlyBreakdown(cat) }
                setOnLongClickListener { showCompanyProfileEditor(cat); true }
            }

            // Header: company name + chevron, then totals
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
            }
            headerRow.addView(TextView(this).apply {
                text = cat.uppercase()
                setTextColor(accent); textSize = 14f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD); letterSpacing = 0.05f
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            headerRow.addView(TextView(this).apply {
                text = "$catPeriods p  ›"
                setTextColor(dim); textSize = 11f; typeface = mono
            })
            col.addView(headerRow)

            col.addView(TextView(this).apply {
                text = formatVnd(catEarn)
                setTextColor(gold); textSize = 16f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
            })

            // Per-school breakdown within this company
            val bySchool = catSessions.groupBy { it.school }
                .entries.sortedByDescending { e -> e.value.sumOf { it.periods } }
            for ((school, schoolSessions) in bySchool) {
                val sp = schoolSessions.sumOf { it.periods }
                val se = schoolSessions.sumOf { sessionEarnings(it) }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(3) }
                }
                row.addView(TextView(this).apply {
                    text = shortName(school)
                    setTextColor(white); textSize = 12f; typeface = mono
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                row.addView(TextView(this).apply {
                    text = "$sp p"
                    setTextColor(dim); textSize = 11f; typeface = mono
                    layoutParams = LinearLayout.LayoutParams(dp(54), WRAP)
                    gravity = Gravity.END
                })
                row.addView(TextView(this).apply {
                    text = formatVnd(se)
                    setTextColor(dim); textSize = 11f; typeface = mono
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(dp(96), WRAP)
                })
                col.addView(row)
            }

            card.addView(col)
            container.addView(card)
        }

        // Grand total card — horizontal: left=label+periods, right=big earnings
        val totalCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#0a1322"))
                setStroke(dp(1), gold)
                cornerRadius = dp(8).toFloat()
            }
            clipToOutline = true
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                it.setMargins(dp(12), dp(10), dp(12), dp(8))
            }
        }
        val totalLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        totalLeft.addView(TextView(this).apply {
            text = "TOTAL"
            setTextColor(gold); textSize = 11f; typeface = mono
            setTypeface(typeface, android.graphics.Typeface.BOLD); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(4) }
        })
        totalLeft.addView(TextView(this).apply {
            text = "$grandPeriods periods"
            setTextColor(dim); textSize = 12f; typeface = mono
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        })
        totalCard.addView(totalLeft)
        totalCard.addView(TextView(this).apply {
            text = formatVnd(grandEarn)
            setTextColor(gold); textSize = 22f; typeface = mono
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        container.addView(totalCard)
    }

    /** Monthly timekeeping grid for one company: schools (rows) × days (columns),
     *  period counts per cell, Period/Hour totals per school + a grand total row.
     *  ‹ › navigate between the months that have data so they can be compared.
     *  The whole grid is one Canvas-drawn View (GridSection) so it opens instantly. */
    private fun showCompanyMonthlyBreakdown(category: String) {
        val white   = Color.WHITE
        val dim     = Color.parseColor("#969696")
        val gold    = Color.parseColor("#FFB300")
        val accent  = categoryColor(category)
        val gridLn  = Color.parseColor("#2A2A2A")
        val headBg  = Color.parseColor("#161616")
        val cellBg  = Color.parseColor("#0D0D0D")
        val mono    = android.graphics.Typeface.MONOSPACE
        val sd      = resources.displayMetrics.scaledDensity
        fun sp(v: Float) = v * sd

        val sessions = readAllSessions().filter { schoolCategory(it.school) == category }
        val months   = sessions.map { it.date.substring(0, 7) }.distinct().sorted()

        val ROW_H    = dp(30)
        val NAME_W   = dp(116)
        val DAY_W    = dp(30)
        val SUM_W    = dp(52)
        val padStart = dp(6).toFloat()

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ── Month nav (large tap targets) ────────────────────────────────────────
        var idx = months.lastIndex
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val btnPrev = TextView(this).apply {
            text = "‹"; textSize = 24f; typeface = mono; setTextColor(accent)
            setPadding(dp(22), dp(6), dp(22), dp(6)); isClickable = true; isFocusable = true
        }
        val lblMonth = TextView(this).apply {
            textSize = 13f; typeface = mono; setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val btnNext = TextView(this).apply {
            text = "›"; textSize = 24f; typeface = mono; setTextColor(accent)
            setPadding(dp(22), dp(6), dp(22), dp(6)); isClickable = true; isFocusable = true
        }
        navRow.addView(btnPrev); navRow.addView(lblMonth); navRow.addView(btnNext)
        outer.addView(navRow)

        // ── Body: frozen name column + horizontally-scrollable day grid ───────────
        val nameHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val gridHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(gridHolder)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val bodyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; addView(nameHolder); addView(hScroll)
        }
        outer.addView(ScrollView(this).apply { addView(bodyRow) })

        fun render() {
            nameHolder.removeAllViews(); gridHolder.removeAllViews()
            if (months.isEmpty()) { lblMonth.text = "No data"; return }
            val ym   = java.time.YearMonth.parse(months[idx])
            val days = ym.lengthOfMonth()
            lblMonth.text = ym.format(MONTH_FMT)
            btnPrev.alpha = if (idx > 0) 1f else 0.3f
            btnNext.alpha = if (idx < months.lastIndex) 1f else 0.3f

            val monthSessions = sessions.filter { it.date.startsWith(months[idx]) }
            val bySchool = monthSessions.groupBy { it.school }
                .entries.sortedBy { shortName(it.key).lowercase() }

            val nameRows = ArrayList<List<GridCell>>()
            val gridRows = ArrayList<List<GridCell>>()

            // Header
            nameRows.add(listOf(GridCell("SCHOOLS", dim, headBg, sp(10f), true, true)))
            val hg = ArrayList<GridCell>()
            for (d in 1..days) hg.add(GridCell("$d", dim, headBg, sp(9f)))
            hg.add(GridCell("PER", gold, headBg, sp(9f), true))
            hg.add(GridCell("HRS", gold, headBg, sp(9f), true))
            gridRows.add(hg)

            var totP = 0; var totMins = 0
            for ((school, ss) in bySchool) {
                val perDay = IntArray(days + 1)
                for (s in ss) {
                    val d = s.date.substring(8, 10).toIntOrNull() ?: continue
                    if (d in 1..days) perDay[d] += s.periods
                }
                val spp = ss.sumOf { it.periods }; val sMins = ss.sumOf { it.totalMins }
                totP += spp; totMins += sMins

                nameRows.add(listOf(GridCell(shortName(school), white, cellBg, sp(10f), false, true)))
                val rg = ArrayList<GridCell>()
                for (d in 1..days) {
                    val v = perDay[d]
                    rg.add(GridCell(if (v > 0) "$v" else "", if (v > 0) accent else dim, cellBg, sp(11f), v > 0))
                }
                rg.add(GridCell("$spp", white, cellBg, sp(10f), true))
                rg.add(GridCell("%.2f".format(sMins / 60.0), dim, cellBg, sp(10f)))
                gridRows.add(rg)
            }

            // Total row
            nameRows.add(listOf(GridCell("TOTAL", gold, headBg, sp(10f), true, true)))
            val tg = ArrayList<GridCell>()
            for (d in 1..days) tg.add(GridCell("", dim, headBg, sp(11f)))
            tg.add(GridCell("$totP", gold, headBg, sp(11f), true))
            tg.add(GridCell("%.2f".format(totMins / 60.0), gold, headBg, sp(11f), true))
            gridRows.add(tg)

            nameHolder.addView(GridSection(this, intArrayOf(NAME_W), ROW_H, nameRows, gridLn, padStart))
            val gw = IntArray(days + 2) { if (it < days) DAY_W else SUM_W }
            gridHolder.addView(GridSection(this, gw, ROW_H, gridRows, gridLn, padStart))
        }

        btnPrev.setOnClickListener { if (idx > 0) { idx--; render() } }
        btnNext.setOnClickListener { if (idx < months.lastIndex) { idx++; render() } }
        render()

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("$category · timekeeping")
            .setView(outer)
            .setPositiveButton("CLOSE", null)
            .let { if (!isFinishing && !isDestroyed) try { it.show() } catch (_: Exception) {} }
    }

    private fun showManualAddDialog() {
        // Load school list from the asset on IO, then show dialog on Main
        CoroutineScope(Dispatchers.IO).launch {
            val allSchoolNames: List<String> = try {
                SchoolMatcher(this@MainActivity).loadSchools()
                    .map { it.name }
                    .distinct()
                    .sortedWith(Comparator { a, b -> a.compareTo(b) })
            } catch (_: Exception) {
                listOf("Tân Thới Hòa", "Lê Văn Việt", "An Lạc", "Tạ Quang Bửu",
                       "STEM Club", "Lotus English Center")
            }
            withContext(Dispatchers.Main) {
                // The activity may have been backgrounded/finished while the school list loaded.
                // Showing a dialog on a dead window throws BadTokenException — guard against it.
                if (!isFinishing && !isDestroyed) buildAndShowAddDialog(allSchoolNames)
            }
        }
    }

    private fun buildAndShowAddDialog(schoolNames: List<String>) {
        val sky     = Color.parseColor("#29B6F6")
        val gold    = Color.parseColor("#FFB300")
        val green   = Color.parseColor("#00E676")
        val bgColor = Color.parseColor("#0D0D0D")
        val white   = Color.WHITE
        val dimText = Color.parseColor("#969696")
        val mono    = android.graphics.Typeface.MONOSPACE

        // ── Tab state ──────────────────────────────────────────────────────────
        var currentTab = 0  // 0 = Add Session, 1 = Add Location

        // ── Session tab state ──────────────────────────────────────────────────
        val fullByShort = linkedMapOf<String, String>()
        for (full in schoolNames) fullByShort[shortName(full)] = full
        val shortNames = fullByShort.keys.toList()
        var sessionSelectedFullName = ""
        var sessionSelectedPeriods  = 1

        // ── Location tab state ─────────────────────────────────────────────────
        var locType           = "school"
        var locRadiusM        = 100.0
        var locMinsPerPeriod  = 45
        var locRateType       = "hourly"
        var locCategory       = CAT_COMPASS   // which company this venue rolls up to

        pendingPickedLat  = null
        pendingPickedLng  = null
        pendingCoordView  = null

        // ── Root ───────────────────────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // ── Tab bar ────────────────────────────────────────────────────────────
        val tabBar = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#141414"))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val btnTabSession = TextView(this).apply {
            text = "ADD SESSION"; textSize = 11f; typeface = mono
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(sky); gravity = Gravity.CENTER; letterSpacing = 0.08f
            setPadding(dp(8), dp(14), dp(8), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val btnTabLocation = TextView(this).apply {
            text = "ADD LOCATION"; textSize = 11f; typeface = mono
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(dimText); gravity = Gravity.CENTER; letterSpacing = 0.08f
            setPadding(dp(8), dp(14), dp(8), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        tabBar.addView(btnTabSession)
        tabBar.addView(btnTabLocation)
        root.addView(tabBar)

        // Thin indicator line under active tab
        val indRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(2))
        }
        val indSession  = View(this).apply { setBackgroundColor(sky);              layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f) }
        val indLocation = View(this).apply { setBackgroundColor(Color.TRANSPARENT); layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f) }
        indRow.addView(indSession); indRow.addView(indLocation)
        root.addView(indRow)

        // ── Scrollable content panels ──────────────────────────────────────────
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val contentCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(contentCol)
        root.addView(scrollView)

        // ────────────────────────────────────────────────────────────────────────
        // PANEL 1 — Add Session (existing form)
        // ────────────────────────────────────────────────────────────────────────
        val panel1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        panel1.addView(label("SCHOOL", dimText))
        val adapter = containsAdapter(shortNames)
        val schoolInput = AutoCompleteTextView(this).apply {
            setAdapter(adapter); threshold = 0; hint = "Search school…"
            textSize = 13f; typeface = mono; setTextColor(white); setHintTextColor(dimText)
            setDropDownBackgroundResource(android.R.color.black); dropDownHeight = dp(220)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
            setOnFocusChangeListener { _, has -> if (has) post { showDropDown() } }
            setOnItemClickListener { _, _, pos, _ ->
                val s = adapter.getItem(pos) ?: return@setOnItemClickListener
                sessionSelectedFullName = fullByShort[s] ?: s; setText(s, false)
            }
        }
        panel1.addView(schoolInput)

        panel1.addView(label("DATE  (yyyy-MM-dd)", dimText))
        val dateInput = EditText(this).apply {
            setText(LocalDate.now().toString()); textSize = 14f; typeface = mono
            setTextColor(white); inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        panel1.addView(dateInput)

        panel1.addView(label("TIME  (HH:mm, optional)", dimText))
        val timeInput = EditText(this).apply {
            hint = "e.g. 09:30"; textSize = 14f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        panel1.addView(timeInput)

        panel1.addView(label("PERIODS", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val periodBtns = mutableListOf<TextView>()
        val periodBtnRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        fun refreshPeriodBtns() {
            periodBtns.forEachIndexed { idx, tv ->
                val n = idx + 1; val sel = n == sessionSelectedPeriods
                tv.background = outlineDrawable(dp(3).toFloat(), sky, if (sel) sky else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else sky)
            }
        }
        for (n in 1..8) {
            val btn = TextView(this).apply {
                text = "$n"; textSize = 13f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(sky); gravity = Gravity.CENTER; isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { sessionSelectedPeriods = n; refreshPeriodBtns() }
            }
            periodBtns.add(btn); periodBtnRow.addView(btn)
        }
        panel1.addView(periodBtnRow)
        refreshPeriodBtns()

        contentCol.addView(panel1)

        // ────────────────────────────────────────────────────────────────────────
        // PANEL 2 — Add Location
        // ────────────────────────────────────────────────────────────────────────
        val panel2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            visibility  = View.GONE
        }

        // Name
        panel2.addView(label("LOCATION NAME", dimText))
        val locNameInput = EditText(this).apply {
            hint = "e.g. My Tutoring Centre"; textSize = 14f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        panel2.addView(locNameInput)

        // Type toggles
        panel2.addView(label("TYPE", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val typeOptions = listOf("school", "club", "tutoring", "other")
        val typeLabels  = listOf("SCHOOL", "CLUB", "TUTORING", "OTHER")
        val typeBtns    = mutableListOf<TextView>()
        val typeRow     = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        fun refreshTypeBtns() {
            typeBtns.forEachIndexed { i, tv ->
                val sel = typeOptions[i] == locType
                tv.background = outlineDrawable(dp(3).toFloat(), sky, if (sel) sky else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else sky)
            }
        }
        typeLabels.forEachIndexed { i, lbl ->
            val btn = TextView(this).apply {
                text = lbl; textSize = 10f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(sky); gravity = Gravity.CENTER; letterSpacing = 0.06f
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { locType = typeOptions[i]; refreshTypeBtns() }
            }
            typeBtns.add(btn); typeRow.addView(btn)
        }
        panel2.addView(typeRow)
        refreshTypeBtns()

        // Company / category — which business this venue's earnings roll up under.
        panel2.addView(label("COMPANY / CATEGORY", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val catQuickPicks = listOf(CAT_COMPASS, CAT_STEM, CAT_LOTUS)
        val catQuickLabels = listOf("COMPASS", "STEM", "LOTUS")
        val catBtns = mutableListOf<TextView>()
        val catRow  = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        val customCatInput = EditText(this).apply {
            hint = "or type a custom company…"; textSize = 13f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        val purple = Color.parseColor("#B47FFF")
        fun refreshCatBtns() {
            catBtns.forEachIndexed { i, tv ->
                val sel = catQuickPicks[i] == locCategory && customCatInput.text.toString().isBlank()
                tv.background = outlineDrawable(dp(3).toFloat(), purple, if (sel) purple else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else purple)
            }
        }
        catQuickLabels.forEachIndexed { i, lbl ->
            val btn = TextView(this).apply {
                text = lbl; textSize = 10f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(purple); gravity = Gravity.CENTER; letterSpacing = 0.06f
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener {
                    locCategory = catQuickPicks[i]; customCatInput.text.clear(); refreshCatBtns()
                }
            }
            catBtns.add(btn); catRow.addView(btn)
        }
        // Typing a custom company overrides the quick-pick selection
        customCatInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { refreshCatBtns() }
        })
        panel2.addView(catRow)
        panel2.addView(customCatInput)
        refreshCatBtns()

        // Location picker
        panel2.addView(label("LOCATION", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val coordView = TextView(this).apply {
            text     = "No location set — use a button below"
            textSize = 11f; typeface = mono; setTextColor(dimText)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        pendingCoordView = coordView
        panel2.addView(coordView)

        val locBtnRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        val btnGps = actionButton("◉  USE CURRENT GPS", sky)
        btnGps.setOnClickListener {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    pendingPickedLat = loc.latitude
                    pendingPickedLng = loc.longitude
                    coordView.text   = "%.6f,  %.6f".format(loc.latitude, loc.longitude)
                    coordView.setTextColor(green)
                } else {
                    Toast.makeText(this, "No GPS fix yet — try Pick on Map", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            }
        }
        val btnMap = actionButton("⊕  PICK ON MAP", sky)
        btnMap.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java)
            pendingPickedLat?.let { lat ->
                intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, lat)
                intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, pendingPickedLng ?: 0.0)
            }
            pickLocationLauncher.launch(intent)
        }
        locBtnRow.addView(btnGps.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).also { lp -> lp.marginEnd = dp(6) }
        })
        locBtnRow.addView(btnMap.also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
        })
        panel2.addView(locBtnRow)

        // Detection radius
        panel2.addView(label("DETECTION RADIUS", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val radiusOptions = listOf(50.0, 80.0, 100.0, 150.0)
        val radiusLabels  = listOf("50 m", "80 m", "100 m", "150 m")
        val radiusBtns    = mutableListOf<TextView>()
        val radiusRow     = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val customRadiusInput = EditText(this).apply {
            hint = "custom m"; textSize = 12f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType    = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                it.topMargin = dp(6); it.bottomMargin = dp(16)
            }
        }
        fun refreshRadiusBtns() {
            radiusBtns.forEachIndexed { i, tv ->
                val sel = radiusOptions[i] == locRadiusM
                tv.background = outlineDrawable(dp(3).toFloat(), gold, if (sel) gold else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else gold)
            }
        }
        radiusLabels.forEachIndexed { i, lbl ->
            val btn = TextView(this).apply {
                text = lbl; textSize = 10f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(gold); gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener {
                    locRadiusM = radiusOptions[i]; customRadiusInput.text.clear(); refreshRadiusBtns()
                }
            }
            radiusBtns.add(btn); radiusRow.addView(btn)
        }
        panel2.addView(radiusRow)
        panel2.addView(customRadiusInput)
        refreshRadiusBtns()

        // Period length
        panel2.addView(label("PERIOD LENGTH", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val minsOptions = listOf(35, 45, 60)
        val minsLabels  = listOf("35 min", "45 min", "60 min")
        val minsBtns    = mutableListOf<TextView>()
        val minsRow     = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val customMinsInput = EditText(this).apply {
            hint = "custom min"; textSize = 12f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType    = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                it.topMargin = dp(6); it.bottomMargin = dp(16)
            }
        }
        fun refreshMinsBtns() {
            minsBtns.forEachIndexed { i, tv ->
                val sel = minsOptions[i] == locMinsPerPeriod
                tv.background = outlineDrawable(dp(3).toFloat(), sky, if (sel) sky else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else sky)
            }
        }
        minsLabels.forEachIndexed { i, lbl ->
            val btn = TextView(this).apply {
                text = lbl; textSize = 10f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(sky); gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener {
                    locMinsPerPeriod = minsOptions[i]; customMinsInput.text.clear(); refreshMinsBtns()
                }
            }
            minsBtns.add(btn); minsRow.addView(btn)
        }
        panel2.addView(minsRow)
        panel2.addView(customMinsInput)
        refreshMinsBtns()

        // Pay rate
        panel2.addView(label("PAY RATE", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8)
        })
        val rateTypeBtns = mutableListOf<TextView>()
        val rateTypeRow  = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        val rateHint = TextView(this).apply {
            textSize = 10f; typeface = mono; setTextColor(dimText)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(6) }
        }
        fun updateRateHint(rateText: String, mins: Int) {
            if (locRateType == "per_period") {
                val amount = rateText.toLongOrNull() ?: 0L
                if (amount > 0 && mins > 0) {
                    val hourly = (amount / (mins / 60.0)).toLong()
                    rateHint.text = "≈ ${vndFmt.format(hourly)} ₫/hr"
                } else rateHint.text = "Enter rate above to see hourly equivalent"
            } else rateHint.text = ""
        }
        fun refreshRateTypeBtns() {
            rateTypeBtns.forEachIndexed { i, tv ->
                val types = listOf("hourly", "per_period")
                val sel   = types[i] == locRateType
                tv.background = outlineDrawable(dp(3).toFloat(), gold, if (sel) gold else Color.TRANSPARENT)
                tv.setTextColor(if (sel) Color.BLACK else gold)
            }
        }
        listOf("PER HOUR", "PER PERIOD").forEachIndexed { i, lbl ->
            val types = listOf("hourly", "per_period")
            val btn = TextView(this).apply {
                text = lbl; textSize = 10f; typeface = mono
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(gold); gravity = Gravity.CENTER; letterSpacing = 0.06f
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { locRateType = types[i]; refreshRateTypeBtns() }
            }
            rateTypeBtns.add(btn); rateTypeRow.addView(btn)
        }
        panel2.addView(rateTypeRow)
        refreshRateTypeBtns()

        val rateInput = EditText(this).apply {
            hint = "e.g. 520000"; textSize = 14f; typeface = mono
            setTextColor(white); setHintTextColor(dimText)
            inputType    = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val mins = customMinsInput.text.toString().toIntOrNull() ?: locMinsPerPeriod
                    updateRateHint(s?.toString() ?: "", mins)
                }
            })
        }
        panel2.addView(rateInput)
        panel2.addView(rateHint)
        panel2.addView(label("VND", dimText).also {
            (it.layoutParams as LinearLayout.LayoutParams).also { lp ->
                lp.bottomMargin = dp(16); lp.topMargin = 0
            }
        })

        contentCol.addView(panel2)

        // ── Tab switch function ────────────────────────────────────────────────
        fun switchTab(tab: Int) {
            currentTab = tab
            panel1.visibility    = if (tab == 0) View.VISIBLE else View.GONE
            panel2.visibility    = if (tab == 1) View.VISIBLE else View.GONE
            btnTabSession.setTextColor(if (tab == 0) sky else dimText)
            btnTabLocation.setTextColor(if (tab == 1) sky else dimText)
            indSession.setBackgroundColor(if (tab == 0) sky else Color.TRANSPARENT)
            indLocation.setBackgroundColor(if (tab == 1) sky else Color.TRANSPARENT)
        }
        btnTabSession.setOnClickListener  { switchTab(0) }
        btnTabLocation.setOnClickListener { switchTab(1) }

        // ── Show dialog ────────────────────────────────────────────────────────
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(root)
            .setPositiveButton("SAVE") { _, _ ->
                if (currentTab == 0) {
                    // ── Save session ──────────────────────────────────────────
                    val typed  = schoolInput.text.toString().trim()
                    val school = when {
                        sessionSelectedFullName.isNotEmpty() -> sessionSelectedFullName
                        fullByShort.containsKey(typed)       -> fullByShort[typed]!!
                        else -> typed
                    }
                    val date = dateInput.text.toString().trim()
                    val time = timeInput.text.toString().trim()
                    if (school.isEmpty()) {
                        Toast.makeText(this, "Select a school", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                    }
                    if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                        Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                    }
                    val pMins     = periodMins(school)
                    val totalMins = sessionSelectedPeriods * pMins
                    val hours     = totalMins / 60.0
                    val timeSuffix = if (time.matches(Regex("""\d{2}:\d{2}"""))) "  [$time]" else ""
                    val msg = "[TIMESHEET] $date $school (secondary) " +
                              "$sessionSelectedPeriods period${if (sessionSelectedPeriods != 1) "s" else ""} × ${pMins}min " +
                              "= ${totalMins}min (${"%.2f".format(hours)}h)$timeSuffix"
                    CoroutineScope(Dispatchers.IO).launch {
                        appendToLog(msg)
                        withContext(Dispatchers.Main) {
                            fetchData(); fetchTodayPending()
                            Toast.makeText(this@MainActivity, "Session saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // ── Save location ─────────────────────────────────────────
                    val name   = locNameInput.text.toString().trim()
                    val lat    = pendingPickedLat
                    val lng    = pendingPickedLng
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Enter a location name", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                    }
                    if (lat == null || lng == null) {
                        Toast.makeText(this, "Set a location — use GPS or Pick on Map", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                    }
                    val radiusM      = customRadiusInput.text.toString().toDoubleOrNull()?.coerceIn(10.0, 500.0) ?: locRadiusM
                    val minsPerPeriod = customMinsInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: locMinsPerPeriod
                    val rateRaw      = rateInput.text.toString().toLongOrNull() ?: 520_000L
                    val rateVnd      = when (locRateType) {
                        "per_period" -> (rateRaw / (minsPerPeriod / 60.0)).toLong()
                        else         -> rateRaw
                    }
                    // Custom company text overrides the quick-pick selection
                    val typedCat = customCatInput.text.toString().trim()
                    val category = if (typedCat.isNotEmpty()) typedCat else locCategory
                    CoroutineScope(Dispatchers.IO).launch {
                        saveUserLocation(name, locType, lat, lng, radiusM, minsPerPeriod, rateVnd, category)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Location '$name' saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .let { if (!isFinishing && !isDestroyed) try { it.show() } catch (_: Exception) {} }
    }

    // ── Small UI helpers for the dialog ────────────────────────────────────────

    private fun label(text: String, color: Int) = TextView(this).apply {
        this.text     = text
        textSize      = 10f
        typeface      = android.graphics.Typeface.MONOSPACE
        setTextColor(color)
        letterSpacing = 0.1f
        layoutParams  = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
    }

    private fun actionButton(text: String, color: Int) = TextView(this).apply {
        this.text     = text
        textSize      = 11f
        typeface      = android.graphics.Typeface.MONOSPACE
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(color)
        gravity       = Gravity.CENTER
        isClickable   = true; isFocusable = true
        background    = outlineDrawable(dp(3).toFloat(), color, Color.TRANSPARENT)
    }

    private fun outlineDrawable(cornerRadius: Float, strokeColor: Int, fillColor: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setStroke(dp(1), strokeColor)
            setColor(fillColor)
        }

    // ── Long-press context menu for history entries ────────────────────────────

    private fun showSessionContextMenu(s: TimesheetSession) {
        val company = schoolCategory(s.school)
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("${s.school}  ·  ${s.date}")
            .setItems(arrayOf("Edit session", "Delete session", "Edit $company profile")) { _, which ->
                when (which) {
                    0 -> editSessionDialog(s)
                    1 -> confirmDeleteSession(s)
                    2 -> showCompanyProfileEditor(company)
                }
            }
            .show()
    }

    private fun confirmDeleteSession(s: TimesheetSession) {
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Delete session?")
            .setMessage("${s.school}\n${s.date}  ·  ${s.periods} period${if (s.periods != 1) "s" else ""}")
            .setPositiveButton("DELETE") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    deleteSessionFromLog(s)
                    withContext(Dispatchers.Main) {
                        fetchData()
                        Toast.makeText(this@MainActivity, "Session deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun deleteSessionFromLog(s: TimesheetSession) {
        if (!logFile.exists()) return
        val matchPat = Regex(
            """\[TIMESHEET\]\s+${Regex.escape(s.date)}\s+${Regex.escape(s.school)}\s+\(\w+\)\s+${s.periods}\s+period.*?×\s*${s.minsPerPeriod}min"""
        )
        val lines = logFile.readLines(Charsets.UTF_8)
        val filtered = lines.filter { line -> !matchPat.containsMatchIn(line) }
        logFile.writeText(filtered.joinToString("\n").let { if (it.isNotEmpty()) "$it\n" else "" }, Charsets.UTF_8)
        pushBackup()
    }

    private fun editSessionDialog(original: TimesheetSession) {
        CoroutineScope(Dispatchers.IO).launch {
            val allSchoolNames: List<String> = try {
                SchoolMatcher(this@MainActivity).loadSchools()
                    .map { it.name }.distinct().sorted()
            } catch (_: Exception) {
                listOf("Tân Thới Hòa", "Lê Văn Việt", "An Lạc", "Tạ Quang Bửu",
                       "STEM Club", "Lotus English Center")
            }
            withContext(Dispatchers.Main) { buildEditDialog(original, allSchoolNames) }
        }
    }

    private fun buildEditDialog(original: TimesheetSession, schoolNames: List<String>) {
        val sky     = Color.parseColor("#29B6F6")
        val bgColor = Color.parseColor("#0D0D0D")
        val white   = Color.WHITE
        val dimText = Color.parseColor("#969696")

        val fullByShort = linkedMapOf<String, String>()
        for (full in schoolNames) fullByShort[shortName(full)] = full
        val shortNames = fullByShort.keys.toList()

        var selectedFullName = original.school

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            setBackgroundColor(bgColor)
        }

        layout.addView(TextView(this).apply {
            text = "SCHOOL"; textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(dimText); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })

        val adapter = containsAdapter(shortNames)
        val schoolInput = AutoCompleteTextView(this).apply {
            setAdapter(adapter); threshold = 0
            setText(shortName(original.school), false)
            textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(white); setHintTextColor(dimText)
            setDropDownBackgroundResource(android.R.color.black); dropDownHeight = dp(220)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) post { showDropDown() } }
            setOnItemClickListener { _, _, position, _ ->
                val short = adapter.getItem(position) ?: return@setOnItemClickListener
                selectedFullName = fullByShort[short] ?: short
                setText(short, false)
            }
        }
        layout.addView(schoolInput)

        layout.addView(TextView(this).apply {
            text = "DATE  (yyyy-MM-dd)"; textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(dimText); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })
        val dateInput = EditText(this).apply {
            setText(original.date); textSize = 14f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(white); inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        layout.addView(dateInput)

        layout.addView(TextView(this).apply {
            text = "MINS PER PERIOD"; textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(dimText); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })
        val minsInput = EditText(this).apply {
            setText(original.minsPerPeriod.toString())
            textSize = 14f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(white); inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        layout.addView(minsInput)

        layout.addView(TextView(this).apply {
            text = "PERIODS"; textSize = 10f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(dimText); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        })

        var selectedPeriods = original.periods
        val periodBtns = mutableListOf<TextView>()
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }

        fun updatePeriodSelection() {
            periodBtns.forEachIndexed { idx, tv ->
                val n = idx + 1; val sel = n == selectedPeriods
                tv.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat(); setStroke(dp(1), sky)
                    setColor(if (sel) sky else Color.TRANSPARENT)
                }
                tv.setTextColor(if (sel) Color.BLACK else sky)
            }
        }

        for (n in 1..8) {
            val btn = TextView(this).apply {
                text = "$n"; textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(sky); gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).also {
                    it.setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener { selectedPeriods = n; updatePeriodSelection() }
            }
            periodBtns.add(btn); btnRow.addView(btn)
        }
        layout.addView(btnRow)
        updatePeriodSelection()

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Edit Session")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val typed = schoolInput.text.toString().trim()
                val school = when {
                    selectedFullName.isNotEmpty() && selectedFullName != original.school -> selectedFullName
                    fullByShort.containsKey(typed) -> fullByShort[typed]!!
                    else -> typed.ifEmpty { original.school }
                }
                val date = dateInput.text.toString().trim()
                if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val pMins     = minsInput.text.toString().trim().toIntOrNull()
                    ?.coerceIn(1, 180) ?: periodMins(school)
                val totalMins = selectedPeriods * pMins
                val hours     = totalMins / 60.0
                val newLine = "[TIMESHEET] $date $school (secondary) " +
                    "$selectedPeriods period${if (selectedPeriods != 1) "s" else ""} × ${pMins}min " +
                    "= ${totalMins}min (${"%.2f".format(hours)}h)"
                CoroutineScope(Dispatchers.IO).launch {
                    replaceSessionInLog(original, newLine)
                    withContext(Dispatchers.Main) {
                        fetchData()
                        Toast.makeText(this@MainActivity, "Session updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun replaceSessionInLog(original: TimesheetSession, newLogEntry: String) {
        if (!logFile.exists()) return
        val matchPat = Regex(
            """\[TIMESHEET\]\s+${Regex.escape(original.date)}\s+${Regex.escape(original.school)}\s+\(\w+\)\s+${original.periods}\s+period.*?×\s*${original.minsPerPeriod}min"""
        )
        var replaced = false
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())
        val lines = logFile.readLines(Charsets.UTF_8).map { line ->
            if (!replaced && matchPat.containsMatchIn(line)) {
                replaced = true
                "$ts [timesheet-app] INFO: $newLogEntry"
            } else line
        }
        logFile.writeText(lines.joinToString("\n").let { if (it.isNotEmpty()) "$it\n" else "" }, Charsets.UTF_8)
        pushBackup()
    }

    // ── Company profiles (editable rate / period length per company) ───────────

    private fun refreshCompanyProfiles() {
        val f = java.io.File(filesDir, "company_profiles.json")
        companyProfiles = if (!f.exists()) emptyList() else try {
            val arr = org.json.JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CompanyProfile(
                    name          = o.getString("name"),
                    rateVnd       = o.optLong("rate_vnd", 520_000L),
                    minsPerPeriod = o.optInt("mins_per_period", 0)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveCompanyProfile(name: String, rateVnd: Long, minsPerPeriod: Int) {
        val file = java.io.File(filesDir, "company_profiles.json")
        val arr  = if (file.exists()) {
            try { org.json.JSONArray(file.readText()) } catch (_: Exception) { org.json.JSONArray() }
        } else org.json.JSONArray()
        fun obj() = org.json.JSONObject().apply {
            put("name", name); put("rate_vnd", rateVnd); put("mins_per_period", minsPerPeriod)
        }
        var replaced = false
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("name").equals(name, ignoreCase = true)) {
                arr.put(i, obj()); replaced = true; break
            }
        }
        if (!replaced) arr.put(obj())
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2), Charsets.UTF_8)
        refreshCompanyProfiles()
    }

    /** Editable profile for a company: hourly rate + default period length. */
    private fun showCompanyProfileEditor(category: String) {
        val white   = Color.WHITE
        val dim     = Color.parseColor("#969696")
        val accent  = categoryColor(category)
        val mono    = android.graphics.Typeface.MONOSPACE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        root.addView(TextView(this).apply {
            text = "Earnings for every venue under this company use this hourly rate."
            textSize = 11f; typeface = mono; setTextColor(dim)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        })

        root.addView(label("HOURLY RATE (VND)", dim))
        val rateInput = EditText(this).apply {
            setText(companyRate(category).toString()); textSize = 16f; typeface = mono
            setTextColor(white); inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        root.addView(rateInput)

        root.addView(label("DEFAULT PERIOD LENGTH (MIN)", dim))
        val minsInput = EditText(this).apply {
            setText(companyMins(category).toString()); textSize = 16f; typeface = mono
            setTextColor(white); inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        root.addView(minsInput)

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("$category · profile")
            .setView(root)
            .setPositiveButton("SAVE") { _, _ ->
                val rate = rateInput.text.toString().toLongOrNull()?.coerceIn(0, 100_000_000L) ?: companyRate(category)
                val mins = minsInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: companyMins(category)
                saveCompanyProfile(category, rate, mins)
                Toast.makeText(this, "$category rate set to ${formatVnd(rate)}/hr", Toast.LENGTH_SHORT).show()
                if (b.layoutSummary.visibility == View.VISIBLE) buildSummaryView()
                if (b.layoutHistory.visibility == View.VISIBLE) fetchData()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── User location management ───────────────────────────────────────────────

    private fun refreshUserLocations() {
        val f = java.io.File(filesDir, "user_locations.json")
        userLocations = if (!f.exists()) emptyList() else try {
            val arr = org.json.JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserLocationConfig(
                    name          = o.getString("name"),
                    minsPerPeriod = o.optInt("mins_per_period", 45),
                    rateVnd       = o.optLong("rate_vnd", 520_000L),
                    category      = o.optString("category", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveUserLocation(
        name: String, type: String,
        lat: Double, lng: Double, radiusM: Double,
        minsPerPeriod: Int, rateVnd: Long, category: String = ""
    ) {
        val file = java.io.File(filesDir, "user_locations.json")
        val arr  = if (file.exists()) {
            try { org.json.JSONArray(file.readText()) } catch (_: Exception) { org.json.JSONArray() }
        } else org.json.JSONArray()

        // Replace existing entry with same name (case-insensitive) or append new one
        var replaced = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("name").equals(name, ignoreCase = true)) {
                arr.put(i, buildLocJson(name, type, lat, lng, radiusM, minsPerPeriod, rateVnd, category))
                replaced = true
                break
            }
        }
        if (!replaced) arr.put(buildLocJson(name, type, lat, lng, radiusM, minsPerPeriod, rateVnd, category))

        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2), Charsets.UTF_8)
        com.mcubi.timesheet.location.SchoolMatcher.invalidateCache()
        refreshUserLocations()
    }

    private fun buildLocJson(
        name: String, type: String,
        lat: Double, lng: Double, radiusM: Double,
        minsPerPeriod: Int, rateVnd: Long, category: String = ""
    ): org.json.JSONObject = org.json.JSONObject().apply {
        put("name",           name)
        put("type",           type)
        put("lat",            lat)
        put("lng",            lng)
        put("radius_m",       radiusM)
        put("mins_per_period", minsPerPeriod)
        put("rate_vnd",       rateVnd)
        put("category",       category)
    }

    private fun showLoading() {
        b.swipeRefresh.isRefreshing       = false
        b.layoutLoading.visibility        = View.VISIBLE
        showErrorSection(false)
        b.tvEmpty.visibility              = View.GONE
        b.historyContainer.visibility     = View.GONE
    }

    private fun showError(msg: String) {
        b.swipeRefresh.isRefreshing       = false
        b.layoutLoading.visibility        = View.GONE
        showErrorSection(true)
        b.tvError.text                    = msg
        b.tvEmpty.visibility              = View.GONE
        b.historyContainer.visibility     = View.GONE
    }

    fun sendLog(msg: String, level: String = "INFO") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = org.json.JSONObject().apply {
                    put("app", "timesheet"); put("level", level); put("msg", msg)
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/log")
                    .post(body).build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    companion object {
        private const val UPDATE_CH       = "app_update"
        private const val UPDATE_NOTIF_ID = 9001
        private const val REQ_LOCATION       = 101
        private const val REQ_BACKGROUND_LOC = 102
    }
    private fun setupGlowCards() {
        listOf(
            R.id.glow_card_schedule to Color.argb(50, 0x22, 0xd3, 0xee),
            R.id.glow_card_history  to Color.argb(50, 0x2e, 0xe6, 0xa6),
        ).forEach { (id, color) -> findViewById<GlowCardLayout>(id)?.setGlowColor(color) }
    }

    private fun setupNotifyCard() {
        val card = findViewById<GlowCardLayout>(R.id.glow_notify) ?: return
        val dismiss = findViewById<android.widget.TextView>(R.id.btnNotifyDismiss)
        val dismissAction = {
            card.stopPulse()
            card.visibility = android.view.View.GONE
        }
        card.setOnClickListener { dismissAction() }
        dismiss?.setOnClickListener { dismissAction() }
    }

    fun showNotify(msg: String, colorArgb: Int) {
        val card = findViewById<GlowCardLayout>(R.id.glow_notify) ?: return
        val tv   = findViewById<android.widget.TextView>(R.id.tvNotifyMsg) ?: return
        tv.text  = msg
        card.setGlowColor(colorArgb)
        card.visibility = android.view.View.VISIBLE
        card.startPulse()
    }

    private fun showPendingSection(show: Boolean) {
        val glowPending = findViewById<GlowCardLayout>(R.id.glow_pending)
        if (glowPending != null) {
            if (show) {
                glowPending.visibility = android.view.View.VISIBLE
                glowPending.setGlowColor(Color.argb(100, 0xff, 0x7a, 0x78)) // alert red
                glowPending.startPulse()
                // Stop pulse when any child of pendingVerifySection is tapped
                b.pendingVerifySection.setOnTouchListener { _, _ ->
                    glowPending.stopPulse(); false
                }
            } else {
                glowPending.stopPulse()
                glowPending.visibility = android.view.View.GONE
            }
        } else {
            b.pendingVerifySection.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun showErrorSection(show: Boolean) {
        val glowError = findViewById<GlowCardLayout>(R.id.glow_error)
        if (glowError != null) {
            if (show) {
                glowError.visibility = android.view.View.VISIBLE
                glowError.setGlowColor(Color.argb(100, 0xff, 0x7a, 0x78))
                glowError.startPulse()
                // Stop pulse when retry is tapped (existing click listener calls fetchData())
                b.btnRetry.post {
                    val orig = b.btnRetry.tag as? android.view.View.OnClickListener
                    b.btnRetry.setOnClickListener {
                        glowError.stopPulse()
                        glowError.visibility = android.view.View.GONE
                        fetchData()
                    }
                }
            } else {
                glowError.stopPulse()
                glowError.visibility = android.view.View.GONE
            }
        } else {
            b.layoutError.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

}
