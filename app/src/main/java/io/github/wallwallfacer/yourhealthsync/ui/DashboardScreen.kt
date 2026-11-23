package io.github.wallwallfacer.yourhealthsync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.wallwallfacer.yourhealthsync.model.Metric

/** Home screen after setup: the sync route, one row per data type, and Sync now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(ui: UiState, actions: UiActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Health Sync") },
                actions = { OverflowMenu(ui, actions) },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.message?.let { Message(it) }
                Button(onClick = actions::syncNow, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.busy) "Syncing…" else "Sync now")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RouteCard(ui, actions) }
            items(Metric.entries) { metric ->
                MetricRow(metric, ui, actions)
            }
            item { Hint("Syncs the last 30 days every 6 hours. Re-syncing never creates duplicates.") }
        }
    }
}

@Composable
private fun RouteCard(ui: UiState, actions: UiActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HUAWEI Health", style = MaterialTheme.typography.titleMedium)
                Text("→", style = MaterialTheme.typography.titleMedium)
                Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            }
            val run = ui.lastRun
            Text(
                when {
                    run.at == 0L -> "Never synced"
                    run.failed > 0 -> "Last sync ${MetricFormat.time(run.at)} · ${run.written} records · ${run.failed} failed"
                    else -> "Last sync ${MetricFormat.time(run.at)} · ${run.written} records"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ui.huaweiConnected) {
                TextButton(onClick = actions::connectHuawei, contentPadding = PaddingValues(0.dp)) {
                    Text("HUAWEI Health is not connected. Connect")
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metric: Metric, ui: UiState, actions: UiActions) {
    val enabled = metric in ui.enabled
    val state = RowState.of(enabled, metric in ui.granted, ui.latest[metric])
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            overlineContent = {
                Text(if (MetricFormat.shownInSamsungHealth(metric)) "Samsung Health" else "Health Connect only")
            },
            headlineContent = { Text(MetricFormat.label(metric), style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                when (state) {
                    RowState.Off -> Text("Off")
                    RowState.NotAllowed -> Text("Not allowed in Health Connect. Tap to allow", color = MaterialTheme.colorScheme.error)
                    RowState.Waiting -> Text("Waiting for the first sync")
                    is RowState.Synced -> Text(
                        "${MetricFormat.value(metric, state.latest.value)} · ${MetricFormat.time(state.latest.epochMillis)}",
                    )
                }
            },
            trailingContent = { Switch(checked = enabled, onCheckedChange = { actions.setEnabled(metric, it) }) },
            modifier = if (state == RowState.NotAllowed) Modifier.clickable { actions.setEnabled(metric, true) } else Modifier,
        )
    }
}

@Composable
private fun OverflowMenu(ui: UiState, actions: UiActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple("Backfill last year", !ui.busy, actions::backfill),
                Triple(if (ui.huaweiConnected) "Reconnect HUAWEI Health" else "Connect HUAWEI Health", true, actions::connectHuawei),
                Triple("Revoke HUAWEI access", true, actions::revokeHuawei),
                Triple("Health Connect settings", true, actions::openHealthConnectSettings),
                Triple("Run setup again", true, actions::runSetupAgain),
                Triple("Privacy statement", true, actions::openPrivacy),
            ).forEach { (label, enabled, action) ->
                DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { open = false; action() })
            }
        }
    }
}
