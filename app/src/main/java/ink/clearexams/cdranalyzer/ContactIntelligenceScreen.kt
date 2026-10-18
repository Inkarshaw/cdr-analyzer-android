package ink.clearexams.cdranalyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Native counterpart of cdranalysis/js/contacts.js.
 * Rows and exported data remain on-device unless the analyst explicitly shares them.
 * [historicalRows] intentionally ignores only the date/time filters; other CDR filters remain.
 */
internal data class ContactInsight(
    val number: String,
    val records: List<CdrRecord>,
    val calls: Int,
    val incoming: Int,
    val outgoing: Int,
    val sms: Int,
    val durationSeconds: Double,
    val firstCall: CdrRecord?,
    val lastRecord: CdrRecord?,
    val subjects: Set<String>,
    val towers: Set<String>,
    val providers: Set<String>
)

private fun eventKind(r: CdrRecord): String {
    val kind = r.direction.trim().lowercase().replace(" ", "").replace("-", "")
    return when {
        kind.contains("sms") -> "SMS"
        kind.contains("data") || kind.contains("gprs") || kind.contains("internet") -> "Other"
        kind.contains("incoming") || kind == "in" || kind == "mt" || kind.contains("mtc") || kind.contains("mti") -> "Incoming"
        kind.contains("outgoing") || kind == "out" || kind == "mo" || kind.contains("moc") -> "Outgoing"
        kind.contains("call") || kind.contains("voice") -> "Call"
        else -> "Other"
    }
}

private fun eventAt(r: CdrRecord): Long? = parseCdrTime(r.dateTime)
private fun earlier(a: CdrRecord?, b: CdrRecord): CdrRecord =
    if (a == null || (eventAt(b) ?: Long.MAX_VALUE) < (eventAt(a) ?: Long.MAX_VALUE)) b else a

private fun later(a: CdrRecord?, b: CdrRecord): CdrRecord =
    if (a == null || (eventAt(b) ?: Long.MIN_VALUE) > (eventAt(a) ?: Long.MIN_VALUE)) b else a

internal fun contactInsights(rows: List<CdrRecord>, history: List<CdrRecord>): List<ContactInsight> {
    val earliestCalls = mutableMapOf<String, CdrRecord>()
    history.forEach { r ->
        val n = r.otherParty.trim()
        if (n.isNotEmpty() && eventAt(r) != null && eventKind(r) in setOf("Incoming", "Outgoing", "Call")) {
            earliestCalls[n] = earlier(earliestCalls[n], r)
        }
    }
    return rows.asSequence().filter { it.otherParty.isNotBlank() }
        .groupBy { it.otherParty.trim() }
        .map { (number, events) ->
            val types = events.map(::eventKind)
            ContactInsight(
                number = number,
                records = events,
                calls = types.count { it in setOf("Incoming", "Outgoing", "Call") },
                incoming = types.count { it == "Incoming" },
                outgoing = types.count { it == "Outgoing" },
                sms = types.count { it == "SMS" },
                durationSeconds = events.filterIndexed { index, _ -> types[index] in setOf("Incoming", "Outgoing", "Call") }
                    .sumOf { it.duration.replace(",", "").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 },
                firstCall = earliestCalls[number],
                lastRecord = events.filter { eventAt(it) != null }.fold(null as CdrRecord?, ::later),
                subjects = events.map { it.number }.filter { it.isNotBlank() }.toSet(),
                towers = events.map { listOf(it.lac, it.cellId).filter(String::isNotBlank).joinToString("/") }
                    .filter { it.isNotBlank() }.toSet(),
                providers = events.map { it.provider.ifBlank { it.operator } }.filter { it.isNotBlank() }.toSet()
            )
        }
}

internal fun orderedContacts(rows: List<ContactInsight>, sort: String): List<ContactInsight> {
    val fallback = Comparator<ContactInsight> { a, b -> a.number.compareTo(b.number) }
    return when (sort) {
        "First ↑", "First ↓", "Last ↑", "Last ↓" -> {
            val useFirst = sort.startsWith("First")
            val ascending = sort.endsWith("↑")
            rows.sortedWith(Comparator { a, b ->
                val at = eventAt(if (useFirst) a.firstCall ?: CdrRecord() else a.lastRecord ?: CdrRecord())
                val bt = eventAt(if (useFirst) b.firstCall ?: CdrRecord() else b.lastRecord ?: CdrRecord())
                when {
                    at == null && bt == null -> fallback.compare(a, b)
                    at == null -> 1
                    bt == null -> -1
                    at != bt -> if (ascending) at.compareTo(bt) else bt.compareTo(at)
                    else -> fallback.compare(a, b)
                }
            })
        }
        "Number" -> rows.sortedWith(fallback)
        else -> rows.sortedWith(compareByDescending<ContactInsight> { it.records.size }.then(fallback))
    }
}

