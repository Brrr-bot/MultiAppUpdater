package com.mcubi.finances

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class QuickAddActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DIRECTION = "direction"
        private const val BASE_URL = "https://finances.mcubittbuilders.workers.dev"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var db: FinanceDb
    private var direction = "out"
    private var selectedCat = ""
    private val catButtons = mutableListOf<Pair<String, TextView>>()
    private lateinit var etAmount: EditText
    private lateinit var etWhat: EditText
    private lateinit var tvMsg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FinanceDb(this)
        direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "out"

        val isIn    = direction == "in"
        val accent  = if (isIn) Color.parseColor("#00E676") else Color.parseColor("#FF1744")
        val sky     = Color.parseColor("#29B6F6")
        val dim     = Color.parseColor("#646464")
        val white   = Color.WHITE
        val bg      = Color.parseColor("#0D0D0D")
        val fieldBg = Color.parseColor("#1A1A1A")
        val MATCH   = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP    = ViewGroup.LayoutParams.WRAP_CONTENT
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // Direction header
        root.addView(TextView(this).apply {
            text = if (isIn) "\u25b2  MONEY IN" else "\u25bc  MONEY OUT"
            textSize = 13f; typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        })

        // Amount label + field
        root.addView(TextView(this).apply {
            text = "AMOUNT"; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(dim); letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })
        etAmount = EditText(this).apply {
            hint = "0"; textSize = 22f; typeface = Typeface.MONOSPACE
            setTextColor(white); setHintTextColor(dim)
            setBackgroundColor(fieldBg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        root.addView(etAmount)

        // Category label + horizontal scroll
        root.addView(TextView(this).apply {
            text = "CATEGORY"; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(dim); letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })
        val catScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
            isHorizontalScrollBarEnabled = false
        }
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cats = if (isIn) FinanceCategories.income else FinanceCategories.expense
        for (cat in cats) {
            val btn = TextView(this).apply {
                text = cat; textSize = 10f; typeface = Typeface.MONOSPACE
                setTextColor(dim); gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                setBackgroundResource(R.drawable.cat_btn_inactive)
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(WRAP, dp(34)).also {
                    it.setMargins(0, 0, dp(6), 0)
                }
                setOnClickListener { selectCat(cat, sky) }
            }
            catButtons.add(cat to btn); catRow.addView(btn)
        }
        catScroll.addView(catRow); root.addView(catScroll)

        // Description label + field
        root.addView(TextView(this).apply {
            text = "DESCRIPTION"; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(dim); letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(4) }
        })
        etWhat = EditText(this).apply {
            hint = "e.g. Lunch, Grab ride\u2026"; textSize = 13f; typeface = Typeface.MONOSPACE
            setTextColor(white); setHintTextColor(dim)
            setBackgroundColor(fieldBg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(20) }
        }
        root.addView(etWhat)

        // Status message
        tvMsg = TextView(this).apply {
            textSize = 11f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        root.addView(tvMsg)

        // Save button — reuses existing btn_save drawable
        root.addView(TextView(this).apply {
            text = "SAVE"; textSize = 13f; typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_save)
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(48))
            setOnClickListener { saveEntry() }
        })

        setContentView(ScrollView(this).apply { addView(root) })

        etAmount.requestFocus()
        etAmount.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(etAmount, InputMethodManager.SHOW_IMPLICIT)
        }, 80)
    }

    private fun selectCat(cat: String, sky: Int) {
        selectedCat = cat
        if (etWhat.text.isNullOrBlank()) etWhat.setText(cat)
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

    private fun saveEntry() {
        val amountStr = etAmount.text.toString().trim()
        val what      = etWhat.text.toString().trim()
        if (amountStr.isEmpty() || amountStr.toDoubleOrNull() == null || amountStr.toDouble() <= 0) {
            flash("Enter a valid amount", "#FF1744"); return
        }
        if (what.isEmpty()) { flash("Describe what it was", "#FF1744"); return }
        if (selectedCat.isEmpty()) { flash("Pick a category", "#FF1744"); return }

        val amount = amountStr.toDouble()
        val ts     = System.currentTimeMillis()
        val date   = LocalDate.now()

        CoroutineScope(Dispatchers.IO).launch {
            val localId = db.insertLocalEntry(ts, direction, amount, what, selectedCat)
            withContext(Dispatchers.Main) {
                flash("\u2713 saved", "#00E676")
                etAmount.postDelayed({ finish() }, 700)
            }
            try {
                val body = JSONObject().apply {
                    put("direction", direction); put("amount", amount)
                    put("what", what); put("category", selectedCat); put("date", date.toString())
                }.toString().toRequestBody("application/json".toMediaType())
                val resp = client.newCall(
                    Request.Builder().url("$BASE_URL/api/add").post(body).build()
                ).execute()
                if (resp.isSuccessful) {
                    val j = JSONObject(resp.body?.string() ?: "")
                    val serverId = j.optInt("id", 0)
                    val serverTs = j.optLong("ts", ts)
                    if (serverId > 0)
                        db.markEntrySynced(localId, serverId, serverTs, direction, amount, what, selectedCat)
                }
            } catch (_: Exception) {}
        }
    }

    private fun flash(msg: String, colorHex: String) {
        tvMsg.text = msg
        tvMsg.setTextColor(Color.parseColor(colorHex))
    }
}
