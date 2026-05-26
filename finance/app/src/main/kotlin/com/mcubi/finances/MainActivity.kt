package com.mcubi.finances

import android.Manifest
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.InputType
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

// ─── Categories ───────────────────────────────────────────────────────────────

private val CATEGORIES_OUT = listOf(
    "Food & Drink", "Groceries", "Transport", "Bills", "Shopping",
    "Health", "Entertainment", "Rent", "Other"
)
private val CATEGORIES_IN = listOf(
    "Salary", "Freelance", "Gift", "Other Income"
)

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

    companion object {
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickImageLauncher.launch("image/*")
        else flash("Gallery permission denied", "#FF1744")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        db = FinanceDb(this)

        // Tab buttons
        b.btnTabAdd.setOnClickListener     { showTab(add = true) }
        b.btnTabHistory.setOnClickListener { showTab(add = false) }

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

        // Swipe refresh
        b.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#29B6F6"), Color.parseColor("#FFB300")
        )
        b.swipeRefresh.setOnRefreshListener {
            if (b.layoutHistory.visibility == View.VISIBLE) fetchHistory()
            else { b.swipeRefresh.isRefreshing = false }
            fetchBalancePill()
            fetchSavingsTotal()
        }

        setDirection("out")
        showTab(add = true)
        initPeriodFromSalaries()
        sendLog("Started v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        checkForUpdate()
    }

    // ── OTA update ───────────────────────────────────────────────────────────

    private fun checkForUpdate() {
        if (updateInProgress) return
        android.util.Log.d("UpdateCheck", "starting — installed build ${BuildConfig.VERSION_CODE}")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("https://app-updates.mcubittbuilders.workers.dev/api/version/finance")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return@launch
                val body = resp.body?.string() ?: return@launch
                val json = JSONObject(body)
                val serverCode = json.optInt("versionCode", 0)
                val apkUrl     = json.optString("apkUrl", "")
                if (serverCode > BuildConfig.VERSION_CODE && apkUrl.isNotEmpty()) {
                    updateInProgress = true
                    withContext(Dispatchers.Main) { promptInstall(serverCode, apkUrl) }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateCheck", "failed: ${e::class.simpleName}: ${e.message}", e)
            }
        }
    }

    private fun promptInstall(serverCode: Int, apkUrl: String) {
        Snackbar.make(b.root, "Update available (build $serverCode)", Snackbar.LENGTH_INDEFINITE)
            .setAction("INSTALL") { downloadAndInstall(apkUrl) }
            .setActionTextColor(Color.parseColor("#FFB300"))
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Go to Settings → Install unknown apps → enable for this app", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req   = Request.Builder().url(apkUrl).build()
                val resp  = client.newCall(req).execute()
                if (!resp.isSuccessful) return@launch
                val bytes = resp.body?.bytes() ?: return@launch

                val pi      = packageManager.packageInstaller
                val params  = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val sessionId = pi.createSession(params)
                val session   = pi.openSession(sessionId)
                session.openWrite("apk", 0, bytes.size.toLong()).use { out ->
                    out.write(bytes)
                    session.fsync(out)
                }
                val intent  = Intent(this@MainActivity, InstallReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    this@MainActivity, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
            pickImageLauncher.launch("image/*")
        else
            permissionLauncher.launch(permission)
    }

    private fun processReceiptImage(uri: Uri) {
        flash("Scanning…", "#29B6F6")
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
                            flash("Filled from scan", "#29B6F6")
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
        android.app.AlertDialog.Builder(this)
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

        val sky     = Color.parseColor("#29B6F6")
        val selBg   = Color.parseColor("#0D2233")
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
                android.app.AlertDialog.Builder(this)
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

        android.app.AlertDialog.Builder(this)
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
        flash("Saving ${entries.size} entries…", "#29B6F6")
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
        val sky  = Color.parseColor("#29B6F6")
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
        val sky = Color.parseColor("#29B6F6")
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

        val body = JSONObject().apply {
            put("direction", direction)
            put("amount",    amountStr.toDouble())
            put("what",      what)
            put("category",  selectedCat)
            put("date",      selectedDate.toString())
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url("$BASE_URL/api/add").post(body).build()
        val isSalaryEntry = direction == "in" && selectedCat == "Salary"
        val prevPeriodStart = periodStart
        val salaryDate = selectedDate

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(request).execute()
                val respBody = resp.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    if (resp.isSuccessful) {
                        // Cache locally using the server-assigned id
                        try {
                            val j = JSONObject(respBody)
                            val newId = j.optInt("id", 0)
                            val ts    = j.optLong("ts", System.currentTimeMillis())
                            if (newId > 0) {
                                db.insertEntry(newId, ts, direction,
                                    amountStr.toDouble(), what, selectedCat)
                            }
                        } catch (_: Exception) {}

                        flash("✓ saved", "#00E676")
                        b.etAmount.text?.clear()
                        b.etWhat.text?.clear()
                        selectedCat = ""
                        selectedDate = LocalDate.now()
                        buildCategoryButtons()
                        updateDateDisplay()
                        if (isSalaryEntry) {
                            handleSalaryEntry(salaryDate, prevPeriodStart)
                        } else {
                            fetchBalancePill()
                            fetchSavingsTotal()
                        }
                    } else {
                        flash("Error ${resp.code}", "#FF1744")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { flash("Network error", "#FF1744") }
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
        b.tvSaveMsg.text = msg
        b.tvSaveMsg.setTextColor(Color.parseColor(colorHex))
        b.tvSaveMsg.postDelayed({ b.tvSaveMsg.text = "" }, 2500)
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
        }
    }

    private fun fetchSavingsTotal() {
        // Show local cache instantly, then refresh from server
        CoroutineScope(Dispatchers.IO).launch {
            val local = db.getSavingsTotal()
            withContext(Dispatchers.Main) { updateSavingsDisplay(local) }
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

        // Background sync from server — replaces local cache
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
        val summaryUrl = if (isFirstSalary) {
            "$BASE_URL/api/summary?to=$prevTo"
        } else {
            if (prevPeriodStart.isAfter(prevTo)) {
                // Salary added on same day as previous salary — nothing to settle
                updateSalaryPeriod(salaryDate)
                fetchBalancePill()
                fetchSavingsTotal()
                return
            }
            "$BASE_URL/api/summary?from=$prevPeriodStart&to=$prevTo"
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val summaryResp = client.newCall(Request.Builder().url(summaryUrl).build()).execute()
                val summary     = JSONObject(summaryResp.body!!.string())
                val balance     = summary.getDouble("income") - summary.getDouble("expense")

                if (balance != 0.0) {
                    val note = if (isFirstSalary) "All-time to $prevTo" else "Period $prevPeriodStart to $prevTo"
                    val adjustBody = JSONObject().apply {
                        put("delta", balance)
                        put("note",  note)
                    }.toString().toRequestBody("application/json".toMediaType())
                    val adjustResp = client.newCall(
                        Request.Builder().url("$BASE_URL/api/savings/adjust").post(adjustBody).build()
                    ).execute()
                    if (adjustResp.isSuccessful) {
                        val newTotal = JSONObject(adjustResp.body!!.string()).optDouble("total", db.getSavingsTotal() + balance)
                        db.saveSavingsTotal(newTotal)
                    }
                }
            } catch (_: Exception) {}
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
        if (entries.length() == 0) {
            b.tvEmpty.visibility          = View.VISIBLE
            b.historyContainer.visibility = View.GONE
        } else {
            b.tvEmpty.visibility          = View.GONE
            b.historyContainer.visibility = View.VISIBLE
            buildHistoryView(entries)
            b.nestedScroll.post { b.nestedScroll.requestLayout() }
        }
    }

    private fun buildHistoryView(entries: JSONArray) {
        val container = b.historyContainer
        container.removeAllViews()

        val sky    = Color.parseColor("#29B6F6")
        val gold   = Color.parseColor("#FFB300")
        val green  = Color.parseColor("#00E676")
        val red    = Color.parseColor("#FF1744")
        val cardBg = Color.parseColor("#0D0D0D")
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
                val entryId     = e.getInt("id")
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
                    setOnLongClickListener { confirmDelete(entryId); true }
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

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun confirmDelete(id: Int) {
        android.app.AlertDialog.Builder(this)
            .setMessage("Delete this entry?")
            .setPositiveButton("DELETE") { _, _ -> deleteEntry(id) }
            .setNegativeButton("CANCEL", null)
            .show()
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

    private fun showTab(add: Boolean) {
        val sky = Color.parseColor("#29B6F6")
        val dim = Color.parseColor("#646464")

        b.layoutAdd.visibility          = if (add) View.VISIBLE else View.GONE
        b.layoutHistory.visibility      = if (add) View.GONE    else View.VISIBLE
        b.btnTabAdd.setTextColor(        if (add) sky            else dim)
        b.btnTabHistory.setTextColor(    if (add) dim            else sky)
        b.tabIndicatorAdd.visibility     = if (add) View.VISIBLE  else View.INVISIBLE
        b.tabIndicatorHistory.visibility = if (add) View.INVISIBLE else View.VISIBLE

        if (!add) fetchHistory()
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
}