private fun csvCell(v: String): String {
    // Spreadsheet applications can execute untrusted formula-looking cells.
    val escaped = if (v.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" + v else v
    return "\"" + escaped.replace("\"", "\"\"") + "\""
}
internal fun exportContactsCsv(contacts: List<ContactInsight>, tags: Map<String, ContactTag>): String {
    val header = listOf("B Party", "Name", "Tag", "Records", "Calls", "Incoming", "Outgoing",
        "SMS", "Duration Seconds", "Historical First Call", "Scoped Last Record", "Towers", "Providers", "Subjects")
    val lines = contacts.map { c ->
        listOf(c.number, tags[c.number]?.name.orEmpty(), tags[c.number]?.relation.orEmpty(),
            c.records.size.toString(), c.calls.toString(), c.incoming.toString(), c.outgoing.toString(),
            c.sms.toString(), c.durationSeconds.toString(), c.firstCall?.dateTime.orEmpty(),
            c.lastRecord?.dateTime.orEmpty(), c.towers.joinToString("; "), c.providers.joinToString("; "),
            c.subjects.joinToString("; ")).joinToString(",") { csvCell(it) }
    }
    return (listOf(header.joinToString(",") { csvCell(it) }) + lines).joinToString("\r\n") + "\r\n"
}

@Composable
fun ContactIntelligenceScreen(
    filteredRows: List<CdrRecord>,
    historicalRows: List<CdrRecord>,
    tags: Map<String, ContactTag>,
    onTag: (String) -> Unit
) {
    val context = LocalContext.current
    var useHistory by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var eventFilter by remember { mutableStateOf("All") }
    var tagFilter by remember { mutableStateOf("All tags") }
    var sort by remember { mutableStateOf("Records ↓") }
    var commonOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var openProfile by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf("") }

    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingCsv.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open export destination")
            }.isSuccess
            Toast.makeText(context, if (saved) "Contacts CSV saved" else "CSV export failed", Toast.LENGTH_LONG).show()
        }
    }
    val scopeRows = if (useHistory) historicalRows else filteredRows
    val eventRows = remember(scopeRows, eventFilter) {
        scopeRows.filter { r ->
            when (eventFilter) {
                "Calls" -> eventKind(r) in setOf("Incoming", "Outgoing", "Call")
                "SMS" -> eventKind(r) == "SMS"
                else -> true
            }
        }
    }
    val contactList = remember(eventRows, historicalRows, tags, query, tagFilter, commonOnly, sort) {
        val matches = contactInsights(eventRows, historicalRows).filter { item ->
            val tag = tags[item.number]
            val matchesText = query.isBlank() || listOf(item.number, tag?.name.orEmpty(),
                tag?.relation.orEmpty(), item.subjects.joinToString(), item.providers.joinToString())
                .any { it.contains(query.trim(), ignoreCase = true) }
            val matchesTag = tagFilter == "All tags" ||
                (tagFilter == "Unclassified" && tag?.relation.isNullOrBlank()) ||
                tag?.relation.equals(tagFilter, ignoreCase = true)
            matchesText && matchesTag && (!commonOnly || item.subjects.size >= 2)
        }
        orderedContacts(matches, sort)
    }
    val visibleNumbers = contactList.map { it.number }.toSet()

    fun copyNumbers() {
        val numbers = selected.filter { it.matches(Regex("[+]?[0-9]{10,15}")) }.sorted()
        if (numbers.isEmpty()) {
            Toast.makeText(context, "Select numeric contacts to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CDR contact numbers", numbers.joinToString("\n")))
        Toast.makeText(context, "Copied ${numbers.size} numbers • clear clipboard when finished", Toast.LENGTH_LONG).show()
    }
    fun saveCsv() {
        val chosen = if (selected.isEmpty()) contactList else contactList.filter { it.number in selected }
        if (chosen.isEmpty()) {
            Toast.makeText(context, "No visible contacts to export", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCsv = exportContactsCsv(chosen, tags)
        csvSaver.launch("cdr_contacts.csv")
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Contacts intelligence", style = MaterialTheme.typography.titleLarge)
        Text("${contactList.size} contacts • First call ignores date/time filters; Last follows the selected view.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, label = { Text("Find number, name, tag, subject or provider") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(!useHistory, { useHistory = false }, { Text("Filtered") }) }
            item { FilterChip(useHistory, { useHistory = true }, { Text("Historical*") }) }
            item { FilterChip(commonOnly, { commonOnly = !commonOnly }, { Text("Common contacts (2+ subjects)") }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("All", "Calls", "SMS")) { opt ->
                FilterChip(eventFilter == opt, { eventFilter = opt }, { Text(opt) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("All tags", "Unclassified", "Suspect", "Victim", "Witness", "Associate", "Family", "Service", "Other")) { opt ->
                FilterChip(tagFilter == opt, { tagFilter = opt }, { Text(opt) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("Records ↓", "First ↑", "First ↓", "Last ↑", "Last ↓", "Number")) { opt ->
                FilterChip(sort == opt, { sort = opt }, { Text(opt) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { selected = selected + visibleNumbers }, modifier = Modifier.weight(1f)) { Text("Select visible") }
            OutlinedButton(onClick = { selected = emptySet() }, modifier = Modifier.weight(1f)) { Text("Clear (${selected.size})") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(::copyNumbers, modifier = Modifier.weight(1f), enabled = selected.isNotEmpty()) { Text("Copy selected") }
            OutlinedButton(::saveCsv, modifier = Modifier.weight(1f), enabled = contactList.isNotEmpty()) { Text("Export CSV") }
        }
        Text("*Historical removes date/time limits only; other main filters still apply. Clipboard/export happen only on request.",
            style = MaterialTheme.typography.labelSmall)
        LazyColumn(Modifier.weight(1f)) {
            if (contactList.isEmpty()) item { Text("No matching contacts. Try clearing filters.", modifier = Modifier.padding(12.dp)) }
            items(contactList, key = { it.number }) { item ->
                val t = tags[item.number]
                val label = t?.name?.takeIf { it.isNotBlank() }?.let { "$it (${item.number})" } ?: item.number
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.number in selected, onCheckedChange = { checked ->
                                selected = if (checked) selected + item.number else selected - item.number
                            })
                            Column(Modifier.weight(1f).clickable { openProfile = item.number }) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(t?.relation?.takeIf { it.isNotBlank() },
                                    "${item.records.size} events • ${item.calls} calls • ${item.sms} SMS")
                                    .joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onTag(item.number) }) { Text("Tag") }
                        }
                        Text("In ${item.incoming} / Out ${item.outgoing} • ${item.durationSeconds.toLong()} sec • Towers ${item.towers.size}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("First call: ${item.firstCall?.dateTime ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Text("Last record: ${item.lastRecord?.dateTime ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openProfile = item.number }) { Text("Profile / timeline") }
                            TextButton(onClick = {
                                val url = "https://clearexams.ink/policedocuments/cdr_request_generator.html?from=cdr-analyzer&number=${Uri.encode(item.number)}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }) { Text("Request") }
                        }
                    }
                }
            }
        }
    }
    openProfile?.let { number ->
        val scope = contactList.firstOrNull { it.number == number }
        val all = historicalRows.filter { it.otherParty.trim() == number }
        val visible = scope?.records.orEmpty()
        val timed = visible.sortedWith(compareBy<CdrRecord> { eventAt(it) ?: Long.MAX_VALUE }.thenBy { it.dateTime })
        AlertDialog(
            onDismissRequest = { openProfile = null },
            title = { Text(tags[number]?.name?.ifBlank { number } ?: number) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(number, style = MaterialTheme.typography.labelMedium)
                    Text("Historical first call: ${all.filter { eventKind(it) in setOf("Incoming", "Outgoing", "Call") && eventAt(it) != null }.minByOrNull { eventAt(it) ?: Long.MAX_VALUE }?.dateTime ?: "—"}")
                    Text("Current view: ${visible.size} events • ${scope?.calls ?: 0} calls • ${scope?.sms ?: 0} SMS")
                    Text("Last event: ${scope?.lastRecord?.dateTime ?: "—"}")
                    Text("Subjects: ${scope?.subjects?.joinToString().orEmpty().ifBlank { "—" }}")
                    Text("Providers: ${scope?.providers?.joinToString().orEmpty().ifBlank { "—" }}")
                    Text("Top towers", style = MaterialTheme.typography.titleSmall)
                    val towers = visible.filter { it.cellId.isNotBlank() }
                        .groupingBy { listOf(it.lac, it.cellId).filter(String::isNotBlank).joinToString("/") }
                        .eachCount().entries.sortedByDescending { it.value }.take(8)
                    if (towers.isEmpty()) Text("No tower records")
                    towers.forEach { e -> Text("${e.key} • ${e.value} event(s)", style = MaterialTheme.typography.bodySmall) }
                    Text("Contact activity timeline (first 100 events)", style = MaterialTheme.typography.titleSmall)
                    if (timed.isEmpty()) Text("No records in this view")
                    timed.take(100).forEach { event ->
                        HorizontalDivider()
                        Text("${event.dateTime.ifBlank { "Unknown time" }} • ${event.direction} • ${event.duration.ifBlank { "0" }}s",
                            style = MaterialTheme.typography.bodySmall)
                        Text("CDR ${event.number} • ${event.towerAddress.ifBlank { event.cellId }.ifBlank { "Tower unknown" }}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    if (timed.size > 100) Text("+${timed.size - 100} additional events", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { openProfile = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { openProfile = null; onTag(number) }) { Text("Edit tag") } }
        )
    }
}
