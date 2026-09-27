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
fun CaseWorkspaceScreen(
    store: CaseWorkspaceStore,
    currentRows: List<CdrRecord>,
    currentFileName: String,
    onLoadDataset: (List<CdrRecord>, String) -> Unit
) {
    var cases by remember { mutableStateOf(store.list()) }
    var selected by remember { mutableStateOf<CaseWorkspace?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showCommon by remember { mutableStateOf(false) }

    fun refresh(id: String? = selected?.id) {
        cases = store.list()
        selected = id?.let(store::load)
    }

    if (showNew) {
        NewCaseDialog(
            onDismiss = { showNew = false },
            onCreate = { title, crime ->
                selected = store.create(title, crime)
                refresh(selected?.id)
                showNew = false
            }
        )
    }

    selected?.let { workspace ->
        if (showCommon) {
            CommonContactsDialog(store.commonContacts(workspace), onDismiss = { showCommon = false })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(workspace.title, style = MaterialTheme.typography.headlineSmall)
                if (workspace.crimeNumber.isNotBlank()) Text("Crime No.: ${workspace.crimeNumber}")
                Text("${workspace.datasets.size} CDR dataset(s)", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selected = null }, modifier = Modifier.weight(1f)) { Text("All Cases") }
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
            if (workspace.datasets.size >= 2) {
                item { Button(onClick = { showCommon = true }, modifier = Modifier.fillMaxWidth()) { Text("Common Contacts") } }
            }
            item { Text("Saved CDRs", style = MaterialTheme.typography.titleMedium) }
            if (workspace.datasets.isEmpty()) {
                item { Text("No CDR saved in this case yet. Import a CDR, then tap Add Current CDR.") }
            } else {
                items(workspace.datasets) { dataset ->
                    Card(Modifier.fillMaxWidth().clickable { onLoadDataset(dataset.records, dataset.name) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(dataset.name, style = MaterialTheme.typography.titleSmall)
                            Text("${dataset.records.size} records", style = MaterialTheme.typography.bodySmall)
                            Text("Tap to load for analysis", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Text("Case Notes", style = MaterialTheme.typography.titleMedium) }
            item {
                var notes by remember(workspace.id, workspace.updatedAt) { mutableStateOf(workspace.notes) }
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Notes") })
                Button(onClick = { selected = store.updateNotes(workspace, notes); refresh(selected?.id) }, modifier = Modifier.padding(top = 8.dp)) { Text("Save Notes") }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) { Text("New Case") }
        Spacer(Modifier.height(8.dp))
        Text("Saved Cases", style = MaterialTheme.typography.titleMedium)
        if (cases.isEmpty()) {
            Text("No saved cases yet.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn {
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
}

@Composable
private fun NewCaseDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var crime by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Case") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Case name") }, singleLine = true)
                OutlinedTextField(crime, { crime = it }, label = { Text("Crime No. (optional)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onCreate(title, crime) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommonContactsDialog(contacts: List<CommonContact>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Common Contacts") },
        text = {
            if (contacts.isEmpty()) Text("No number appears in two or more saved CDRs.")
            else LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(contacts.take(200)) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.number) },
                        supportingContent = { Text("${contact.datasetCount} CDRs • ${contact.totalInteractions} interactions\n${contact.datasetNames.joinToString()}") }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
