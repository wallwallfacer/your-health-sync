package io.github.wallwallfacer.yourhealthsync.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import io.github.wallwallfacer.yourhealthsync.hc.HealthConnectSink
import io.github.wallwallfacer.yourhealthsync.csv.CsvImport
import io.github.wallwallfacer.yourhealthsync.huawei.HuaweiAuth
import io.github.wallwallfacer.yourhealthsync.huawei.HuaweiBodySource
import io.github.wallwallfacer.yourhealthsync.sync.SyncResult
import io.github.wallwallfacer.yourhealthsync.sync.SyncStatus
import io.github.wallwallfacer.yourhealthsync.sync.SyncWorker
import io.github.wallwallfacer.yourhealthsync.sync.Syncer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var message by mutableStateOf("")
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)

    private val huaweiAuth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        message = HuaweiAuth.errorOf(this, it.data) ?: "HUAWEI Health connected"
    }

    private val hcPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        message = if (it.containsAll(HealthConnectSink.PERMISSIONS)) "Health Connect access granted" else "Health Connect access incomplete"
        if (it.containsAll(HealthConnectSink.PERMISSIONS)) SyncWorker.schedule(this)
    }

    // Temporary entry point for the CSV device check; replaced by the dashboard.
    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        busy = true
        message = "Importing…"
        lifecycleScope.launch {
            message = try {
                val outcome = CsvImport.run(this@MainActivity, uri)
                SyncStatus.save(this@MainActivity, outcome.result)
                outcome.summary
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            status = SyncStatus.describe(this@MainActivity)
            busy = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        status = SyncStatus.describe(this)
        lifecycleScope.launch {
            if (HealthConnectSink.isAvailable(this@MainActivity) && HealthConnectSink.hasPermissions(this@MainActivity)) {
                SyncWorker.schedule(this@MainActivity)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your Health Sync", style = MaterialTheme.typography.headlineSmall)
            Text("Copies weight and body composition from HUAWEI Health to Health Connect.")
            Button(onClick = ::connectHuawei, Modifier.fillMaxWidth()) {
                Text("1. Connect HUAWEI Health")
            }
            Button(onClick = ::requestHealthConnect, Modifier.fillMaxWidth()) { Text("2. Allow Health Connect") }
            Button(onClick = { launchSync { Syncer.engine(this@MainActivity).syncRecent(HuaweiBodySource(this@MainActivity)) } }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Sync now (last 30 days)")
            }
            OutlinedButton(
                onClick = { launchSync { Syncer.engine(this@MainActivity).backfill(HuaweiBodySource(this@MainActivity), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365)) } },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            ) { Text("Backfill history (last year)") }
            OutlinedButton(onClick = { pickCsv.launch(CsvImport.MIME_TYPES) }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Import CSV file")
            }
            Text(status)
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = ::revokeHuawei, Modifier.fillMaxWidth()) { Text("Revoke HUAWEI Health access") }
            TextButton(onClick = { startActivity(Intent(this@MainActivity, PrivacyActivity::class.java)) }) {
                Text("Privacy statement")
            }
        }
    }

    private fun connectHuawei() {
        try {
            huaweiAuth.launch(HuaweiAuth.requestIntent(this))
        } catch (e: Exception) {
            message = "Cannot open HUAWEI authorization. Is HMS Core installed and up to date? (${e.message})"
        }
    }

    private fun requestHealthConnect() {
        if (!HealthConnectSink.isAvailable(this)) {
            message = "Health Connect is not available on this phone"
            return
        }
        hcPermissions.launch(HealthConnectSink.PERMISSIONS)
    }

    private fun launchSync(block: suspend () -> SyncResult) {
        busy = true
        message = "Syncing…"
        lifecycleScope.launch {
            message = try {
                val result = block()
                SyncStatus.save(this@MainActivity, result)
                if (result.failedWindows.isEmpty()) "Wrote ${result.written} records"
                else "Wrote ${result.written} records; ${result.failedWindows.size} window(s) failed: ${result.firstError}"
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            status = SyncStatus.describe(this@MainActivity)
            busy = false
        }
    }

    private fun revokeHuawei() {
        lifecycleScope.launch {
            message = try {
                HuaweiAuth.revoke(this@MainActivity)
                "HUAWEI Health access revoked"
            } catch (e: Exception) {
                "Revoke failed: ${e.message}"
            }
        }
    }
}
