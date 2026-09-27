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
fun RelationshipGraphDialog(workspace: CaseWorkspace, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val identities = remember(context) { NumberIdentityStore(context) }
    val data = remember(workspace) { RelationshipGraph.build(workspace) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GraphNode?>(null) }

    fun label(number: String): String =
        identities.find(workspace.id, number)?.displayLabel ?: number

    val filtered = data.nodes.filter { node ->
        query.isBlank() ||
            node.id.contains(query, ignoreCase = true) ||
            label(node.id).contains(query, ignoreCase = true)
    }

    selected?.let { node ->
        RelationshipNodeDialog(workspace, data, node, identities) { selected = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relationship Graph") },
        text = {
            Column {
                Text(
                    "${data.nodes.size} numbers • ${data.edges.size} communication links",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name / role / number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Text(
                    "Numbers are ranked by observed CDR interactions. Tap a number to inspect its links.",
                    style = MaterialTheme.typography.labelSmall
                )
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(filtered.take(1000)) { node ->
                        val links = RelationshipGraph.neighbors(data, node.id)
                        ListItem(
                            headlineContent = { Text(label(node.id)) },
                            supportingContent = {
                                Text(
                                    "${node.interactions} records • ${links.size} linked number(s) • " +
                                        "${node.datasets} CDR(s) • ${node.imeis} IMEI • ${node.towers} tower(s)"
                                )
                            },
                            modifier = Modifier.clickable { selected = node }
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
private fun RelationshipNodeDialog(
    workspace: CaseWorkspace,
    data: RelationshipGraphData,
    node: GraphNode,
    identities: NumberIdentityStore,
    onDismiss: () -> Unit
) {
    fun label(number: String): String = identities.find(workspace.id, number)?.displayLabel ?: number
    val links = remember(data, node) { RelationshipGraph.neighbors(data, node.id) }
    var selectedEdge by remember { mutableStateOf<GraphEdge?>(null) }

    selectedEdge?.let { edge ->
        RelationshipEdgeDialog(workspace, node.id, edge, identities) { selectedEdge = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label(node.id)) },
        text = {
            Column {
                Text(
                    "${node.interactions} records • ${links.size} direct links",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Connected numbers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (links.isEmpty()) {
                    Text("No direct communication links found.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(links) { edge ->
                            val other = if (edge.source == node.id) edge.target else edge.source
                            ListItem(
                                headlineContent = { Text(label(other)) },
                                supportingContent = {
                                    Text(
                                        "${edge.interactions} interaction(s) • ${edge.datasets.size} CDR dataset(s)\n" +
                                            edge.datasets.joinToString()
                                    )
                                },
                                modifier = Modifier.clickable { selectedEdge = edge }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

@Composable
private fun RelationshipEdgeDialog(
    workspace: CaseWorkspace,
    focus: String,
    edge: GraphEdge,
    identities: NumberIdentityStore,
    onDismiss: () -> Unit
) {
    fun label(number: String): String = identities.find(workspace.id, number)?.displayLabel ?: number
    val other = if (edge.source == focus) edge.target else edge.source
    val records = remember(workspace, focus, other) {
        workspace.datasets.flatMap { dataset ->
            dataset.records.filter { record ->
                (record.number == focus && record.otherParty == other) ||
                    (record.number == other && record.otherParty == focus)
            }.map { LinkedRecord(dataset.name, it) }
        }.sortedBy { it.record.dateTime }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${label(focus)} ↔ ${label(other)}") },
        text = {
            Column {
                Text(
                    "${records.size} matching communication record(s)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (records.isEmpty()) {
                    Text("No underlying records found.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 500.dp)) {
                        items(records.take(1500)) { item ->
                            val record = item.record
                            val tower = if (record.cellId.isNotBlank()) {
                                listOf(record.lac, record.cellId).filter { it.isNotBlank() }.joinToString("/")
                            } else ""
                            ListItem(
                                headlineContent = { Text(record.dateTime.ifBlank { "Time unavailable" }) },
                                supportingContent = {
                                    Text(
                                        listOf(
                                            item.datasetName,
                                            record.direction,
                                            record.duration.takeIf { it.isNotBlank() }?.let { "${it}s" }.orEmpty(),
                                            record.imei.takeIf { it.isNotBlank() }?.let { "IMEI $it" }.orEmpty(),
                                            tower.takeIf { it.isNotBlank() }?.let { "Tower $it" }.orEmpty()
                                        ).filter { it.isNotBlank() }.joinToString(" • ")
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}
