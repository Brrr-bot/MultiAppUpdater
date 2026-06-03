package com.mcubi.finances

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews

class FinanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_finance)

            // Apply saved text size to labels
            val sp = WidgetSettingsActivity.getTextSize(context).toFloat()
            views.setTextViewTextSize(R.id.tvWidgetInLabel,  TypedValue.COMPLEX_UNIT_SP, sp)
            views.setTextViewTextSize(R.id.tvWidgetOutLabel, TypedValue.COMPLEX_UNIT_SP, sp)

            fun pending(dir: String, reqCode: Int): PendingIntent {
                val i = Intent(context, QuickAddActivity::class.java).apply {
                    putExtra(QuickAddActivity.EXTRA_DIRECTION, dir)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                return PendingIntent.getActivity(
                    context, reqCode, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            fun gearPending(): PendingIntent {
                val i = Intent(context, WidgetSettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                return PendingIntent.getActivity(
                    context, 3, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // Clicks on the whole LinearLayout blocks
            views.setOnClickPendingIntent(R.id.btnWidgetIn,   pending("in",  1))
            views.setOnClickPendingIntent(R.id.btnWidgetOut,  pending("out", 2))
            views.setOnClickPendingIntent(R.id.btnWidgetGear, gearPending())
            manager.updateAppWidget(widgetId, views)
        }
    }
}
