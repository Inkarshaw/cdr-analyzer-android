package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun InvestigationTimelineDialog(workspace: CaseWorkspace, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val identityStore = remember(context) { NumberIdentityStore(context) }
    val summary = remember(workspace) { InvestigationTimeline.build(workspace) }
    var query by remember { mutableStateOf("") }
    var flaggedOnly by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("All") }
    var selectedEvent by remember { mutableStateOf<TimelineEvent?>(null) }
    val types = listOf("All", "Incoming", "Outgoing", "SMS", "Data")

    fun label(number: String): String =
        identityStore.find(workspace.id, number)?.displayLabel ?: number

    val filtered = summary.events.filter { event ->
        (!flaggedOnly || event.flags.isNotEmpty()) &&
            (selectedType == "All" || event.type == selectedType) &&
            (query.isBlank() || listOf(
                event.number, label(event.number), event.dataset, event.imei,
                event.imsi, event.tower, event.direction
            ).any { it.contains(query, ignoreCase = true) })
    }

    selectedEvent?.let { event ->
        TimelineEventDialog(event, label(event.number)) { selectedEvent = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Investigation Timeline") },
        text = {
            Column {
                Text(
                    "${summary.events.size} events • ${summary.deviceChanges} IMEI changes • " +
                        "${summary.simChanges} IMSI changes • ${summary.towerChanges} tower changes",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name / role / number / IMEI / IMSI / tower") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(flaggedOnly, { flaggedOnly = !flaggedOnly }, { Text("Flagged") })
                    types.take(3).forEach { type ->
                        FilterChip(selectedType == type, { selectedType = type }, { Text(type) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.drop(3).forEach { type ->
                        FilterChip(selectedType == type, { selectedType = type }, { Text(type) })
                    }
                }
                summary.frequentContacts.firstOrNull()?.let { top ->
                    Text(
                        "Top contact: ${label(top.first)} (${top.second} events)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    if (filtered.isEmpty()) {
                        item { Text("No timeline events match the selected filters.", Modifier.padding(12.dp)) }
                    }
                    items(filtered.take(1500)) { event ->
                        ListItem(
                            headlineContent = { Text(label(event.number).ifBlank { "Unknown number" }) },
                            supportingContent = {
                                Text(
                                    listOf(
                                        event.dateTime,
                                        event.type,
                                        event.dataset,
                                        event.tower.takeIf { it.isNotBlank() }?.let { "Tower $it" }.orEmpty(),
                                        event.flags.joinToString()
                                    ).filter { it.isNotBlank() }.joinToString(" • ")
                                )
                            },
                            modifier = Modifier.clickable { selectedEvent = event }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TimelineEventDialog(event: TimelineEvent, label: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label.ifBlank { "Timeline event" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Number: ${event.number}")
                Text("Time: ${event.dateTime.ifBlank { "Unavailable" }}")
                Text("Type: ${event.type}")
                Text("Dataset: ${event.dataset}")
                if (event.direction.isNotBlank()) Text("Direction: ${event.direction}")
                if (event.duration.isNotBlank()) Text("Duration: ${event.duration}")
                if (event.imei.isNotBlank()) Text("IMEI: ${event.imei}")
                if (event.imsi.isNotBlank()) Text("IMSI: ${event.imsi}")
                if (event.tower.isNotBlank()) Text("Tower: ${event.tower}")
                if (event.flags.isNotEmpty()) {
                    Text("Flags: ${event.flags.joinToString()}", style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}
