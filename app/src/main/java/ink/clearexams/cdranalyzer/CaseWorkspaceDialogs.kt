package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewCaseDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var crime by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Case") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Case name") }, singleLine = true)
            OutlinedTextField(crime, { crime = it }, label = { Text("Crime No. (optional)") }, singleLine = true)
        } },
        confirmButton = { Button({ onCreate(title, crime) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CommonContactsDialog(caseId: String, contacts: List<CommonContact>, identities: NumberIdentityStore, onDismiss: () -> Unit) {
    fun label(n: String) = identities.find(caseId, n)?.displayLabel ?: n
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Common Contacts") },
        text = {
            if (contacts.isEmpty()) Text("No number appears in two or more saved CDRs for the selected period.")
            else LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(contacts.take(200)) { contact ->
                    ListItem(headlineContent = { Text(label(contact.number)) }, supportingContent = { Text("${contact.datasetCount} CDRs • ${contact.totalInteractions} interactions\n${contact.datasetNames.joinToString()}") })
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
}

@Composable
fun LinkAnalysisDialog(store: CaseWorkspaceStore, workspace: CaseWorkspace, identities: NumberIdentityStore, onDismiss: () -> Unit) {
    fun label(n: String) = identities.find(workspace.id, n)?.displayLabel ?: n
    val profiles = remember(workspace) { store.linkProfiles(workspace) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<LinkProfile?>(null) }
    var showRecords by remember { mutableStateOf(false) }
    val filtered = profiles.filter { p -> query.isBlank() || label(p.number).contains(query, true) || p.number.contains(query, true) || p.imeis.any { it.contains(query, true) } || p.imsis.any { it.contains(query, true) } || p.towers.any { it.contains(query, true) } }
    if (showRecords) chosen?.let { LinkedRecordsDialog(workspace.id, it.number, store.linkedRecords(workspace, it.number), identities) { showRecords = false } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cross-CDR Link Analysis") },
        text = { Column {
            OutlinedTextField(query, { query = it }, label = { Text("Search name / role / number / IMEI / IMSI / tower") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            chosen?.let { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(label(p.number), style = MaterialTheme.typography.titleMedium)
                Text("${p.totalInteractions} interactions • ${p.datasetNames.size} CDR(s)")
                if (p.datasetNames.isNotEmpty()) Text("CDRs: ${p.datasetNames.joinToString()}")
                if (p.imeis.isNotEmpty()) Text("IMEI: ${p.imeis.joinToString()}")
                if (p.imsis.isNotEmpty()) Text("IMSI: ${p.imsis.joinToString()}")
                if (p.towers.isNotEmpty()) Text("Towers: ${p.towers.joinToString()}")
                Button({ showRecords = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("View ${p.totalInteractions} Matching Records") }
            } }; Spacer(Modifier.height(8.dp)) }
            LazyColumn(Modifier.heightIn(max = 380.dp)) { items(filtered.take(300)) { p ->
                ListItem(headlineContent = { Text(label(p.number)) }, supportingContent = { Text("${p.totalInteractions} interactions • ${p.datasetNames.size} CDR(s) • ${p.towers.size} tower(s)") }, modifier = Modifier.clickable { chosen = p })
                HorizontalDivider()
            } }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
}

@Composable
private fun LinkedRecordsDialog(caseId: String, number: String, records: List<LinkedRecord>, identities: NumberIdentityStore, onDismiss: () -> Unit) {
    fun label(n: String) = identities.find(caseId, n)?.displayLabel ?: n
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Records: ${label(number)}") },
        text = { if (records.isEmpty()) Text("No matching records.") else LazyColumn(Modifier.heightIn(max = 520.dp)) { items(records.take(1000)) { item ->
            val r = item.record
            val tower = if (r.cellId.isNotBlank()) listOf(r.lac, r.cellId).filter { it.isNotBlank() }.joinToString("/") else ""
            val party = r.otherParty.ifBlank { r.number }
            val detail = listOf(item.datasetName, r.dateTime, r.direction, r.duration.takeIf { it.isNotBlank() }?.let { "${it}s" }.orEmpty(), r.imei.takeIf { it.isNotBlank() }?.let { "IMEI $it" }.orEmpty(), r.imsi.takeIf { it.isNotBlank() }?.let { "IMSI $it" }.orEmpty(), tower.takeIf { it.isNotBlank() }?.let { "Tower $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")
            ListItem(headlineContent = { Text(label(party)) }, supportingContent = { Text(detail) })
            HorizontalDivider()
        } } },
        confirmButton = { TextButton(onDismiss) { Text("Back") } }
    )
}

@Composable
fun SharedTowerDialog(store: CaseWorkspaceStore, workspace: CaseWorkspace, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }
    var window by remember { mutableIntStateOf(15) }; var results by remember { mutableStateOf<List<SharedTower>>(emptyList()) }
    var timed by remember { mutableStateOf<List<CoLocationMatch>>(emptyList()) }; var searched by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SharedTower?>(null) }
    selected?.let { tower -> SharedTowerEventsDialog(first, second, tower) { selected = null } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shared Towers / Co-location") },
        text = { Column {
            OutlinedTextField(first, { first = it }, label = { Text("Number A") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(second, { second = it }, label = { Text("Number B") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text("Time window", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(5, 15, 30, 60).forEach { m -> FilterChip(window == m, { window = m }, { Text("±$m min") }) } }
            Button(onClick = { results = store.sharedTowers(workspace, first.trim(), second.trim()); timed = store.coLocationMatches(workspace, first.trim(), second.trim(), window); searched = true }, enabled = first.isNotBlank() && second.isNotBlank() && first.trim() != second.trim(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Compare") }
            Spacer(Modifier.height(8.dp))
            if (searched) {
                Text("Time-matched events: ${timed.size}", style = MaterialTheme.typography.titleSmall)
                Text("Same tower within ±$window minutes. This is an investigative lead, not proof of exact physical proximity.", style = MaterialTheme.typography.bodySmall)
                if (timed.isEmpty()) Text("No time-window matches found.", modifier = Modifier.padding(vertical = 6.dp))
                else LazyColumn(Modifier.heightIn(max = 220.dp)) { items(timed.take(200)) { m -> ListItem(headlineContent = { Text("Tower ${m.tower} • ${m.differenceMinutes} min apart") }, supportingContent = { Text("A: ${m.firstEvent.dateTime} • ${m.firstEvent.datasetName}\nB: ${m.secondEvent.dateTime} • ${m.secondEvent.datasetName}") }); HorizontalDivider() } }
                HorizontalDivider(); Text("All shared towers: ${results.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                if (results.isEmpty()) Text("No shared LAC/Cell tower found.")
                else LazyColumn(Modifier.heightIn(max = 180.dp)) { items(results) { tower -> ListItem(headlineContent = { Text("Tower ${tower.tower}") }, supportingContent = { Text("$first: ${tower.firstCount} event(s) • $second: ${tower.secondCount} event(s)") }, modifier = Modifier.clickable { selected = tower }); HorizontalDivider() } }
            }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SharedTowerEventsDialog(first: String, second: String, tower: SharedTower, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tower ${tower.tower}") },
        text = { LazyColumn(Modifier.heightIn(max = 520.dp)) {
            item { Text(first, style = MaterialTheme.typography.titleSmall) }
            items(tower.firstEvents) { e -> ListItem(headlineContent = { Text(e.dateTime.ifBlank { "Time unavailable" }) }, supportingContent = { Text("${e.datasetName} • ${e.direction}") }) }
            item { HorizontalDivider(); Text(second, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(tower.secondEvents) { e -> ListItem(headlineContent = { Text(e.dateTime.ifBlank { "Time unavailable" }) }, supportingContent = { Text("${e.datasetName} • ${e.direction}") }) }
        } },
        confirmButton = { TextButton(onDismiss) { Text("Back") } }
    )
}
