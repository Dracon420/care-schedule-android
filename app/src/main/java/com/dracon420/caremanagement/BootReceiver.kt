package com.dracon420.caremanagement

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Refreshes widget state after the phone restarts.
 * SharedPreferences survive a reboot, so the widget
 * can restore RUNNING + next task without opening the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val manager   = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CareScheduleWidget::class.java)
            val ids       = manager.getAppWidgetIds(component)
            ids.forEach { CareScheduleWidget.updateWidget(context, manager, it) }
        }
    }
}
