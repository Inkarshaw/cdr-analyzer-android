package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class InsightItem(val title: String, val detail: String)

@Composable
fun InvestigationInsightsScreen(rows: List<CdrRecord>, tags: Map<String, ContactTag>) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text("Import a CDR to generate investigation insights.", modifier = Modifier.padding(16.dp))
        }
        return
    }

    val contacts = rows.filter { it.otherParty.isNotBlank() }
        .groupingBy { it.otherParty }.eachCount().entries
        .sortedByDescending { it.value }.take(10)

    val towers = rows.filter { it.cellId.isNotBlank() }
        .groupingBy { if (it.lac.isBlank()) "Cell ${it.cellId}" else "LAC ${it.lac} / Cell ${it.cellId}" }
        .eachCount().entries.sortedByDescending { it.value }.take(10)

    val imeis = rows.map { it.imei }.filter { it.isNotBlank() }.distinct()
    val imsis = rows.map { it.imsi }.filter { it.isNotBlank() }.distinct()
    val coordinateRows = rows.count {
        val lat = it.latitude.toDoubleOrNull()
        val lon = it.longitude.toDoubleOrNull()
        lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    val directionCounts = rows.groupingBy { directionLabel(it.direction) }.eachCount()
    val insights = mutableListOf<InsightItem>()
    insights += InsightItem("CDR volume", "${rows.size} records • ${contacts.size} records with another party")
    insights += InsightItem("Direction", directionCounts.entries.joinToString(" • ") { "${it.key}: ${it.value}" })
    insights += InsightItem("Device / SIM footprint", "${imeis.size} IMEI(s) • ${imsis.size} IMSI(s)")
    insights += InsightItem("Movement evidence", "${towers.size} top tower(s) shown • $coordinateRows records contain valid coordinates")

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Investigation Insights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
            Text("Automatically derived from the imported CDR. Treat patterns as leads for verification, not conclusions.", style = MaterialTheme.typography.bodySmall)
        }
        items(insights) { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.detail)
                }
            }
        }
        item { Text("High-frequency contacts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
        items(contacts) { entry ->
            val tag = tags[entry.key]
            val label = if (tag != null && tag.name.isNotBlank()) "${tag.name} (${entry.key})" else entry.key
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = { Text(listOfNotNull(tag?.relation?.takeIf { it.isNotBlank() }, "${entry.value} interactions").joinToString(" • ")) }
            )
            HorizontalDivider()
        }
        item { Text("Frequent towers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
        items(towers) { entry ->
            ListItem(headlineContent = { Text(entry.key) }, supportingContent = { Text("${entry.value} records") })
            HorizontalDivider()
        }
    }
}

private fun directionLabel(value: String): String {
    val s = value.lowercase()
    return when {
        s.contains("incoming") || s == "in" || s.contains("mti") -> "Incoming"
        s.contains("outgoing") || s == "out" || s.contains("moc") -> "Outgoing"
        s.contains("sms") -> "SMS"
        value.isBlank() -> "Unknown"
        else -> value
    }
}
