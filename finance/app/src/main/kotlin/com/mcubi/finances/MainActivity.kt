package com.mcubi.finances

import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mcubi.finances.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://finances.mcubittbuilders.workers.dev"
// Home-hub sidecar (same local/Tailscale channel the PhotoSync client uses). The dashboard reads
// the finance card from the hub, so it stays live with no cloud dependency.
private const val HUB_SIDECAR_FINANCE = "http://100.126.58.18:8767/sidecar/finance"

// ─── Categories ───────────────────────────────────────────────────────────────

private val CATEGORIES_OUT = FinanceCategories.expense
private val CATEGORIES_IN = FinanceCategories.income

// ─── OCR result ───────────────────────────────────────────────────────────────

private data class EntryToSave(
    val amount: Double,
    val description: String,
    val category: String,
    val date: LocalDate
)

private data class ScannedAmount(
    val amount: Double,
    val suggestedDesc: String,   // text on the same line as the amount (minus the number/currency)
    val date: LocalDate?         // date extracted from the full document, shared across all entries
)

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()

    private var updateInProgress = false
    private lateinit var db: FinanceDb

    private var direction    = "out"   // "in" or "out"
    private var selectedCat  = ""
    private var periodStart  = LocalDate.now()
    private var selectedDate = LocalDate.now()
    private var salaryDates  = listOf<LocalDate>()  // sorted ascending, one per salary entry date
    private var currentHistoryEntries = JSONArray()

    companion object {
        private const val UPDATE_CH       = "app_update"
        private const val UPDATE_NOTIF_ID = 9001

        fun fallbackPeriodStart(): LocalDate {
            val today = LocalDate.now()
            return if (today.dayOfMonth >= 10) today.withDayOfMonth(10)
                   else today.minusMonths(1).withDayOfMonth(10)
        }
    }

    private val periodEnd get(): LocalDate {
        val next = salaryDates.firstOrNull { it > periodStart }
        return when {
            next != null -> next.minusDays(1)                          // up to day before next salary
            salaryDates.any { it == periodStart } -> LocalDate.now()   // current (latest) salary period
            else -> periodStart.plusMonths(1).minusDays(1)             // manual month navigation
        }
    }

    private val catButtons = mutableListOf<Pair<String, TextView>>()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) processReceiptImage(uri) }

    private val editEntryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) refreshCurrentTransactionTab()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        db = FinanceDb(this)

        // Tab buttons
        b.btnTabAdd.setOnClickListener     { showTab(0) }
        b.btnTabHistory.setOnClickListener { showTab(1) }
        b.btnTabSummary.setOnClickListener { showTab(2) }

        // Direction toggle
        b.btnDirIn.setOnClickListener  { setDirection("in") }
        b.btnDirOut.setOnClickListener { setDirection("out") }

        // Category buttons
        buildCategoryButtons()

        // Date field — init to today, open picker on tap
        updateDateDisplay()
        b.tvDate.setOnClickListener { showDatePicker() }

        // Save
        b.btnSave.setOnClickListener { saveEntry() }

        // OCR scan
        b.btnScanReceipt.setOnClickListener { openImagePicker() }

        // Period nav: salary-to-salary where possible, monthly fallback otherwise
        b.btnPrevMonth.setOnClickListener {
            val idx = salaryDates.indexOf(periodStart)
            periodStart = when {
                idx > 0  -> salaryDates[idx - 1]   // step to previous salary period
                else     -> periodStart.minusMonths(1)  // go back one month (pre-salary history)
            }
            fetchHistory()
        }
        b.btnNextMonth.setOnClickListener {
            val idx = salaryDates.indexOf(periodStart)
            periodStart = when {
                idx >= 0 && idx < salaryDates.size - 1 -> salaryDates[idx + 1]  // next salary period
                idx == -1 -> {
                    // In manual month mode — advance one month but cap at last salary date
                    val next = periodStart.plusMonths(1)
                    val firstSalaryAfter = salaryDates.firstOrNull { it >= next }
                    firstSalaryAfter ?: next
                }
                else -> periodStart  // already at current period, don't advance
            }
            fetchHistory()
        }
        b.etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredHistoryEntries()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Swipe refresh
        b.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#22d3ee"), Color.parseColor("#FFB300")
        )
        b.swipeRefresh.setOnRefreshListener {
            if (b.layoutHistory.visibility == View.VISIBLE) fetchHistory()
            else { b.swipeRefresh.isRefreshing = false }
            fetchBalancePill()
            fetchSavingsTotal()
        }

        setDirection("out")
        showTab(0)
        initPeriodFromSalaries()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) on ${android.os.Build.MODEL}")
        createUpdateChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001)
        checkForUpdate()
    }

    // ── OTA update ───────────────────────────────────────────────────────────

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
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/version/finance")
                    .build()).execute()
                if (!resp.isSuccessful) return@launch
                val json = JSONObject(resp.body?.string() ?: return@launch)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl     = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    downloadAndNotify(serverCode, apkUrl)
                } else {
                    // Already up to date — clear any stale "update ready" notification.
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
                        .setContentTitle("Finance update ready")
                        .setContentText("Build $buildNum downloaded — tap to install")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) { updateInProgress = false }
        }
    }


    // ── Date picker ───────────────────────────────────────────────────────────

    private fun updateDateDisplay() {
        val today = LocalDate.now()
        b.tvDate.text = when (selectedDate) {
            today            -> "Today  ·  ${selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))}"
            today.minusDays(1) -> "Yesterday  ·  ${selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))}"
            else             -> selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            R.style.DatePickerTheme,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                updateDateDisplay()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    // ── OCR receipt scanning ──────────────────────────────────────────────────

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun processReceiptImage(uri: Uri) {
        flash("Scanning…", "#22d3ee")
        try {
            val image = InputImage.fromFilePath(this, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText ->
                    // DEBUG: ship OCR lines + date extraction results to portal log
                    sendLog("=== OCR SCAN ===")
                    visionText.textBlocks.flatMap { it.lines }.forEachIndexed { i, line ->
                        val dateFound = extractDateFromText(line.text)
                        sendLog("line[$i]: '${line.text}'  date=$dateFound")
                    }
                    sendLog("fullText(500): ${visionText.text.take(500)}")
                    val entries = extractScannedAmounts(visionText)
                    when {
                        entries.isEmpty() -> showRawOcrDialog(visionText.text)
                        entries.size == 1 -> {
                            val e = entries[0]
                            b.etAmount.setText(formatAmountForField(e.amount))
                            if (e.suggestedDesc.isNotEmpty()) b.etWhat.setText(e.suggestedDesc)
                            e.date?.let { selectedDate = it; updateDateDisplay() }
                            flash("Filled from scan", "#22d3ee")
                        }
                        else -> showAmountPickerDialog(entries)
                    }
                }
                .addOnFailureListener { flash("Scan failed", "#FF1744") }
        } catch (e: Exception) {
            flash("Scan failed", "#FF1744")
        }
    }

    // Walk ML Kit's line structure: for each line containing an amount, capture the amount
    // and the best available description (same line text, or the line above if same line is empty).
    private fun extractScannedAmounts(visionText: Text): List<ScannedAmount> {
        val allLines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }
        val lineDates = allLines.map { extractDateFromText(it) }
        val globalDate = lineDates.firstOrNull { it != null } ?: extractDateFromText(visionText.text)

        // Collect ordered list of date line indices and ordered list of (lineIdx, amount) pairs
        val dateLineIndices = allLines.indices.filter { lineDates[it] != null }
        val seen = mutableSetOf<Double>()
        val amountLines = mutableListOf<Pair<Int, Double>>()
        for (i in allLines.indices) {
            for (amount in rawAmountsIn(allLines[i])) {
                if (seen.add(amount)) amountLines.add(i to amount)
            }
        }

        // Zip by position: amount[n] gets date[n]. Works for both activity lists and single receipts.
        val results = amountLines.mapIndexed { idx, (amtIdx, amount) ->
            val dateIdx = dateLineIndices.getOrNull(idx)
            val date = if (dateIdx != null) lineDates[dateIdx] else globalDate
            // Description: prefer same-line text; fall back to line before the matched date line
            val sameLineDesc = descFromLine(allLines[amtIdx])
            val desc = when {
                sameLineDesc.isNotEmpty() -> sameLineDesc
                dateIdx != null && dateIdx > 0 && rawAmountsIn(allLines[dateIdx - 1]).isEmpty() ->
                    allLines[dateIdx - 1].trim()
                amtIdx > 0 && rawAmountsIn(allLines[amtIdx - 1]).isEmpty() ->
                    allLines[amtIdx - 1].trim()
                else -> ""
            }
            ScannedAmount(amount, desc, date)
        }.toMutableList()

        // Fallback: full-text scan for anything the line-by-line pass missed
        for (amount in rawAmountsIn(visionText.text)) {
            if (seen.add(amount)) results.add(ScannedAmount(amount, "", globalDate))
        }

        sendLog("amounts: ${amountLines.size}  dates: ${dateLineIndices.size}  global=$globalDate")
        results.forEach { sendLog("  → ${it.amount}  date=${it.date}  desc='${it.suggestedDesc}'") }

        return results.sortedByDescending { it.amount }
    }

    // Returns all currency amounts found in a string
    private fun rawAmountsIn(text: String): List<Double> {
        val found = mutableSetOf<Double>()
        val suffix  = Regex("""(\d[\d.,]*)\s*(?:₫|đ|VNĐ|VND|RM|SGD)""", RegexOption.IGNORE_CASE)
        val prefix  = Regex("""(?:₫|VND|VNĐ|RM|SGD|\$|€|£)\s*(\d[\d.,]*)""", RegexOption.IGNORE_CASE)
        val plain   = Regex("""(?<![/:\d])(\d{1,3}(?:[.,]\d{3})+)(?![/:\d])""")

        fun stripSep(s: String) = s.replace(".", "").replace(",", "")
        fun parseVnd(s: String) = stripSep(s).toDoubleOrNull()?.takeIf { it >= 1000 }
        fun parseStd(s: String): Double? {
            val cleaned = if (s.matches(Regex(""".*[.,]\d{3}"""))) stripSep(s) else s.replace(",", "")
            return cleaned.toDoubleOrNull()?.takeIf { it > 0 }
        }

        for (m in suffix.findAll(text)) parseVnd(m.groupValues[1])?.let { found.add(it) }
        for (m in prefix.findAll(text)) parseStd(m.groupValues[1])?.let { found.add(it) }
        for (m in plain.findAll(text))  parseVnd(m.groupValues[1])?.let { found.add(it) }
        return found.toList()
    }

    // Strip numbers and currency markers from a line, leaving the description text
    private fun descFromLine(line: String): String =
        line
            .replace(Regex("""[\d.,]+\s*(?:₫|đ|VNĐ|VND|RM|SGD)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:₫|VND|VNĐ|RM|SGD|\$|€|£)\s*[\d.,]+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?<![/:\d])\d{1,3}(?:[.,]\d{3})+(?![/:\d])"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    // Extract the first recognisable date from OCR text — tries DD/MM/YYYY, ISO, and long forms
    private fun extractDateFromText(rawText: String): LocalDate? {
        // Normalize: collapse Unicode whitespace (U+00A0, thin spaces, etc.) to plain ASCII space
        val text = rawText.replace(Regex("\\p{Z}+"), " ")
        val monthMap = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
        fun safe(y: Int, m: Int, d: Int) = try {
            if (m in 1..12 && d in 1..31 && y in 2000..2099) LocalDate.of(y, m, d) else null
        } catch (_: Exception) { null }

        fun fixYear(y: Int) = if (y in 0..99) 2000 + y else y

        // DD/MM/YYYY or DD/MM/YY  (SEA standard — try 4-digit year first)
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""").findAll(text).forEach {
            val y = fixYear(it.groupValues[3].toInt())
            safe(y, it.groupValues[2].toInt(), it.groupValues[1].toInt())?.let { d -> return d }
        }
        // YYYY-MM-DD (ISO)
        Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""").find(text)?.let {
            val y = it.groupValues[1].toInt()
            if (y in 2000..2099)
                safe(y, it.groupValues[2].toInt(), it.groupValues[3].toInt())?.let { d -> return d }
        }
        // Vietnamese: "24 tháng 5 2026" / "24 tháng 5, 2026" / "24 Th5 2026"
        // Also handles OCR misreads like "thang", "Th 5", "th5"
        Regex("""(\d{1,2})\s+[Tt]h(?:áng|ang|\.?)?\s*(\d{1,2})[,\s]+(?:năm\s+)?(\d{2,4})""").find(text)?.let {
            val y = fixYear(it.groupValues[3].toInt())
            safe(y, it.groupValues[2].toInt(), it.groupValues[1].toInt())?.let { d -> return d }
        }
        // Vietnamese without year: "24 tháng 5" — use current year
        Regex("""(\d{1,2})\s+[Tt]h(?:áng|ang|\.?)?\s*(\d{1,2})""").find(text)?.let {
            val mo = it.groupValues[2].toInt()
            val dy = it.groupValues[1].toInt()
            safe(LocalDate.now().year, mo, dy)?.let { d -> return d }
        }
        // "22 May 2026" / "22 May, 2026" / "22May2026" — permissive spacing
        val engWithYear = Regex("""(\d{1,2})\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s,.]+(\d{2,4})""",
            RegexOption.IGNORE_CASE)
        engWithYear.find(text)?.let {
            val mo = monthMap[it.groupValues[2].lowercase().take(3)] ?: return@let
            val y  = fixYear(it.groupValues[3].toInt())
            safe(y, mo, it.groupValues[1].toInt())?.let { d -> return d }
        }
        // "May 22, 2026" / "May 22 2026"
        Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2})[\s,.]+(\d{2,4})""",
            RegexOption.IGNORE_CASE).find(text)?.let {
            val mo = monthMap[it.groupValues[1].lowercase().take(3)] ?: return@let
            val y  = fixYear(it.groupValues[3].toInt())
            safe(y, mo, it.groupValues[2].toInt())?.let { d -> return d }
        }
        // "22 May" without year — use current year (fallback)
        val engNoYear = Regex("""(\d{1,2})\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*""",
            RegexOption.IGNORE_CASE)
        engNoYear.find(text)?.let {
            val mo = monthMap[it.groupValues[2].lowercase().take(3)] ?: return@let
            safe(LocalDate.now().year, mo, it.groupValues[1].toInt())?.let { d -> return d }
        }
        return null
    }

    // Shows raw OCR text when no amounts are parsed — helps debug what ML Kit actually read
    private fun showRawOcrDialog(raw: String) {
        val dim = Color.parseColor("#646464")
        val tv = TextView(this).apply {
            text = if (raw.isBlank()) "(no text detected)" else raw
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(dim)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val sv = ScrollView(this).also { it.addView(tv) }
        android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setTitle("NO AMOUNTS FOUND")
            .setMessage("OCR read the following text:")
            .setView(sv)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatAmountForField(amount: Double): String =
        if (amount == Math.floor(amount)) amount.toLong().toString()
        else "%.2f".format(amount)

    private fun showAmountPickerDialog(entries: List<ScannedAmount>) {
        // Per-row mutable state — local class so lambdas can read/write properties
        class RowState(val amount: Double, suggestedDesc: String, defaultCat: String, defaultDate: LocalDate) {
            var cat      = defaultCat
            var date     = defaultDate
            var selected = true
            // UI refs filled in after the row is built
            var etDesc: EditText? = null
            var catTv: TextView? = null
            var dateTv: TextView? = null
            var tick: TextView? = null
            var card: LinearLayout? = null
            var amtTv: TextView? = null
            val initDesc = suggestedDesc
        }

        val sky     = Color.parseColor("#22d3ee")
        val selBg   = Color.parseColor("#0a1322")
        val unselBg = Color.parseColor("#1A1A1A")
        val fieldBg = Color.parseColor("#0A0A0A")
        val dim     = Color.parseColor("#646464")
        val gold    = Color.parseColor("#FFB300")
        val cats    = if (direction == "in") CATEGORIES_IN else CATEGORIES_OUT
        val defCat  = if (selectedCat.isNotEmpty()) selectedCat else ""
        val sharedDate = entries.firstOrNull()?.date ?: selectedDate

        val rowStates = entries.map { e ->
            RowState(e.amount, e.suggestedDesc, defCat, e.date ?: sharedDate)
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        // Direction label
        outer.addView(TextView(this).apply {
            text = if (direction == "in") "▲  MONEY IN" else "▼  MONEY OUT"
            textSize = 10f; typeface = Typeface.MONOSPACE
            setTextColor(if (direction == "in") Color.parseColor("#00E676") else Color.parseColor("#FF1744"))
            setPadding(0, 0, 0, dp(10))
        })

        for (state in rowStates) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(selBg)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(10) }
            }

            // ── Amount header row — tap to toggle ────────────────────────────
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(10) }
            }
            val tick = TextView(this).apply {
                text = "✓"; textSize = 13f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setTextColor(sky)
                layoutParams = LinearLayout.LayoutParams(dp(22), WRAP)
            }
            val amtTv = TextView(this).apply {
                text = fmt(state.amount); textSize = 16f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setTextColor(sky)
            }
            headerRow.addView(tick); headerRow.addView(amtTv)
            card.addView(headerRow)
            headerRow.setOnClickListener {
                state.selected = !state.selected
                card.setBackgroundColor(if (state.selected) selBg else unselBg)
                tick.text = if (state.selected) "✓" else "  "
                amtTv.setTextColor(if (state.selected) sky else dim)
            }

            // ── Description ──────────────────────────────────────────────────
            addFieldLabel("DESCRIPTION", outer = card)
            val etDesc = EditText(this).apply {
                setText(state.initDesc); hint = "e.g. Lunch, Grab ride…"
                textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE); setHintTextColor(dim)
                setBackgroundColor(fieldBg); setPadding(dp(8), dp(7), dp(8), dp(7))
                inputType = InputType.TYPE_CLASS_TEXT
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(7) }
            }
            card.addView(etDesc)

            // ── Category dropdown ─────────────────────────────────────────────
            addFieldLabel("CATEGORY", outer = card)
            val catTv = TextView(this).apply {
                text = if (state.cat.isEmpty()) "tap to select…" else state.cat
                textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(if (state.cat.isEmpty()) dim else sky)
                setBackgroundColor(fieldBg); setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(7) }
            }
            catTv.setOnClickListener {
                val arr = cats.toTypedArray()
                val cur = arr.indexOf(state.cat).coerceAtLeast(0)
                android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
                    .setTitle("Category")
                    .setSingleChoiceItems(arr, cur) { dlg, idx ->
                        state.cat = arr[idx]; catTv.text = state.cat; catTv.setTextColor(sky)
                        dlg.dismiss()
                    }.show()
            }
            card.addView(catTv)

            // ── Date ─────────────────────────────────────────────────────────
            addFieldLabel("DATE", outer = card)
            val dateTv = TextView(this).apply {
                textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(gold)
                setBackgroundColor(fieldBg); setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            fun refreshDate() {
                dateTv.text = state.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
            }
            refreshDate()
            dateTv.setOnClickListener {
                DatePickerDialog(this, R.style.DatePickerTheme,
                    { _, y, m, d -> state.date = LocalDate.of(y, m + 1, d); refreshDate() },
                    state.date.year, state.date.monthValue - 1, state.date.dayOfMonth
                ).show()
            }
            card.addView(dateTv)

            outer.addView(card)

            // Store UI refs
            state.etDesc = etDesc; state.catTv = catTv; state.dateTv = dateTv
            state.tick = tick; state.card = card; state.amtTv = amtTv
        }

        val sv = ScrollView(this).also { it.addView(outer) }

        android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setTitle("${entries.size} AMOUNTS FOUND")
            .setView(sv)
            .setPositiveButton("SAVE SELECTED") { _, _ ->
                val toSave = rowStates.filter { it.selected }
                if (toSave.isEmpty()) {
                    Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (toSave.any { it.cat.isEmpty() }) {
                    Toast.makeText(this, "Pick a category for each selected entry", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                saveBatchFromDialog(toSave.map { s ->
                    EntryToSave(
                        s.amount,
                        s.etDesc!!.text.toString().trim().ifEmpty { "Receipt" },
                        s.cat,
                        s.date
                    )
                })
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun addFieldLabel(label: String, outer: LinearLayout) {
        outer.addView(TextView(this).apply {
            text = label; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#646464")); letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = dp(2); it.bottomMargin = dp(3) }
        })
    }

    private fun saveBatchFromDialog(entries: List<EntryToSave>) {
        flash("Saving ${entries.size} entries…", "#22d3ee")
        CoroutineScope(Dispatchers.IO).launch {
            var savedCount = 0
            for (entry in entries) {
                try {
                    val body = JSONObject().apply {
                        put("direction", direction)
                        put("amount",    entry.amount)
                        put("what",      entry.description)
                        put("category",  entry.category)
                        put("date",      entry.date.toString())
                    }.toString().toRequestBody("application/json".toMediaType())
                    val resp = client.newCall(
                        Request.Builder().url("$BASE_URL/api/add").post(body).build()
                    ).execute()
                    if (resp.isSuccessful) {
                        try {
                            val j  = JSONObject(resp.body?.string() ?: "")
                            val id = j.optInt("id", 0)
                            val ts = j.optLong("ts", System.currentTimeMillis())
                            if (id > 0) db.insertEntry(id, ts, direction, entry.amount, entry.description, entry.category)
                        } catch (_: Exception) {}
                        savedCount++
                    }
                } catch (_: Exception) {}
            }
            val count = savedCount
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    flash("✓ $count saved", "#00E676")
                    b.etAmount.text?.clear(); b.etWhat.text?.clear()
                    selectedCat = ""; selectedDate = LocalDate.now()
                    buildCategoryButtons(); updateDateDisplay()
                    fetchBalancePill(); fetchSavingsTotal()
                } else {
                    flash("Failed to save", "#FF1744")
                }
            }
        }
    }

    // ── Direction toggle ──────────────────────────────────────────────────────

    private fun setDirection(dir: String) {
        direction = dir
        if (dir == "in") {
            b.btnDirIn.setBackgroundResource(R.drawable.btn_in_active)
            b.btnDirIn.setTextColor(Color.parseColor("#00E676"))
            b.btnDirOut.setBackgroundResource(R.drawable.btn_out_inactive)
            b.btnDirOut.setTextColor(Color.parseColor("#646464"))
        } else {
            b.btnDirIn.setBackgroundResource(R.drawable.btn_in_inactive)
            b.btnDirIn.setTextColor(Color.parseColor("#646464"))
            b.btnDirOut.setBackgroundResource(R.drawable.btn_out_active)
            b.btnDirOut.setTextColor(Color.parseColor("#FF1744"))
        }
        selectedCat = ""
        buildCategoryButtons()
    }

    // ── Category buttons ──────────────────────────────────────────────────────

    private fun buildCategoryButtons() {
        val rows = listOf(b.catRow1, b.catRow2, b.catRow3)
        rows.forEach { it.removeAllViews() }
        catButtons.clear()

        val cats = if (direction == "in") CATEGORIES_IN else CATEGORIES_OUT
        val sky  = Color.parseColor("#22d3ee")
        val dim  = Color.parseColor("#646464")

        // Split cats into rows of 3
        cats.chunked(3).forEachIndexed { rowIdx, rowCats ->
            val row = rows.getOrNull(rowIdx) ?: return@forEachIndexed
            for (cat in rowCats) {
                val btn = TextView(this).apply {
                    text        = cat
                    textSize    = 10f
                    typeface    = Typeface.MONOSPACE
                    setTextColor(dim)
                    gravity     = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setBackgroundResource(R.drawable.cat_btn_inactive)
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).also {
                        it.setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                    setOnClickListener { selectCat(cat) }
                }
                catButtons.add(cat to btn)
                row.addView(btn)
            }
        }
    }

    private fun selectCat(cat: String) {
        selectedCat = cat
        if (b.etWhat.text.isNullOrBlank()) b.etWhat.setText(cat)
        val sky = Color.parseColor("#22d3ee")
        val dim = Color.parseColor("#646464")
        catButtons.forEach { (c, btn) ->
            if (c == cat) {
                btn.setBackgroundResource(R.drawable.cat_btn_active)
                btn.setTextColor(Color.parseColor("#060a12"))
            } else {
                btn.setBackgroundResource(R.drawable.cat_btn_inactive)
                btn.setTextColor(dim)
            }
        }
    }

    // ── Save entry ────────────────────────────────────────────────────────────

    private fun saveEntry() {
        val amountStr = b.etAmount.text.toString().trim()
        val what      = b.etWhat.text.toString().trim()

        if (amountStr.isEmpty() || amountStr.toDoubleOrNull() == null || amountStr.toDouble() <= 0) {
            flash("Enter a valid amount", "#FF1744"); return
        }
        if (what.isEmpty()) {
            flash("Describe what it was", "#FF1744"); return
        }
        if (selectedCat.isEmpty()) {
            flash("Pick a category", "#FF1744"); return
        }

        val amount = amountStr.toDouble()
        val cat    = selectedCat
        val dir    = direction
        val date   = selectedDate
        val isSalaryEntry = dir == "in" && cat == "Salary"
        val prevPeriodStart = periodStart
        // Local timestamp that lands on the chosen day (noon UTC), or "now" if it's today.
        val ts = if (date == LocalDate.now()) System.currentTimeMillis()
                 else date.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

        CoroutineScope(Dispatchers.IO).launch {
            // 1) Save locally first — the local DB is the source of truth, so this works
            //    with no internet at all. The entry is marked unsynced (synced=0).
            val localId = db.insertLocalEntry(ts, dir, amount, what, cat)
            withContext(Dispatchers.Main) {
                flash("✓ saved", "#00E676")
                b.etAmount.text?.clear()
                b.etWhat.text?.clear()
                selectedCat = ""
                selectedDate = LocalDate.now()
                buildCategoryButtons()
                updateDateDisplay()
                if (isSalaryEntry) handleSalaryEntry(date, prevPeriodStart)
                else { fetchBalancePill(); fetchSavingsTotal() }
            }
            // 2) Best-effort push to the cloud. If it fails (offline / server 500) the entry
            //    stays queued and is retried later by syncPendingEntries().
            pushEntry(localId, ts, dir, amount, what, cat, date)
        }
    }

    /** Push one locally-saved entry to the cloud; on success swap in the server id. */
    private fun pushEntry(localId: Int, ts: Long, dir: String, amount: Double,
                          what: String, cat: String, date: LocalDate) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("direction", dir); put("amount", amount); put("what", what)
                    put("category", cat); put("date", date.toString())
                }.toString().toRequestBody("application/json".toMediaType())
                val resp = client.newCall(Request.Builder().url("$BASE_URL/api/add").post(body).build()).execute()
                if (resp.isSuccessful) {
                    val j = JSONObject(resp.body?.string() ?: "")
                    val serverId = j.optInt("id", 0)
                    val serverTs = j.optLong("ts", ts)
                    if (serverId > 0) db.markEntrySynced(localId, serverId, serverTs, dir, amount, what, cat)
                }
            } catch (_: Exception) {}   // stays queued (synced=0)
        }
    }

    /** Retry pushing every entry that was added while offline. Stops on the first failure
     *  (e.g. server still down) and tries again on the next sync. */
    private fun syncPendingEntries() {
        CoroutineScope(Dispatchers.IO).launch {
            val pending = db.getUnsyncedEntries()
            for (i in 0 until pending.length()) {
                val e = pending.getJSONObject(i)
                val localId = e.getInt("id")
                val ts      = e.getLong("ts")
                val dir     = e.getString("direction")
                val amount  = e.getDouble("amount")
                val what    = e.getString("what")
                val cat     = e.getString("category")
                val date    = java.time.Instant.ofEpochMilli(ts)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                try {
                    val body = JSONObject().apply {
                        put("direction", dir); put("amount", amount); put("what", what)
                        put("category", cat); put("date", date.toString())
                    }.toString().toRequestBody("application/json".toMediaType())
                    val resp = client.newCall(Request.Builder().url("$BASE_URL/api/add").post(body).build()).execute()
                    if (resp.isSuccessful) {
                        val j = JSONObject(resp.body?.string() ?: "")
                        val serverId = j.optInt("id", 0)
                        val serverTs = j.optLong("ts", ts)
                        if (serverId > 0) db.markEntrySynced(localId, serverId, serverTs, dir, amount, what, cat)
                    } else return@launch
                } catch (_: Exception) { return@launch }
            }
        }
    }

    private fun sendLog(msg: String, level: String = "INFO") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("app", "finance"); put("level", level); put("msg", msg)
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/log")
                    .post(body).build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun flash(msg: String, colorHex: String) {
        val argb = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.WHITE }
        // Derive aurora-ish alpha-100 glow color from the text color
        val glowColor = Color.argb(100, Color.red(argb), Color.green(argb), Color.blue(argb))
        showNotify(msg, glowColor)
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


    // ── Balance pill ──────────────────────────────────────────────────────────

    private fun fetchBalancePill() {
        // Compute from local DB instantly
        CoroutineScope(Dispatchers.IO).launch {
            val (income, expense) = db.getSummaryForPeriod(periodStart, periodEnd)
            val bal = income - expense
            withContext(Dispatchers.Main) {
                b.tvTopBalance.text = (if (bal >= 0) "+" else "") + fmt(bal)
                b.tvTopBalance.setTextColor(
                    if (bal >= 0) Color.parseColor("#FFB300") else Color.parseColor("#FF1744")
                )
            }
            pushFinanceToHub()
        }
    }

    // Mirror the CURRENT salary-period snapshot to the home hub so the dashboard's finance card
    // shows live data over the local network (and the last value when the phone is offline).
    private fun pushFinanceToHub() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val curStart = db.getSalaryDates().lastOrNull()
                val (income, expense) = db.getSummaryForPeriod(curStart, LocalDate.now())
                val savings = db.getSavingsTotal()
                val label = if (curStart != null)
                    curStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase() + " TO NOW"
                else "ALL TIME"
                val json = JSONObject().apply {
                    put("periodLabel", label)
                    put("income", income)
                    put("expense", expense)
                    put("balance", income - expense)
                    put("savings", savings)
                    put("updatedAt", System.currentTimeMillis())
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder().url(HUB_SIDECAR_FINANCE).post(json).build())
                    .execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun fetchSavingsTotal() {
        // Show local cache instantly, then refresh from server
        CoroutineScope(Dispatchers.IO).launch {
            val local = db.getSavingsTotal()
            withContext(Dispatchers.Main) { updateSavingsDisplay(local) }
            // Only trust the cloud value when we're fully synced. If there are entries still
            // queued offline, the local savings includes settlements the cloud doesn't know
            // about yet — don't let a stale cloud value overwrite it.
            if (db.getUnsyncedEntries().length() > 0) return@launch
            try {
                val resp = client.newCall(Request.Builder().url("$BASE_URL/api/savings").build()).execute()
                if (!resp.isSuccessful) return@launch
                val total = JSONObject(resp.body!!.string()).getDouble("total")
                db.saveSavingsTotal(total)
                withContext(Dispatchers.Main) { updateSavingsDisplay(total) }
            } catch (_: Exception) {}
        }
    }

    private fun updateSavingsDisplay(total: Double) {
        b.tvTopSavings.text = (if (total >= 0) "+" else "") + fmt(total)
        b.tvTopSavings.setTextColor(
            if (total >= 0) Color.parseColor("#00E676") else Color.parseColor("#FF1744")
        )
    }

    // Load salary dates from local DB immediately, then sync all entries from server in background
    private fun initPeriodFromSalaries() {
        // Show local data instantly
        val localDates = db.getSalaryDates()
        salaryDates = localDates
        periodStart = localDates.lastOrNull() ?: fallbackPeriodStart()
        fetchBalancePill()
        fetchSavingsTotal()

        // Push anything added while offline, then sync from server.
        syncPendingEntries()

        // Background sync from server — replaces the synced cache (keeps unsynced local entries)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder().url("$BASE_URL/api/entries").build()).execute()
                if (!resp.isSuccessful) return@launch
                val all  = JSONArray(resp.body!!.string())
                db.replaceAllEntries(all)

                val savingsResp = client.newCall(Request.Builder().url("$BASE_URL/api/savings").build()).execute()
                if (savingsResp.isSuccessful) {
                    val total = JSONObject(savingsResp.body!!.string()).getDouble("total")
                    db.saveSavingsTotal(total)
                }

                val synced = db.getSalaryDates()
                withContext(Dispatchers.Main) {
                    salaryDates = synced
                    periodStart = synced.lastOrNull() ?: fallbackPeriodStart()
                    fetchBalancePill()
                    fetchSavingsTotal()
                }
            } catch (_: Exception) {} // keep showing local data on failure
        }
    }

    // Called after a Salary entry is successfully saved.
    // Computes the previous period balance and posts it to savings.
    private fun handleSalaryEntry(salaryDate: LocalDate, prevPeriodStart: LocalDate) {
        val prevTo = salaryDate.minusDays(1)
        // First salary ever: capture all-time balance up to yesterday (catches historical carryover).
        // Subsequent salaries: capture balance since the previous salary date only.
        val isFirstSalary = salaryDates.isEmpty()
        if (!isFirstSalary && prevPeriodStart.isAfter(prevTo)) {
            // Salary added on same day as previous salary — nothing to settle
            updateSalaryPeriod(salaryDate); fetchBalancePill(); fetchSavingsTotal(); return
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Compute the previous period's balance from the LOCAL DB so settlement works offline.
            val (income, expense) =
                if (isFirstSalary) db.getSummaryForPeriod(null, prevTo)
                else               db.getSummaryForPeriod(prevPeriodStart, prevTo)
            val balance = income - expense

            if (balance != 0.0) {
                // Settle into savings locally (source of truth)…
                db.saveSavingsTotal(db.getSavingsTotal() + balance)
                // …and tell the cloud too, best-effort (ignored when offline).
                try {
                    val note = if (isFirstSalary) "All-time to $prevTo" else "Period $prevPeriodStart to $prevTo"
                    val adjustBody = JSONObject().apply {
                        put("delta", balance); put("note", note)
                    }.toString().toRequestBody("application/json".toMediaType())
                    client.newCall(
                        Request.Builder().url("$BASE_URL/api/savings/adjust").post(adjustBody).build()
                    ).execute().close()
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                updateSalaryPeriod(salaryDate)
                fetchBalancePill()
                fetchSavingsTotal()
            }
        }
    }

    private fun updateSalaryPeriod(salaryDate: LocalDate) {
        salaryDates = (salaryDates + salaryDate).distinct().sorted()
        periodStart = salaryDate
    }

    // ── History ───────────────────────────────────────────────────────────────

    private fun fetchHistory() {
        val from = periodStart.toString()
        val to   = periodEnd.toString()
        val labelFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        val endLabel = if (periodEnd == LocalDate.now()) "NOW" else periodEnd.format(labelFmt).uppercase()
        b.tvMonthLabel.text = "${periodStart.format(labelFmt).uppercase()} — $endLabel"

        // Show local data immediately (works offline)
        CoroutineScope(Dispatchers.IO).launch {
            val localEntries = db.getEntriesForPeriod(periodStart, periodEnd)
            val (localIn, localOut) = db.getSummaryForPeriod(periodStart, periodEnd)
            withContext(Dispatchers.Main) {
                b.layoutLoading.visibility = View.GONE
                renderHistorySummary(localIn, localOut)
                renderHistoryEntries(localEntries)
            }
        }

        // Background sync from server — updates local cache and refreshes display
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(
                    Request.Builder().url("$BASE_URL/api/entries").build()
                ).execute()
                if (!resp.isSuccessful) { withContext(Dispatchers.Main) { b.swipeRefresh.isRefreshing = false }; return@launch }
                val all = JSONArray(resp.body!!.string())
                db.replaceAllEntries(all)

                val entries = db.getEntriesForPeriod(periodStart, periodEnd)
                val (inc, exp) = db.getSummaryForPeriod(periodStart, periodEnd)
                withContext(Dispatchers.Main) {
                    b.swipeRefresh.isRefreshing = false
                    b.layoutLoading.visibility  = View.GONE
                    renderHistorySummary(inc, exp)
                    renderHistoryEntries(entries)
                    fetchBalancePill()
                    fetchSavingsTotal()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { b.swipeRefresh.isRefreshing = false }
            }
        }
    }

    private fun renderHistorySummary(income: Double, expense: Double) {
        val bal = income - expense
        b.tvSumIn.text  = fmt(income)
        b.tvSumOut.text = fmt(expense)
        b.tvSumBal.text = fmt(bal)
        b.tvSumBal.setTextColor(if (bal >= 0) Color.parseColor("#FFB300") else Color.parseColor("#FF1744"))
    }

    private fun renderHistoryEntries(entries: JSONArray) {
        currentHistoryEntries = entries
        renderFilteredHistoryEntries()
    }

    private fun renderFilteredHistoryEntries() {
        val query = b.etHistorySearch.text.toString().trim().lowercase(Locale.getDefault())
        val entries = if (query.isEmpty()) currentHistoryEntries else JSONArray().also { filtered ->
            for (i in 0 until currentHistoryEntries.length()) {
                val entry = currentHistoryEntries.getJSONObject(i)
                val haystack = buildString {
                    append(entry.optString("what"))
                    append(' ')
                    append(entry.optString("category"))
                    append(' ')
                    append(entry.optDouble("amount"))
                    append(' ')
                    append(
                        java.text.SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
                            .format(java.util.Date(entry.optLong("ts")))
                    )
                }.lowercase(Locale.getDefault())
                if (haystack.contains(query)) filtered.put(entry)
            }
        }
        if (entries.length() == 0) {
            b.tvEmpty.text = if (query.isEmpty()) "no entries this month" else "no matching transactions"
            b.tvEmpty.visibility          = View.VISIBLE
            b.historyContainer.visibility = View.GONE
        } else {
            b.tvEmpty.text = "no entries this month"
            b.tvEmpty.visibility          = View.GONE
            b.historyContainer.visibility = View.VISIBLE
            buildHistoryView(entries)
            b.nestedScroll.post { b.nestedScroll.requestLayout() }
        }
    }

    private fun buildHistoryView(entries: JSONArray) {
        val container = b.historyContainer
        container.removeAllViews()

        val sky    = Color.parseColor("#22d3ee")
        val gold   = Color.parseColor("#FFB300")
        val green  = Color.parseColor("#00E676")
        val red    = Color.parseColor("#FF1744")
        val cardBg = Color.parseColor("#0a1322")
        val dim    = Color.parseColor("#646464")

        val localDateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val byDate = linkedMapOf<String, MutableList<JSONObject>>()
        for (i in 0 until entries.length()) {
            val e    = entries.getJSONObject(i)
            val ts   = e.getLong("ts")
            val date = localDateFmt.format(java.util.Date(ts))
            byDate.getOrPut(date) { mutableListOf() }.add(e)
        }

        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        for ((date, dayEntries) in byDate) {
            val parsedDate  = LocalDate.parse(date)
            val isToday     = parsedDate == LocalDate.now()
            val dayLabel    = parsedDate.format(dateFmt).uppercase()
            val dayNet      = dayEntries.sumOf { e ->
                val amt = e.getDouble("amount")
                if (e.getString("direction") == "in") amt else -amt
            }
            val headerColor = if (isToday) gold else sky

            // ── Day header ──────────────────────────────────────────────────
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(cardBg)
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(36)).also {
                    it.setMargins(dp(12), dp(10), dp(12), 0)
                }
            }
            headerRow.addView(View(this).apply {
                setBackgroundColor(headerColor)
                layoutParams = LinearLayout.LayoutParams(dp(3), MATCH)
            })
            headerRow.addView(TextView(this).apply {
                text          = if (isToday) "▶  $dayLabel  · TODAY" else "   $dayLabel"
                textSize      = 11f
                typeface      = Typeface.MONOSPACE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(headerColor)
                letterSpacing = 0.06f
                gravity       = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams  = LinearLayout.LayoutParams(0, MATCH, 1f)
            })
            headerRow.addView(TextView(this).apply {
                text     = (if (dayNet >= 0) "+" else "") + fmt(dayNet)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (dayNet >= 0) gold else red)
                gravity  = Gravity.CENTER_VERTICAL
                setPadding(0, 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(WRAP, MATCH)
            })
            container.addView(headerRow)

            // ── Entry cards ─────────────────────────────────────────────────
            for (e in dayEntries) {
                val isIn        = e.getString("direction") == "in"
                val amountColor = if (isIn) green else red
                val amount      = e.getDouble("amount")
                val what        = e.getString("what")
                val cat         = e.getString("category")
                val ts          = e.getLong("ts")
                val jDate       = java.util.Date(ts)
                val dateLine    = java.text.SimpleDateFormat("EEE d MMM yyyy", Locale.ENGLISH).format(jDate)
                val time        = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(jDate)

                val card = LinearLayout(this).apply {
                    orientation  = LinearLayout.HORIZONTAL
                    setBackgroundColor(cardBg)
                    isLongClickable = true
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                        it.setMargins(dp(12), dp(2), dp(12), 0)
                    }
                    setOnLongClickListener { showEntryActions(e); true }
                }

                // Left accent stripe — explicit height so it renders
                card.addView(View(this).apply {
                    setBackgroundColor(sky)
                    layoutParams = LinearLayout.LayoutParams(dp(3), dp(68))
                })

                // Left column: what / category / date·time
                val leftCol = LinearLayout(this).apply {
                    orientation  = LinearLayout.VERTICAL
                    gravity      = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(8), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                }
                leftCol.addView(TextView(this).apply {
                    text     = what
                    textSize = 13f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                leftCol.addView(TextView(this).apply {
                    text     = cat
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(dim)
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(2) }
                })
                leftCol.addView(TextView(this).apply {
                    text     = "$dateLine  ·  $time"
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(dim)
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(2) }
                })

                // Right column: amount
                val rightCol = LinearLayout(this).apply {
                    orientation  = LinearLayout.VERTICAL
                    gravity      = Gravity.END or Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                }
                rightCol.addView(TextView(this).apply {
                    text     = (if (isIn) "+" else "-") + fmt(amount)
                    textSize = 14f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(amountColor)
                    gravity  = Gravity.END
                })

                card.addView(leftCol)
                card.addView(rightCol)
                container.addView(card)
            }

            // Separator
            container.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).also {
                    it.setMargins(dp(12), dp(8), dp(12), 0)
                }
            })
        }

        container.requestLayout()
    }

    // ── Edit / delete ─────────────────────────────────────────────────────────

    private fun showEntryActions(entry: JSONObject) {
        val dialog = android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setTitle(entry.optString("what", "Transaction"))
            .setItems(arrayOf("EDIT", "DELETE")) { _, which ->
                if (which == 0) showEditEntryDialog(entry)
                else confirmDelete(entry.getInt("id"))
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.setOnShowListener { styleFinanceDialog(dialog) }
        dialog.show()
    }

    private fun showEditEntryDialog(entry: JSONObject) {
        editEntryLauncher.launch(Intent(this, QuickAddActivity::class.java).apply {
            putExtra(QuickAddActivity.EXTRA_DIRECTION, entry.getString("direction"))
            putExtra(QuickAddActivity.EXTRA_ENTRY_ID, entry.getInt("id"))
            putExtra(QuickAddActivity.EXTRA_ENTRY_TS, entry.getLong("ts"))
            putExtra(QuickAddActivity.EXTRA_AMOUNT, entry.getDouble("amount"))
            putExtra(QuickAddActivity.EXTRA_WHAT, entry.getString("what"))
            putExtra(QuickAddActivity.EXTRA_CATEGORY, entry.getString("category"))
        })
    }

    private fun styleFinanceDialog(dialog: android.app.AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_bg)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
    }

    private fun refreshCurrentTransactionTab() {
        if (b.layoutSummary.visibility == View.VISIBLE) buildFinanceSummary()
        else fetchHistory()
        fetchBalancePill()
        fetchSavingsTotal()
    }

    private fun confirmDelete(id: Int) {
        val dialog = android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setMessage("Delete this entry?")
            .setPositiveButton("DELETE") { _, _ -> deleteEntry(id) }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.setOnShowListener { styleFinanceDialog(dialog) }
        dialog.show()
    }

    private fun deleteEntry(id: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(
                    Request.Builder().url("$BASE_URL/api/delete/$id").delete().build()
                ).execute().close()
            } catch (_: Exception) {
                // Delete may have succeeded on the server even if the response timed out —
                // always fall through and reload so the list reflects the real state.
            }
            db.deleteEntry(id)
            withContext(Dispatchers.Main) { fetchHistory() }
        }
    }

    // ── Tab management ────────────────────────────────────────────────────────

    // 0 = Add, 1 = History, 2 = Summary
    private fun showTab(tab: Int) {
        val sky = Color.parseColor("#22d3ee")
        val dim = Color.parseColor("#646464")

        b.layoutAdd.visibility     = if (tab == 0) View.VISIBLE else View.GONE
        b.layoutHistory.visibility = if (tab == 1) View.VISIBLE else View.GONE
        b.layoutSummary.visibility = if (tab == 2) View.VISIBLE else View.GONE
        // Hide inactive GlowCardLayout wrappers so their margins disappear
        findViewById<GlowCardLayout>(R.id.glow_card_add)?.visibility     = if (tab == 0) View.VISIBLE else View.GONE
        findViewById<GlowCardLayout>(R.id.glow_card_history)?.visibility = if (tab == 1) View.VISIBLE else View.GONE
        findViewById<GlowCardLayout>(R.id.glow_card_summary)?.visibility = if (tab == 2) View.VISIBLE else View.GONE
        b.btnTabAdd.setTextColor(    if (tab == 0) sky else dim)
        b.btnTabHistory.setTextColor(if (tab == 1) sky else dim)
        b.btnTabSummary.setTextColor(if (tab == 2) sky else dim)
        b.tabIndicatorAdd.visibility     = if (tab == 0) View.VISIBLE else View.INVISIBLE
        b.tabIndicatorHistory.visibility = if (tab == 1) View.VISIBLE else View.INVISIBLE
        b.tabIndicatorSummary.visibility = if (tab == 2) View.VISIBLE else View.INVISIBLE

        if (tab == 1) fetchHistory()
        if (tab == 2) buildFinanceSummary()
    }

    // ── Summary tab: salary-period spending by category ─────────────────────────

    private fun buildFinanceSummary() {
        val container = b.summaryContainer
        container.removeAllViews()
        val mono   = android.graphics.Typeface.MONOSPACE
        val white  = Color.WHITE
        val dim     = Color.parseColor("#969696")
        val green   = Color.parseColor("#00E676")
        val red      = Color.parseColor("#FF1744")
        val gold     = Color.parseColor("#FFB300")
        val cardBg   = Color.parseColor("#0a1322")

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        // Month nav row
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(6))
        }
        val prev = TextView(this).apply {
            text = "‹"; textSize = 24f; typeface = mono; setTextColor(Color.parseColor("#22d3ee"))
            setPadding(dp(22), dp(4), dp(22), dp(4)); isClickable = true
            setOnClickListener {
                val idx = salaryDates.indexOf(periodStart)
                periodStart = if (idx > 0) salaryDates[idx - 1] else periodStart.minusMonths(1)
                buildFinanceSummary()
            }
        }
        val lbl = TextView(this).apply {
            text = "${periodStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – " +
                periodEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            textSize = 14f; typeface = mono; setTextColor(white); setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val next = TextView(this).apply {
            text = "›"; textSize = 24f; typeface = mono; setTextColor(Color.parseColor("#22d3ee"))
            setPadding(dp(22), dp(4), dp(22), dp(4)); isClickable = true
            setOnClickListener {
                val idx = salaryDates.indexOf(periodStart)
                periodStart = when {
                    idx >= 0 && idx < salaryDates.size - 1 -> salaryDates[idx + 1]
                    idx == -1 -> salaryDates.firstOrNull { it >= periodStart.plusMonths(1) }
                        ?: periodStart.plusMonths(1)
                    else -> periodStart
                }
                buildFinanceSummary()
            }
        }
        nav.addView(prev); nav.addView(lbl); nav.addView(next)
        container.addView(nav)
        container.addView(TextView(this).apply {
            text = "SET CATEGORY BUDGETS"
            textSize = 10f
            typeface = mono
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22d3ee"))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.cat_btn_inactive)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(38)).also {
                it.setMargins(dp(12), 0, dp(12), dp(6))
            }
            setOnClickListener { showBudgetCategoryPicker() }
        })

        CoroutineScope(Dispatchers.IO).launch {
            val from = periodStart
            val to   = periodEnd
            val arr  = db.getCategoryBreakdown(from, to)
            val (totalIn, totalOut) = db.getSummaryForPeriod(from, to)
            val rows = ArrayList<Triple<String, Double, Int>>()
            val transactionsByCategory = LinkedHashMap<String, JSONArray>()
            val budgetsByCategory = LinkedHashMap<String, Double>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val category = o.getString("category")
                rows.add(Triple(category, o.getDouble("out"), o.getInt("outCount")))
                transactionsByCategory[category] = db.getCategoryTransactions(from, to, category)
                budgetsByCategory[category] = db.getCategoryBudget(category)
            }
            withContext(Dispatchers.Main) {
                if (rows.isEmpty()) {
                    container.addView(TextView(this@MainActivity).apply {
                        text = "No entries this month"; setTextColor(dim); textSize = 13f; typeface = mono
                        gravity = Gravity.CENTER; setPadding(dp(32), dp(48), dp(32), dp(32))
                    })
                    return@withContext
                }
                // Totals card
                val totalsCard = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(Color.parseColor("#140E00"))
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(dp(12), dp(4), dp(12), dp(8)) }
                }
                fun totCol(label: String, value: Double, color: Int): LinearLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    addView(TextView(this@MainActivity).apply { text = label; setTextColor(dim); textSize = 9f; typeface = mono; letterSpacing = 0.1f })
                    addView(TextView(this@MainActivity).apply { text = fmt(value); setTextColor(color); textSize = 14f; typeface = mono; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                }
                totalsCard.addView(totCol("IN", totalIn, green))
                totalsCard.addView(totCol("OUT", totalOut, red))
                totalsCard.addView(totCol("NET", totalIn - totalOut, gold))
                container.addView(totalsCard)

                // Category rows (expenses emphasised; show count + amount, with a bar)
                val maxOut = rows.maxOf { it.second }.coerceAtLeast(1.0)
                for ((cat, cOut, count) in rows) {
                    val budget = budgetsByCategory[cat] ?: 0.0
                    val card = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(cardBg)
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(dp(12), dp(4), dp(12), 0) }
                        isLongClickable = true
                        setOnLongClickListener {
                            showBudgetDialog(cat)
                            true
                        }
                    }
                    val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    val arrow = TextView(this@MainActivity).apply {
                        text = "›"; setTextColor(dim); textSize = 18f; typeface = mono
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = dp(8) }
                    }
                    top.addView(arrow)
                    top.addView(TextView(this@MainActivity).apply {
                        text = cat; setTextColor(white); textSize = 13f; typeface = mono
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    })
                    top.addView(TextView(this@MainActivity).apply {
                        text = "$count ×"; setTextColor(dim); textSize = 10f; typeface = mono
                        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginEnd = dp(10) }
                    })
                    top.addView(TextView(this@MainActivity).apply {
                        text = fmt(cOut)
                        setTextColor(red); textSize = 13f; typeface = mono
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    card.addView(top)
                    // proportion bar (expense share)
                    val barBg = LinearLayout(this@MainActivity).apply {
                        setBackgroundColor(Color.parseColor("#222222"))
                        layoutParams = LinearLayout.LayoutParams(MATCH, dp(3)).also { it.topMargin = dp(6) }
                    }
                    val fillW = if (budget > 0) {
                        ((cOut / budget) * 100).toInt().coerceIn(2, 100)
                    } else {
                        ((cOut / maxOut) * 100).toInt().coerceIn(2, 100)
                    }
                    barBg.addView(View(this@MainActivity).apply {
                        setBackgroundColor(if (budget > 0 && cOut <= budget) green else red)
                        layoutParams = LinearLayout.LayoutParams(0, MATCH, fillW.toFloat())
                    })
                    barBg.addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, MATCH, (100 - fillW).toFloat())
                    })
                    card.addView(barBg)
                    card.addView(TextView(this@MainActivity).apply {
                        text = when {
                            budget <= 0 -> "LONG PRESS TO SET BUDGET"
                            cOut <= budget -> "${fmt(budget - cOut)} REMAINING OF ${fmt(budget)}"
                            else -> "${fmt(cOut - budget)} OVER ${fmt(budget)} BUDGET"
                        }
                        setTextColor(
                            when {
                                budget <= 0 -> dim
                                cOut <= budget -> green
                                else -> red
                            }
                        )
                        textSize = 9f
                        typeface = mono
                        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also {
                            it.topMargin = dp(5)
                        }
                    })

                    val details = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        visibility = View.GONE
                        setPadding(0, dp(8), 0, 0)
                    }
                    val transactions = transactionsByCategory[cat] ?: JSONArray()
                    for (i in 0 until transactions.length()) {
                        val entry = transactions.getJSONObject(i)
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            isLongClickable = true
                            setPadding(dp(4), dp(7), dp(4), dp(7))
                            if (i > 0) setBackgroundColor(Color.parseColor("#111111"))
                            setOnLongClickListener {
                                showEntryActions(entry)
                                true
                            }
                        }
                        val ts = entry.getLong("ts")
                        val date = java.time.Instant.ofEpochMilli(ts)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            .format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                        row.addView(TextView(this@MainActivity).apply {
                            text = date; setTextColor(dim); textSize = 9f; typeface = mono
                            layoutParams = LinearLayout.LayoutParams(dp(58), WRAP)
                        })
                        row.addView(TextView(this@MainActivity).apply {
                            text = entry.getString("what"); setTextColor(white); textSize = 11f; typeface = mono
                            maxLines = 2
                            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).also {
                                it.marginStart = dp(6); it.marginEnd = dp(8)
                            }
                        })
                        row.addView(TextView(this@MainActivity).apply {
                            text = fmt(entry.getDouble("amount")); setTextColor(red); textSize = 11f; typeface = mono
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                        })
                        details.addView(row)
                    }
                    card.addView(details)
                    card.setOnClickListener {
                        val opening = details.visibility != View.VISIBLE
                        details.visibility = if (opening) View.VISIBLE else View.GONE
                        arrow.text = if (opening) "⌄" else "›"
                    }
                    container.addView(card)
                }
            }
        }
    }

    private fun showBudgetCategoryPicker() {
        val categories = FinanceCategories.expense.toTypedArray()
        val dialog = android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setTitle("CATEGORY BUDGET")
            .setItems(categories) { _, index -> showBudgetDialog(categories[index]) }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.setOnShowListener { styleFinanceDialog(dialog) }
        dialog.show()
    }

    private fun showBudgetDialog(category: String) {
        val input = EditText(this).apply {
            hint = "0 removes budget"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#646464"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val current = db.getCategoryBudget(category)
            if (current > 0) setText(current.toString().removeSuffix(".0"))
            selectAll()
        }
        val wrapper = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val dialog = android.app.AlertDialog.Builder(this, R.style.DatePickerTheme)
            .setTitle("$category BUDGET")
            .setView(wrapper)
            .setPositiveButton("SAVE", null)
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.setOnShowListener {
            styleFinanceDialog(dialog)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = input.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount < 0) {
                    input.error = "Enter 0 or a valid amount"
                    return@setOnClickListener
                }
                db.saveCategoryBudget(category, amount)
                dialog.dismiss()
                buildFinanceSummary()
            }
        }
        dialog.show()
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private val numFmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    private fun fmt(n: Double): String = "₫${numFmt.format(Math.abs(n))}"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun setupGlowCards() {
        listOf(
            R.id.glow_card_add     to Color.argb(100, 0x22, 0xd3, 0xee),
            R.id.glow_card_history to Color.argb(100, 0x2e, 0xe6, 0xa6),
            R.id.glow_card_summary to Color.argb(100, 0xa9, 0x8b, 0xff),
        ).forEach { (id, color) -> findViewById<GlowCardLayout>(id)?.setGlowColor(color) }
    }

}
