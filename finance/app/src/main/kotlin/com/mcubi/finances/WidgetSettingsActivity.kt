package com.mcubi.finances

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WidgetSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_NAME    = "widget_prefs"
        const val KEY_TEXT_SIZE = "text_size_sp"
        const val DEFAULT_SIZE  = 11

        fun getTextSize(context: Context): Int =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TEXT_SIZE, DEFAULT_SIZE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bg      = Color.parseColor("#0D0D0D")
        val sky     = Color.parseColor("#29B6F6")
        val white   = Color.WHITE
        val dim     = Color.parseColor("#646464")
        val MATCH   = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP    = ViewGroup.LayoutParams.WRAP_CONTENT
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        val prefs   = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_TEXT_SIZE, DEFAULT_SIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Title
        root.addView(TextView(this).apply {
            text = "WIDGET SIZE"
            textSize = 11f; typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(sky); letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(24) }
        })

        // Label row: Compact ←→ Large
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        }
        labelRow.addView(TextView(this).apply {
            text = "COMPACT"; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(dim); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        labelRow.addView(TextView(this).apply {
            text = "LARGE"; textSize = 8f; typeface = Typeface.MONOSPACE
            setTextColor(dim); letterSpacing = 0.1f; gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        root.addView(labelRow)

        // Slider — range 7sp (compact) to 18sp (large)
        val MIN = 7; val MAX = 18
        val seek = SeekBar(this).apply {
            max = MAX - MIN
            progress = (current - MIN).coerceIn(0, MAX - MIN)
            progressDrawable?.setTint(sky)
            thumb?.setTint(white)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(16) }
        }
        root.addView(seek)

        // Preview label
        val preview = TextView(this).apply {
            text = "\u25b2 IN     \u25bc OUT"
            textSize = current.toFloat(); typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(sky); gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dp(24) }
        }
        root.addView(preview)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                val sp = p + MIN
                preview.textSize = sp.toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Save button
        root.addView(TextView(this).apply {
            text = "APPLY"; textSize = 12f; typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#060a12")); gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_save)
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(48))
            setOnClickListener {
                val chosen = seek.progress + MIN
                prefs.edit().putInt(KEY_TEXT_SIZE, chosen).apply()
                // Refresh all widgets
                val mgr = AppWidgetManager.getInstance(this@WidgetSettingsActivity)
                val ids = mgr.getAppWidgetIds(
                    android.content.ComponentName(this@WidgetSettingsActivity, FinanceWidget::class.java))
                for (id in ids) FinanceWidget.updateWidget(this@WidgetSettingsActivity, mgr, id)
                finish()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
