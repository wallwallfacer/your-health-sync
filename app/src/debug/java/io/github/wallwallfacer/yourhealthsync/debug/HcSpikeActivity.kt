package io.github.wallwallfacer.yourhealthsync.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import io.github.wallwallfacer.yourhealthsync.hc.HealthConnectSink
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Throwaway spike (debug builds only): writes one record per type, using values
 * typed from your latest real weigh-in, plus a 60-day-old weight, so you can
 * check what Samsung Health shows. Needs no Huawei access.
 */
class HcSpikeActivity : ComponentActivity() {
    private val values = Metric.entries.associateWith { mutableStateOf("") }
    private var message by mutableStateOf("Enter your latest real weigh-in values. Empty fields are skipped.")

    private val permissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        message = if (it.containsAll(HealthConnectSink.PERMISSIONS)) "Permissions granted, tap Write" else "Permissions missing"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Health Connect spike", style = MaterialTheme.typography.headlineSmall)
                    Metric.entries.forEach { metric ->
                        var text by values.getValue(metric)
                        OutlinedTextField(text, { text = it }, label = { Text(label(metric)) }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = { permissions.launch(HealthConnectSink.PERMISSIONS) }) { Text("Grant Health Connect access") }
                    Button(onClick = ::write) { Text("Write now + 60-day-old weight") }
                    Text(message)
                }
            }
        }
    }

    private fun label(metric: Metric) = when (metric) {
        Metric.WEIGHT -> "Weight (kg)"
        Metric.BODY_FAT -> "Body fat (%)"
        Metric.BASAL_METABOLIC_RATE -> "Basal metabolic rate (kcal/day)"
        Metric.BONE_MASS -> "Bone mass (kg)"
        Metric.BODY_WATER_MASS -> "Body water mass (kg)"
    }

    private fun write() {
        val now = System.currentTimeMillis()
        val samples = values.mapNotNull { (metric, state) ->
            state.value.toDoubleOrNull()?.let { BodySample(metric, now, it) }
        }
        val weight = samples.firstOrNull { it.metric == Metric.WEIGHT }
        val old = weight?.copy(epochMillis = now - TimeUnit.DAYS.toMillis(60))
        lifecycleScope.launch {
            message = try {
                val n = HealthConnectSink(this@HcSpikeActivity).write(samples + listOfNotNull(old))
                "Wrote $n records. Check the Health Connect app and Samsung Health. Afterwards delete the 60-day-old test weight."
            } catch (e: Exception) {
                "Write failed: ${e.message}"
            }
        }
    }
}
