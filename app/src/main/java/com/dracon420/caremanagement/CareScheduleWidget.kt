package com.dracon420.caremanagement

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CareScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            // Outer catch: if ANYTHING throws (resource load, security, etc.)
            // we still push a valid RemoteViews so Android never shows "Failed to load"
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                val prefs = context.getSharedPreferences(
                    WebAppInterface.PREFS_NAME, Context.MODE_PRIVATE
                )
                val json = prefs.getString(WebAppInterface.KEY_SCHEDULE, null)

                // Inner catch: JSON/state errors fall back to idle, not a crash
                try {
                    if (json != null) {
                        val state   = JSONObject(json)
                        val running = state.optBoolean("running", false)
                        if (running) {
                            val events = state.optJSONArray("events")
                            val now    = System.currentTimeMillis()
                            var next: JSONObject? = null

                            if (events != null) {
                                for (i in 0 until events.length()) {
                                    val ev  = events.getJSONObject(i)
                                    val end = parseIso(ev.getString("end"))
                                    if (end > now) { next = ev; break }
                                }
                            }

                            if (next != null) showRunning(context, views, next, now)
                            else              showIdle(context, views)
                        } else {
                            showIdle(context, views)
                        }
                    } else {
                        showIdle(context, views)
                    }
                } catch (e: Exception) {
                    showIdle(context, views)
                }

                manager.updateAppWidget(widgetId, views)

            } catch (e: Exception) {
                // Absolute last resort — push the bare layout with no customisation
                // so the system never shows the "Failed to load" placeholder
                try {
                    val bare = RemoteViews(context.packageName, R.layout.widget_layout)
                    manager.updateAppWidget(widgetId, bare)
                } catch (_: Exception) { /* nothing more we can do */ }
            }
        }

        // ── Idle state — show START ───────────────────────────────────────

        private fun showIdle(context: Context, views: RemoteViews) {
            views.setTextViewText(R.id.widget_action_btn, "START")
            views.setInt(R.id.widget_action_btn, "setBackgroundResource", R.drawable.widget_btn_start)
            views.setTextColor(R.id.widget_action_btn, 0xFF07090F.toInt())
            views.setViewVisibility(R.id.widget_divider,      View.GONE)
            views.setViewVisibility(R.id.widget_task_section, View.GONE)
            views.setViewVisibility(R.id.widget_idle_hint,    View.VISIBLE)

            // Tap → open app with ?action=start to auto-launch the schedule
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("action", "start")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_btn, pi)
            views.setOnClickPendingIntent(R.id.widget_idle_hint,  pi)
        }

        // ── Running state — show RUNNING + next task ──────────────────────

        private fun showRunning(
            context: Context, views: RemoteViews,
            event: JSONObject, now: Long
        ) {
            views.setTextViewText(R.id.widget_action_btn, "RUNNING")
            views.setInt(R.id.widget_action_btn, "setBackgroundResource", R.drawable.widget_btn_running)
            views.setTextColor(R.id.widget_action_btn, 0xFF00E676.toInt())
            views.setViewVisibility(R.id.widget_divider,      View.VISIBLE)
            views.setViewVisibility(R.id.widget_task_section, View.VISIBLE)
            views.setViewVisibility(R.id.widget_idle_hint,    View.GONE)

            val emoji    = event.optString("emoji", "")
            val title    = event.optString("title", "—")
            val startMs  = parseIso(event.getString("start"))
            val minsLeft = ((startMs - now) / 60000L).toInt()

            views.setTextViewText(R.id.widget_task_name, "$emoji $title")
            views.setTextViewText(R.id.widget_task_time, formatTime(startMs))

            val (countdown, color) = when {
                minsLeft <= 0  -> "NOW"     to 0xFFFF6D3B.toInt()
                minsLeft <= 10 -> "${minsLeft}m" to 0xFFFFB300.toInt()
                minsLeft < 60  -> "${minsLeft}m" to 0xFF00E5FF.toInt()
                else -> {
                    val h = minsLeft / 60; val m = minsLeft % 60
                    (if (m > 0) "${h}h ${m}m" else "${h}h") to 0xFF00E5FF.toInt()
                }
            }
            views.setTextViewText(R.id.widget_countdown, countdown)
            views.setTextColor(R.id.widget_countdown, color)

            // Tap → open main app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_btn,  pi)
            views.setOnClickPendingIntent(R.id.widget_task_section, pi)
        }

        // ── Helpers ───────────────────────────────────────────────────────

        private fun parseIso(iso: String): Long {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    return sdf.parse(iso)?.time ?: continue
                } catch (_: Exception) {}
            }
            return 0L
        }

        private fun formatTime(ms: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = ms
            val h   = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val m   = cal.get(Calendar.MINUTE)
            val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            return "$h:${m.toString().padStart(2, '0')} $amPm"
        }
    }
}
