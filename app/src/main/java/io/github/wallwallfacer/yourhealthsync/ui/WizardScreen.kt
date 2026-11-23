package io.github.wallwallfacer.yourhealthsync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.wallwallfacer.yourhealthsync.model.Metric

const val WIZARD_STEPS = 3

/** First-run setup: 1 source, 2 destination, 3 data types. */
@Composable
fun WizardScreen(step: Int, ui: UiState, actions: UiActions, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text(
                "Step ${step + 1} of $WIZARD_STEPS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (step + 1f) / WIZARD_STEPS },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    0 -> SourceStep(ui, actions)
                    1 -> DestinationStep(ui, actions)
                    else -> DataTypesStep(ui, actions)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext) { Text(if (step == WIZARD_STEPS - 1) "Finish" else "Next") }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.SourceStep(ui: UiState, actions: UiActions) {
    StepTitle("Choose a source", "Where your weight and body composition come from.")
    ChoiceCard(
        title = "HUAWEI Health",
        body = "Readings from your HUAWEI scale, read through HUAWEI Health Kit.",
        status = if (ui.huaweiConnected) "Connected" else "Not connected yet",
    ) {
        if (ui.huaweiConnected) OutlinedButton(onClick = actions::connectHuawei) { Text("Reconnect") }
        else Button(onClick = actions::connectHuawei) { Text("Connect HUAWEI Health") }
    }
    Hint("Needs HMS Core and HUAWEI Health on this phone. Only the official release build can read HUAWEI data.")
    ui.message?.let { Message(it) }
    TextButton(onClick = actions::openPrivacy) { Text("Privacy statement") }
}

@Composable
private fun ColumnScope.DestinationStep(ui: UiState, actions: UiActions) {
    StepTitle("Choose a destination", "Where the readings are written.")
    ChoiceCard(
        title = "Health Connect",
        body = "Android's health data store. Samsung Health and other apps read from it.",
        status = when {
            !ui.healthConnectAvailable -> "Not available on this phone"
            ui.granted.isEmpty() -> "Not allowed yet"
            else -> "Allowed for ${ui.granted.size} of ${Metric.entries.size} data types"
        },
    ) {
        Button(onClick = actions::allowHealthConnect, enabled = ui.healthConnectAvailable) { Text("Allow access") }
    }
    ui.message?.let { Message(it) }
}

@Composable
private fun ColumnScope.DataTypesStep(ui: UiState, actions: UiActions) {
    StepTitle("Choose data types", "Each one is copied on every sync. You can change this later.")
    Card {
        Metric.entries.forEachIndexed { i, metric ->
            if (i > 0) HorizontalDivider()
            val on = metric in ui.enabled
            ListItem(
                headlineContent = { Text(MetricFormat.label(metric)) },
                supportingContent = {
                    Text(
                        if (on && metric !in ui.granted) "Not allowed in Health Connect yet"
                        else if (MetricFormat.shownInSamsungHealth(metric)) "Shown in Samsung Health"
                        else "Health Connect only",
                        color = if (on && metric !in ui.granted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = { Checkbox(checked = on, onCheckedChange = { actions.setEnabled(metric, it) }) },
                modifier = Modifier.clickable { actions.setEnabled(metric, !on) },
            )
        }
    }
}

/** A selected option card, in the style of a source/destination picker. */
@Composable
private fun ChoiceCard(title: String, body: String, status: String, action: @Composable () -> Unit) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = true, onClick = null)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            action()
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}
