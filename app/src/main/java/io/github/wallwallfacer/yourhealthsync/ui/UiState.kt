package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import io.github.wallwallfacer.yourhealthsync.sync.LastRun

/** Everything the wizard and the dashboard draw. */
data class UiState(
    val huaweiConnected: Boolean = false,
    val healthConnectAvailable: Boolean = true,
    val granted: Set<Metric> = emptySet(),
    val enabled: Set<Metric> = Metric.entries.toSet(),
    val latest: Map<Metric, BodySample> = emptyMap(),
    val lastRun: LastRun = LastRun(0L, 0, 0, null),
    val busy: Boolean = false,
    val message: String? = null,
)

/** Actions the screens can ask for; MainActivity carries them out. */
interface UiActions {
    fun connectHuawei()
    fun revokeHuawei()
    fun allowHealthConnect()
    fun openHealthConnectSettings()
    fun setEnabled(metric: Metric, enabled: Boolean)
    fun syncNow()
    fun backfill()
    fun runSetupAgain()
    fun openPrivacy()
}
