package io.github.wallwallfacer.yourhealthsync.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import io.github.wallwallfacer.yourhealthsync.hc.HealthConnectSink
import io.github.wallwallfacer.yourhealthsync.huawei.HuaweiAuth
import io.github.wallwallfacer.yourhealthsync.huawei.HuaweiBodySource
import io.github.wallwallfacer.yourhealthsync.model.Metric
import io.github.wallwallfacer.yourhealthsync.settings.AppSettings
import io.github.wallwallfacer.yourhealthsync.sync.MetricStatus
import io.github.wallwallfacer.yourhealthsync.sync.SyncResult
import io.github.wallwallfacer.yourhealthsync.sync.SyncWorker
import io.github.wallwallfacer.yourhealthsync.sync.Syncer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/** Hosts the first-run wizard and, once setup is done, the dashboard. */
class MainActivity : ComponentActivity(), UiActions {
    private var ui by mutableStateOf(UiState())
    private var setupDone by mutableStateOf(false)
    private var step by mutableIntStateOf(0)

    private val huaweiAuth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val error = HuaweiAuth.errorOf(this, it.data)
        if (error == null) AppSettings.setHuaweiConnected(this, true)
        refresh(message = error ?: "HUAWEI Health connected", isError = error != null)
    }

    /** Remembers what was asked for, to tell a denial from a granted request. */
    private var requested: Set<Metric> = emptySet()

    private val hcPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val missing = requested.filter { HealthConnectSink.permissionOf(it) !in granted }
        refresh(
            message = if (missing.isEmpty()) "Health Connect access allowed"
            else "Not allowed: ${missing.joinToString { MetricFormat.label(it) }}. Use Health Connect settings in the menu to allow it.",
            isError = missing.isNotEmpty(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setupDone = AppSettings.isSetupDone(this)
        setContent {
            AppTheme {
                if (setupDone) {
                    DashboardScreen(ui, this)
                } else {
                    BackHandler(enabled = step > 0) { step-- }
                    WizardScreen(step, ui, this, onBack = { step-- }, onNext = ::nextStep)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Reloads state from settings and Health Connect, and keeps the periodic sync scheduled. */
    private fun refresh(message: String? = ui.message, isError: Boolean = message != null && message == ui.message && ui.messageIsError) {
        val available = HealthConnectSink.isAvailable(this)
        lifecycleScope.launch {
            val granted = if (available) HealthConnectSink.grantedMetrics(this@MainActivity) else emptySet()
            ui = ui.copy(
                huaweiConnected = AppSettings.isHuaweiConnected(this@MainActivity),
                healthConnectAvailable = available,
                granted = granted,
                enabled = AppSettings.enabledMetrics(this@MainActivity),
                latest = MetricStatus.latest(this@MainActivity),
                lastRun = MetricStatus.lastRun(this@MainActivity),
                message = message,
                messageIsError = isError,
            )
            if (setupDone && (ui.enabled intersect granted).isNotEmpty()) SyncWorker.schedule(this@MainActivity)
        }
    }

    private fun nextStep() {
        when (step) {
            1 -> {
                val types = SetupRules.typesForDataStep(ui.enabled, ui.granted)
                AppSettings.setEnabledMetrics(this, types)
                ui = ui.copy(enabled = types)
            }
            WIZARD_STEPS - 1 -> {
                AppSettings.setSetupDone(this, true)
                setupDone = true
            }
        }
        if (step < WIZARD_STEPS - 1) step++
        refresh(message = null)
    }

    override fun connectHuawei() {
        try {
            huaweiAuth.launch(HuaweiAuth.requestIntent(this))
        } catch (e: Exception) {
            ui = ui.copy(message = "Cannot open HUAWEI authorization. Is HMS Core installed and up to date? (${e.message})", messageIsError = true)
        }
    }

    override fun revokeHuawei() {
        lifecycleScope.launch {
            try {
                HuaweiAuth.revoke(this@MainActivity)
                AppSettings.setHuaweiConnected(this@MainActivity, false)
                refresh("HUAWEI Health access revoked")
            } catch (e: Exception) {
                refresh("Revoke failed: ${e.message}", isError = true)
            }
        }
    }

    override fun allowHealthConnect() = requestHealthConnect(Metric.entries.toSet())

    private fun requestHealthConnect(metrics: Set<Metric>) {
        if (!HealthConnectSink.isAvailable(this)) {
            ui = ui.copy(message = "Health Connect is not available on this phone", messageIsError = true)
            return
        }
        requested = metrics
        hcPermissions.launch(HealthConnectSink.permissionsFor(metrics))
    }

    override fun openHealthConnectSettings() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            ui = ui.copy(message = "Health Connect settings are not available on this phone", messageIsError = true)
        }
    }

    override fun setEnabled(metric: Metric, enabled: Boolean) {
        val metrics = if (enabled) ui.enabled + metric else ui.enabled - metric
        AppSettings.setEnabledMetrics(this, metrics)
        ui = ui.copy(enabled = metrics)
        if (enabled && metric !in ui.granted) requestHealthConnect(setOf(metric))
    }

    override fun syncNow() = launchSync { Syncer.engine(this).syncRecent(HuaweiBodySource(this)) }

    override fun backfill() = launchSync {
        Syncer.engine(this).backfill(HuaweiBodySource(this), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365))
    }

    override fun runSetupAgain() {
        step = 0
        setupDone = false
        AppSettings.setSetupDone(this, false)
        refresh(message = null)
    }

    override fun openPrivacy() {
        startActivity(Intent(this, PrivacyActivity::class.java))
    }

    private fun launchSync(block: suspend () -> SyncResult) {
        ui = ui.copy(busy = true, message = null)
        lifecycleScope.launch {
            try {
                val result = block()
                MetricStatus.save(this@MainActivity, result)
                ui = ui.copy(busy = false)
                refresh(describe(result), isError = result.failedWindows.isNotEmpty())
            } catch (e: Exception) {
                ui = ui.copy(busy = false)
                refresh("Sync failed: ${e.message}", isError = true)
            }
        }
    }

    private fun describe(result: SyncResult): String = buildList {
        add("Synced ${result.written} records")
        if (result.skipped > 0) add("${result.skipped} implausible values skipped")
        if (result.failedWindows.isNotEmpty()) add("${result.failedWindows.size} window(s) failed: ${result.firstError}")
    }.joinToString(" · ")
}
