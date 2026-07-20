package com.nothing.expensetracker.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nothing.expensetracker.R
import com.nothing.expensetracker.feature.overlay.QuickAddShortcutActivity

class ExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_expense_tracker)

            // Map each dot button to launch the overlay with a pre-selected color category
            views.setOnClickPendingIntent(R.id.btn_dot_green, createPendingIntent(context, "GREEN"))
            views.setOnClickPendingIntent(R.id.btn_dot_yellow, createPendingIntent(context, "YELLOW"))
            views.setOnClickPendingIntent(R.id.btn_dot_red, createPendingIntent(context, "RED"))
            views.setOnClickPendingIntent(R.id.btn_dot_blue, createPendingIntent(context, "BLUE"))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPendingIntent(context: Context, color: String): PendingIntent {
            val intent = Intent(context, QuickAddShortcutActivity::class.java).apply {
                putExtra("SELECTED_COLOR", color)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                color.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
