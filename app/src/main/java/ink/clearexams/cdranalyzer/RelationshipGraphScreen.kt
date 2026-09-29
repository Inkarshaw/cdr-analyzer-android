package ink.clearexams.cdranalyzer

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    val context = LocalContext.current
    fun label(number: String): String = identities.find(workspace.id, number)?.displayLabel ?: number
    val other = if (edge.source == focus) edge.target else edge.source
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var eventFilter by remember { mutableStateOf("") }
    var nightOnly by remember { mutableStateOf(false) }
    val records = remember(workspace, focus, other) {
        workspace.datasets.flatMap { dataset ->
            dataset.records.filter { record ->
                (record.number == focus && record.otherParty == other) ||
                    (record.number == other && record.otherParty == focus)
            }.map { LinkedRecord(dataset.name, it) }
        }.sortedBy { it.record.dateTime }
    }
    val filteredRecords = remember(records, dateFrom, dateTo, eventFilter, nightOnly) {
        val from = pairDateBound(dateFrom, false)
        val to = pairDateBound(dateTo, true)
        records.filter { item ->
            val ts = parseCdrTime(item.record.dateTime)
            val h = ts?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY) }
            (from == null || (ts != null && ts >= from)) &&
                (to == null || (ts != null && ts <= to)) &&
                (eventFilter.isBlank() || item.record.direction.contains(eventFilter, true)) &&
                (!nightOnly || h?.let { it >= 20 || it < 6 } == true)
        }
    }
    fun exportPairCsv() {
        runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeA = focus.filter { it.isLetterOrDigit() }.takeLast(12)
            val safeB = other.filter { it.isLetterOrDigit() }.takeLast(12)
            val file = File(dir, "pair_${safeA}_${safeB}.csv")
            fun csv(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
            file.writeText(buildString {
                appendLine("Dataset,DateTime,A Party,B Party,Direction,Duration,IMEI,IMSI,Tower")
                filteredRecords.forEach { item ->
                    val r = item.record
                    val tower = if (r.cellId.isBlank()) "" else listOf(r.lac, r.cellId).filter { it.isNotBlank() }.joinToString("/")
                    appendLine(listOf(item.datasetName, r.dateTime, r.number, r.otherParty, r.direction, r.duration, r.imei, r.imsi, tower).joinToString(",") { csv(it) })
                }
            })
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "Export pair CSV"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${label(focus)} ↔ ${label(other)}") },
        text = {
            Column {
                Text(
                    "${filteredRecords.size} matching / ${records.size} total communication record(s)",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(dateFrom, { dateFrom = it }, label = { Text("From YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(dateTo, { dateTo = it }, label = { Text("To YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(eventFilter, { eventFilter = it }, label = { Text("Event type contains") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(nightOnly, { nightOnly = !nightOnly }, { Text("Night only") })
                    OutlinedButton(::exportPairCsv, enabled = filteredRecords.isNotEmpty()) { Text("Export CSV") }
                }
                if (filteredRecords.isEmpty()) {
                    Text("No underlying records match the selected pair filters.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 430.dp)) {
                        items(filteredRecords.take(1500)) { item ->
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

private fun pairDateBound(value:String,end:Boolean):Long?{if(value.isBlank())return null;return runCatching{val d=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(value.trim())?:return@runCatching null;Calendar.getInstance().apply{time=d;set(Calendar.HOUR_OF_DAY,if(end)23 else 0);set(Calendar.MINUTE,if(end)59 else 0);set(Calendar.SECOND,if(end)59 else 0);set(Calendar.MILLISECOND,if(end)999 else 0)}.timeInMillis}.getOrNull()}
