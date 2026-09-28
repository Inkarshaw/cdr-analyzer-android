package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedCorrelationDashboardDialog(
    workspace: CaseWorkspace,
    store: CaseWorkspaceStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val identities = remember(context) { NumberIdentityStore(context) }
    val data = remember(workspace) { CorrelationDashboard.build(workspace, store) }
    var section by remember { mutableStateOf("Overview") }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var selectedEntity by remember { mutableStateOf<CorrelationSharedEntity?>(null) }
    var selectedPair by remember { mutableStateOf<CorrelationDatasetPair?>(null) }
    var selectedLink by remember { mutableStateOf<GraphEdge?>(null) }

    fun label(number: String): String = identities.find(workspace.id, number)?.displayLabel ?: number

    selectedNumber?.let { number ->
        CorrelationNumberRecordsDialog(
            title = label(number),
            records = store.linkedRecords(workspace, number),
            label = ::label,
            onDismiss = { selectedNumber = null }
        )
    }
    selectedEntity?.let { entity ->
        CorrelationEntityRecordsDialog(workspace, entity) { selectedEntity = null }
    }
    selectedPair?.let { pair ->
        CorrelationPairDialog(pair, ::label) { selectedPair = null }
    }
    selectedLink?.let { link ->
        CorrelationLinkDialog(workspace, link, ::label) { selectedLink = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced CDR Correlation") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "${data.datasets.size} CDR dataset(s) • ${data.totalRecords} record(s)",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(listOf("Overview", "Common", "Pairs", "Devices", "Towers")) { item ->
                        FilterChip(
                            selected = section == item,
                            onClick = { section = item },
                            label = { Text(item) }
                        )
                    }
                }
                when (section) {
                    "Overview" -> CorrelationOverview(data, ::label, { selectedLink = it }, { selectedNumber = it })
                    "Common" -> CorrelationCommonContacts(data.commonContacts, ::label) { selectedNumber = it }
                    "Pairs" -> CorrelationPairs(data.pairs, ::label) { selectedPair = it }
                    "Devices" -> CorrelationDevices(data.sharedImeis, data.sharedImsis) { selectedEntity = it }
                    else -> CorrelationTowers(data.sharedTowers) { selectedEntity = it }
                }
                Text(
                    "Cross-CDR overlaps are review aids. Shared contacts, towers, device identifiers, or close timestamps should be verified against source records and other evidence.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CorrelationOverview(
    data: CorrelationDashboardData,
    label: (String) -> String,
    onLink: (GraphEdge) -> Unit,
    onNumber: (String) -> Unit
) {
    LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CorrelationMetric("Common contacts", data.commonContacts.size.toString(), Modifier.weight(1f))
                CorrelationMetric("Shared towers", data.sharedTowers.size.toString(), Modifier.weight(1f))
                CorrelationMetric("Cross-CDR links", data.crossDatasetLinks.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CorrelationMetric("Shared IMEI", data.sharedImeis.size.toString(), Modifier.weight(1f))
                CorrelationMetric("Shared IMSI", data.sharedImsis.size.toString(), Modifier.weight(1f))
                CorrelationMetric("CDR pairs", data.pairs.size.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Dataset profiles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
        items(data.datasets) { d ->
            Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(8.dp)) {
                    Text(d.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            d.subjectNumber?.let { "Subject: ${label(it)}" },
                            "${d.records} records",
                            "${d.uniqueContacts} contacts",
                            "${d.uniqueTowers} towers",
                            "${d.uniqueImeis} IMEI",
                            "${d.uniqueImsis} IMSI"
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    d.subjectNumber?.let { subject ->
                        TextButton(onClick = { onNumber(subject) }, contentPadding = PaddingValues(0.dp)) { Text("View subject records") }
                    }
                }
            }
        }
        if (data.crossDatasetLinks.isNotEmpty()) {
            item { Text("Communication links seen across multiple CDRs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
            items(data.crossDatasetLinks.take(15)) { link ->
                ListItem(
                    headlineContent = { Text("${label(link.source)} ↔ ${label(link.target)}") },
                    supportingContent = { Text("${link.interactions} interaction(s) • ${link.datasets.size} CDR(s)\n${link.datasets.joinToString()}") },
                    modifier = Modifier.clickable { onLink(link) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CorrelationCommonContacts(
    contacts: List<CommonContact>,
    label: (String) -> String,
    onSelected: (String) -> Unit
) {
    if (contacts.isEmpty()) { Text("No contact appears in two or more CDR datasets.") ; return }
    LazyColumn(Modifier.heightIn(max = 500.dp)) {
        items(contacts.take(300)) { contact ->
            ListItem(
                headlineContent = { Text(label(contact.number)) },
                supportingContent = { Text("${contact.datasetCount} CDR(s) • ${contact.totalInteractions} interaction(s)\n${contact.datasetNames.joinToString()}") },
                modifier = Modifier.clickable { onSelected(contact.number) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CorrelationPairs(
    pairs: List<CorrelationDatasetPair>,
    label: (String) -> String,
    onSelected: (CorrelationDatasetPair) -> Unit
) {
    if (pairs.isEmpty()) { Text("At least two CDR datasets are required.") ; return }
    LazyColumn(Modifier.heightIn(max = 500.dp)) {
        items(pairs) { pair ->
            val a = pair.firstSubject?.let(label) ?: "Subject unavailable"
            val b = pair.secondSubject?.let(label) ?: "Subject unavailable"
            ListItem(
                headlineContent = { Text("${pair.firstDataset} ↔ ${pair.secondDataset}") },
                supportingContent = {
                    Text(
                        "$a ↔ $b\n" +
                            "${pair.commonContacts.size} common contact(s) • ${pair.commonTowers.size} common tower(s) • " +
                            "${pair.commonImeis.size} shared IMEI • ${pair.commonImsis.size} shared IMSI • " +
                            "${pair.directSubjectInteractions} direct subject interaction(s)"
                    )
                },
                modifier = Modifier.clickable { onSelected(pair) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CorrelationDevices(
    imeis: List<CorrelationSharedEntity>,
    imsis: List<CorrelationSharedEntity>,
    onSelected: (CorrelationSharedEntity) -> Unit
) {
    LazyColumn(Modifier.heightIn(max = 500.dp)) {
        item { Text("Shared IMEI", style = MaterialTheme.typography.titleSmall) }
        if (imeis.isEmpty()) item { Text("No IMEI appears in two or more CDR datasets.", style = MaterialTheme.typography.bodySmall) }
        else items(imeis.take(200)) { item -> CorrelationEntityRow(item, onSelected) }
        item { Text("Shared IMSI", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
        if (imsis.isEmpty()) item { Text("No IMSI appears in two or more CDR datasets.", style = MaterialTheme.typography.bodySmall) }
        else items(imsis.take(200)) { item -> CorrelationEntityRow(item, onSelected) }
    }
}

@Composable
private fun CorrelationTowers(
    towers: List<CorrelationSharedEntity>,
    onSelected: (CorrelationSharedEntity) -> Unit
) {
    if (towers.isEmpty()) { Text("No LAC/Cell tower appears in two or more CDR datasets.") ; return }
    LazyColumn(Modifier.heightIn(max = 500.dp)) {
        items(towers.take(300)) { item -> CorrelationEntityRow(item, onSelected) }
    }
}

@Composable
private fun CorrelationEntityRow(
    item: CorrelationSharedEntity,
    onSelected: (CorrelationSharedEntity) -> Unit
) {
    ListItem(
        headlineContent = { Text("${item.type} ${item.value}") },
        supportingContent = { Text("${item.datasetCount} CDR(s) • ${item.totalRecords} matching record(s)\n${item.datasetNames.joinToString()}") },
        modifier = Modifier.clickable { onSelected(item) }
    )
    HorizontalDivider()
}

@Composable
private fun CorrelationMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(7.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CorrelationNumberRecordsDialog(
    title: String,
    records: List<LinkedRecord>,
    label: (String) -> String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (records.isEmpty()) Text("No matching records.")
            else LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(records.take(1500)) { item ->
                    val r = item.record
                    val tower = if (r.cellId.isNotBlank()) listOf(r.lac, r.cellId).filter { it.isNotBlank() }.joinToString("/") else ""
                    val party = r.otherParty.ifBlank { r.number }
                    ListItem(
                        headlineContent = { Text(r.dateTime.ifBlank { "Time unavailable" }) },
                        supportingContent = {
                            Text(
                                listOf(
                                    item.datasetName,
                                    label(party),
                                    r.direction,
                                    r.imei.takeIf { it.isNotBlank() }?.let { "IMEI $it" }.orEmpty(),
                                    r.imsi.takeIf { it.isNotBlank() }?.let { "IMSI $it" }.orEmpty(),
                                    tower.takeIf { it.isNotBlank() }?.let { "Tower $it" }.orEmpty()
                                ).filter { it.isNotBlank() }.joinToString(" • ")
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

@Composable
private fun CorrelationEntityRecordsDialog(
    workspace: CaseWorkspace,
    entity: CorrelationSharedEntity,
    onDismiss: () -> Unit
) {
    val records = remember(workspace, entity) {
        workspace.datasets.flatMap { dataset ->
            dataset.records.filter { r ->
                when (entity.type) {
                    "IMEI" -> r.imei.trim() == entity.value
                    "IMSI" -> r.imsi.trim() == entity.value
                    else -> r.cellId.isNotBlank() && listOf(r.lac.trim(), r.cellId.trim()).filter { it.isNotBlank() }.joinToString("/") == entity.value
                }
            }.map { LinkedRecord(dataset.name, it) }
        }.sortedBy { it.record.dateTime }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entity.type} ${entity.value}") },
        text = {
            Column {
                Text("${records.size} matching record(s) across ${entity.datasetCount} CDR(s)", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(records.take(1500)) { item ->
                        val r = item.record
                        ListItem(
                            headlineContent = { Text(r.dateTime.ifBlank { "Time unavailable" }) },
                            supportingContent = { Text("${item.datasetName} • ${r.number} ↔ ${r.otherParty} • ${r.direction}") }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

@Composable
private fun CorrelationPairDialog(
    pair: CorrelationDatasetPair,
    label: (String) -> String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pair.firstDataset} ↔ ${pair.secondDataset}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                item {
                    Text(
                        "${pair.firstSubject?.let(label) ?: "Subject unavailable"} ↔ ${pair.secondSubject?.let(label) ?: "Subject unavailable"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("${pair.directSubjectInteractions} direct subject interaction(s)", style = MaterialTheme.typography.bodySmall)
                }
                item { CorrelationPairSection("Common contacts", pair.commonContacts.map(label)) }
                item { CorrelationPairSection("Common towers", pair.commonTowers) }
                item { CorrelationPairSection("Shared IMEI", pair.commonImeis) }
                item { CorrelationPairSection("Shared IMSI", pair.commonImsis) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

@Composable
private fun CorrelationPairSection(title: String, values: List<String>) {
    Column(Modifier.padding(top = 10.dp)) {
        Text("$title (${values.size})", style = MaterialTheme.typography.titleSmall)
        Text(if (values.isEmpty()) "None" else values.take(50).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        if (values.size > 50) Text("+${values.size - 50} more", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CorrelationLinkDialog(
    workspace: CaseWorkspace,
    link: GraphEdge,
    label: (String) -> String,
    onDismiss: () -> Unit
) {
    val records = remember(workspace, link) {
        workspace.datasets.flatMap { dataset ->
            dataset.records.filter { r ->
                (r.number == link.source && r.otherParty == link.target) ||
                    (r.number == link.target && r.otherParty == link.source)
            }.map { LinkedRecord(dataset.name, it) }
        }.sortedBy { it.record.dateTime }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${label(link.source)} ↔ ${label(link.target)}") },
        text = {
            Column {
                Text("${records.size} matching interaction(s) across ${link.datasets.size} CDR(s)", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(records.take(1500)) { item ->
                        val r = item.record
                        ListItem(
                            headlineContent = { Text(r.dateTime.ifBlank { "Time unavailable" }) },
                            supportingContent = { Text("${item.datasetName} • ${r.direction} • ${r.duration.takeIf { it.isNotBlank() }?.let { "${it}s" } ?: "duration unavailable"}") }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}
