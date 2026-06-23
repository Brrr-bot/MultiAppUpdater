package com.mcubi.finances

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FinanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_finance)

            fun pending(dir: String, reqCode: Int): PendingIntent {
                val i = Intent(context, QuickAddActivity::class.java).apply {
                    putExtra(QuickAddActivity.EXTRA_DIRECTION, dir)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                return PendingIntent.getActivity(
                    context, reqCode, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            views.setOnClickPendingIntent(R.id.btnWidgetIn,   pending("in",  1))
            views.setOnClickPendingIntent(R.id.btnWidgetOut,  pending("out", 2))
            manager.updateAppWidget(widgetId, views)
        }
    }
}
