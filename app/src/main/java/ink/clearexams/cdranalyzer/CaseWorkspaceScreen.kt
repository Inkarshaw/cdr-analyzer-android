package ink.clearexams.cdranalyzer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun CaseWorkspaceScreen(
    store: CaseWorkspaceStore,
    currentRows: List<CdrRecord>,
    currentFileName: String,
    onLoadDataset: (List<CdrRecord>, String) -> Unit
) {
    val context = LocalContext.current
    val identityStore = remember(context) { NumberIdentityStore(context) }
    var cases by remember { mutableStateOf(store.list()) }
    var selected by remember { mutableStateOf<CaseWorkspace?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showCommon by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    var showShared by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var showGraph by remember { mutableStateOf(false) }
    var showCorrelation by remember { mutableStateOf(false) }
    var showCaseReview by remember { mutableStateOf(false) }
    var showIdentities by remember { mutableStateOf(false) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var reportStatus by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }

    fun refresh(id: String? = selected?.id) {
        cases = store.list()
        selected = id?.let(store::load)
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { CaseBackupManager.restore(context, uri, store) }
                .onSuccess { restored ->
                    cases = store.list(); selected = restored; fromText = ""; toText = ""
                    backupStatus = "Restored ${restored.title}"
                }
                .onFailure { backupStatus = "Restore failed: ${it.message ?: "Invalid backup"}" }
        }
    }

    if (showNew) NewCaseDialog({ showNew = false }) { title, crime ->
        selected = store.create(title, crime); refresh(selected?.id); showNew = false
    }

    selected?.let { workspace ->
        val timeFilter = CaseTimeFilter(fromText.trim(), toText.trim())
        val filterValid = CaseTimeFiltering.valid(timeFilter)
        val analysisWorkspace = if (filterValid) CaseTimeFiltering.apply(workspace, timeFilter) else workspace
        val originalCount = CaseTimeFiltering.count(workspace)
        val filteredCount = CaseTimeFiltering.count(analysisWorkspace)

        if (showIdentities) NumberIdentityDialog(workspace.id, workspace, identityStore) { showIdentities = false }
        if (showCommon) CommonContactsDialog(workspace.id, store.commonContacts(analysisWorkspace), identityStore) { showCommon = false }
        if (showLinks) LinkAnalysisDialog(store, analysisWorkspace, identityStore) { showLinks = false }
        if (showShared) SharedTowerDialog(store, analysisWorkspace) { showShared = false }
        if (showTimeline) InvestigationTimelineDialog(analysisWorkspace) { showTimeline = false }
        if (showGraph) RelationshipGraphDialog(analysisWorkspace) { showGraph = false }
        if (showCorrelation) AdvancedCorrelationDashboardDialog(analysisWorkspace, store) { showCorrelation = false }
        if (showCaseReview) CaseReviewDialog(analysisWorkspace) { showCaseReview = false }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(workspace.title, style = MaterialTheme.typography.headlineSmall)
                if (workspace.crimeNumber.isNotBlank()) Text("Crime No.: ${workspace.crimeNumber}")
                Text("${workspace.datasets.size} CDR dataset(s) • $originalCount records", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ selected = null }, Modifier.weight(1f)) { Text("All Cases") }
                    Button(
                        onClick = {
                            if (currentRows.isNotEmpty()) {
                                selected = store.addDataset(workspace, currentFileName, currentRows)
                                refresh(selected?.id)
                            }
                        },
                        enabled = currentRows.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Add Current CDR") }
                }
            }
            if (workspace.datasets.isNotEmpty()) item {
                Button({ showIdentities = true }, Modifier.fillMaxWidth()) { Text("Manage Number Identities / Roles") }
            }
            if (workspace.datasets.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Analysis Date / Time Filter", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(fromText, { fromText = it }, label = { Text("From: DD-MM-YYYY HH:MM") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(toText, { toText = it }, label = { Text("To: DD-MM-YYYY HH:MM") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        when {
                            !filterValid -> Text("Invalid date/time range. Analysis is using all records.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            timeFilter.active -> Text("Filter active: $filteredCount of $originalCount records", style = MaterialTheme.typography.bodySmall)
                            else -> Text("No filter: all $originalCount records", style = MaterialTheme.typography.bodySmall)
                        }
                        if (timeFilter.active) OutlinedButton({ fromText = ""; toText = "" }, Modifier.fillMaxWidth()) { Text("Clear Date / Time Filter") }
                    }
                }
            }
            if (workspace.datasets.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ showTimeline = true }, Modifier.weight(1f), enabled = filterValid) { Text("Timeline") }
                    Button({ showGraph = true }, Modifier.weight(1f), enabled = filterValid) { Text("Relationship Graph") }
                }
            }
            if (workspace.datasets.isNotEmpty()) item {
                Button({ showCaseReview = true }, Modifier.fillMaxWidth(), enabled = filterValid) { Text("Case Review / Chronology / Flags") }
            }
            if (workspace.datasets.size >= 2) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ showCommon = true }, Modifier.weight(1f), enabled = filterValid) { Text("Common Contacts") }
                        Button({ showLinks = true }, Modifier.weight(1f), enabled = filterValid) { Text("Link Analysis") }
                    }
                }
                item { Button({ showShared = true }, Modifier.fillMaxWidth(), enabled = filterValid) { Text("Shared Towers / Co-location") } }
                item {
                    Button({ showCorrelation = true }, Modifier.fillMaxWidth(), enabled = filterValid) {
                        Text("Advanced Correlation Dashboard")
                    }
                }
            }
            if (workspace.datasets.isNotEmpty()) item {
                Button(
                    onClick = {
                        runCatching {
                            val file = CaseReportExporter.export(context, workspace, timeFilter)
                            reportStatus = "Report created: ${file.name}"
                            CaseReportExporter.share(context, file)
                        }.onFailure { reportStatus = "Report export failed: ${it.message ?: "Unknown error"}" }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = filterValid
                ) { Text("Export / Share Investigation Report") }
                if (reportStatus.isNotBlank()) Text(reportStatus, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Case Backup", style = MaterialTheme.typography.titleSmall)
                        Text("Backup includes case details, notes and all saved CDR records.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    val file = CaseBackupManager.export(context, workspace)
                                    backupStatus = "Backup created: ${file.name}"
                                    CaseBackupManager.share(context, file)
                                }.onFailure { backupStatus = "Backup failed: ${it.message ?: "Unknown error"}" }
                            }, modifier = Modifier.weight(1f)) { Text("Backup Case") }
                            Button(onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Restore Backup") }
                        }
                        if (backupStatus.isNotBlank()) Text(backupStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Saved CDRs", style = MaterialTheme.typography.titleMedium) }
            if (workspace.datasets.isEmpty()) item { Text("No CDR saved in this case yet. Import a CDR, then tap Add Current CDR.") }
            else items(workspace.datasets) { dataset ->
                Card(Modifier.fillMaxWidth().clickable { onLoadDataset(dataset.records, dataset.name) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dataset.name, style = MaterialTheme.typography.titleSmall)
                        Text("${dataset.records.size} records", style = MaterialTheme.typography.bodySmall)
                        Text("Tap to load for analysis", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { Text("Case Notes", style = MaterialTheme.typography.titleMedium) }
            item {
                var notes by remember(workspace.id, workspace.updatedAt) { mutableStateOf(workspace.notes) }
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Notes") })
                Button({ selected = store.updateNotes(workspace, notes); refresh(selected?.id) }, Modifier.padding(top = 8.dp)) { Text("Save Notes") }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ showNew = true }, Modifier.weight(1f)) { Text("New Case") }
            OutlinedButton({ restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, Modifier.weight(1f)) { Text("Restore Backup") }
        }
        if (backupStatus.isNotBlank()) Text(backupStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))
        Text("Saved Cases", style = MaterialTheme.typography.titleMedium)
        if (cases.isEmpty()) Text("No saved cases yet.", modifier = Modifier.padding(top = 16.dp))
        else LazyColumn {
            items(cases) { workspace ->
                ListItem(
                    headlineContent = { Text(workspace.title) },
                    supportingContent = { Text(listOf(workspace.crimeNumber, "${workspace.datasets.size} CDR(s)").filter { it.isNotBlank() }.joinToString(" • ")) },
                    modifier = Modifier.clickable { selected = workspace }
                )
                HorizontalDivider()
            }
        }
    }
}
