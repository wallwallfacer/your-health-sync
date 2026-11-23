package io.github.wallwallfacer.yourhealthsync.settings

import android.content.Context
import io.github.wallwallfacer.yourhealthsync.model.Metric

/** What the user chose in setup: which data types to sync, and whether setup is done. */
object AppSettings {
    private const val PREFS = "settings"
    private const val ENABLED = "enabled_metrics"
    private const val SETUP_DONE = "setup_done"
    private const val HUAWEI_CONNECTED = "huawei_connected"

    /** All types are on until the user turns some off. */
    fun enabledMetrics(context: Context): Set<Metric> {
        val ids = prefs(context).getStringSet(ENABLED, null) ?: return Metric.entries.toSet()
        return Metric.entries.filter { it.id in ids }.toSet()
    }

    fun setEnabledMetrics(context: Context, metrics: Set<Metric>) {
        prefs(context).edit().putStringSet(ENABLED, metrics.map { it.id }.toSet()).apply()
    }

    fun isSetupDone(context: Context): Boolean = prefs(context).getBoolean(SETUP_DONE, false)

    fun setSetupDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(SETUP_DONE, done).apply()
    }

    /** Set when HUAWEI's consent screen succeeds, cleared on revoke. HMS offers no cheap way to ask. */
    fun isHuaweiConnected(context: Context): Boolean = prefs(context).getBoolean(HUAWEI_CONNECTED, false)

    fun setHuaweiConnected(context: Context, connected: Boolean) {
        prefs(context).edit().putBoolean(HUAWEI_CONNECTED, connected).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
