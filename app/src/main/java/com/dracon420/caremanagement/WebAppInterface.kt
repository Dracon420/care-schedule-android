package com.dracon420.caremanagement

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.webkit.JavascriptInterface

/**
 * Injected into the WebView as `window.AndroidBridge`.
 * The PWA calls these methods to push schedule state into
 * Android SharedPreferences, which the widget reads from.
 */
class WebAppInterface(private val context: Context) {

    companion object {
        const val PREFS_NAME  = "CareSchedulePrefs"
        const val KEY_SCHEDULE = "schedule_state"
    }

    /** Called by the PWA when a schedule is started or restarted. */
    @JavascriptInterface
    fun updateSchedule(json: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEDULE, json).apply()
        refreshWidgets()
    }

    /**
     * Called by the PWA Sign In button when running inside the APK.
     * Delegates to MainActivity which runs the native Android Google Sign-In flow.
     * On success, MainActivity calls window.onAndroidGoogleToken(token, expiresIn).
     */
    @JavascriptInterface
    fun requestGoogleSignIn() {
        (context as? MainActivity)?.startNativeGoogleSignIn()
    }

    /**
     * Returns the installed APK version name (e.g. "1.6.0").
     * The web app compares this against version.json to detect available updates.
     */
    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /** Called by the PWA when Stop & Clear is confirmed. */
    @JavascriptInterface
    fun clearSchedule() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SCHEDULE).apply()
        refreshWidgets()
    }

    private fun refreshWidgets() {
        val manager   = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CareScheduleWidget::class.java)
        val ids       = manager.getAppWidgetIds(component)
        ids.forEach { CareScheduleWidget.updateWidget(context, manager, it) }
    }
}
