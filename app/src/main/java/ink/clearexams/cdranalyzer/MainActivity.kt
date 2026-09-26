package ink.clearexams.cdranalyzer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.InputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

data class CdrRecord(val number:String="",val otherParty:String="",val direction:String="",val dateTime:String="",val duration:String="",val imei:String="",val imsi:String="",val cellId:String="",val lac:String="")
data class Summary(val records:Int=0,val contacts:Int=0,val incoming:Int=0,val outgoing:Int=0)
data class ColumnMap(val number:Int=-1,val other:Int=-1,val direction:Int=-1,val dateTime:Int=-1,val duration:Int=-1,val imei:Int=-1,val imsi:Int=-1,val cell:Int=-1,val lac:Int=-1)
data class DeviceChange(val at:String,val oldImei:String,val newImei:String,val oldImsi:String,val newImsi:String)
data class ContactTag(val name:String="",val relation:String="")

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("contact_tags", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CdrApp() } }
    }

    private fun loadTags(): Map<String, ContactTag> {
        val output = mutableMapOf<String, ContactTag>()
        prefs.all.forEach { (key, value) ->
            val raw = value as? String ?: return@forEach
            val parts = raw.split("|", limit = 2)
            output[key] = ContactTag(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
        }
        return output
    }

    private fun saveTag(number: String, tag: ContactTag) {
        prefs.edit().putString(number, "${tag.name}|${tag.relation}").apply()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CdrApp() {
        var rows by remember { mutableStateOf<List<CdrRecord>>(emptyList()) }
        var fileName by remember { mutableStateOf("No CDR loaded") }
        var error by remember { mutableStateOf<String?>(null) }
        var tab by remember { mutableIntStateOf(0) }
        var search by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf(loadTags()) }
        var editNumber by remember { mutableStateOf<String?>(null) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val stream = contentResolver.openInputStream(uri)
                    if (stream != null) {
                        rows = readWorkbook(stream)
                        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "CDR file"
                        error = null
                    } else error = "Unable to open file"
                } catch (e: Exception) { error = e.message ?: "Unable to read file" }
            }
        }

        val filtered = remember(rows, search, tags) {
            if (search.isBlank()) rows else rows.filter { record ->
                val tag = tags[record.otherParty] ?: tags[record.number]
                listOf(record.number, record.otherParty, record.direction, record.dateTime, record.imei, record.imsi, record.cellId, record.lac, tag?.name.orEmpty(), tag?.relation.orEmpty())
                    .any { it.contains(search, ignoreCase = true) }
            }
        }
        val summary = remember(rows) { Summary(rows.size, rows.map { it.otherParty }.filter { it.isNotBlank() }.distinct().size, rows.count { normalizeDirection(it.direction)=="Incoming" }, rows.count { normalizeDirection(it.direction)=="Outgoing" }) }

        Scaffold(topBar = { TopAppBar(title = { Column { Text("CDR Analyzer"); Text("Native • Local analysis", style = MaterialTheme.typography.labelSmall) } }) }) { padding ->
            Column(Modifier.padding(padding).padding(12.dp).fillMaxSize()) {
                Button(onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import CDR file") }
                Text(fileName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stat("Records", summary.records.toString(), Modifier.weight(1f)); Stat("Contacts", summary.contacts.toString(), Modifier.weight(1f)) }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stat("Incoming", summary.incoming.toString(), Modifier.weight(1f)); Stat("Outgoing", summary.outgoing.toString(), Modifier.weight(1f)) }
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search name / number / IMEI / IMSI / tower") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, modifier = Modifier.padding(top = 8.dp)) {
                    listOf("Calls", "Contacts", "Devices", "Towers", "Movement", "Notes").forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
                }
                when (tab) {
                    0 -> RecordList(filtered, tags) { editNumber = it }
                    1 -> ContactList(filtered, tags) { editNumber = it }
                    2 -> DeviceList(filtered)
                    3 -> TowerList(filtered)
                    4 -> EmptyFeature("Movement", "Chronological tower movement will be plotted after tower-coordinate enrichment.")
                    else -> EmptyFeature("Investigation notes", "Local case notes are the next persistence module.")
                }
            }
        }

        editNumber?.let { number ->
            TagDialog(number, tags[number], onDismiss = { editNumber = null }) { tag ->
                saveTag(number, tag)
                tags = tags.toMutableMap().apply { put(number, tag) }
                editNumber = null
            }
        }
    }

    @Composable
    private fun TagDialog(number: String, current: ContactTag?, onDismiss: () -> Unit, onSave: (ContactTag) -> Unit) {
        var name by remember(number) { mutableStateOf(current?.name.orEmpty()) }
        var relation by remember(number) { mutableStateOf(current?.relation ?: "Other") }
        var customRelation by remember(number) { mutableStateOf(if (current?.relation in listOf("Suspect","Victim","Witness","Associate","Family")) "" else current?.relation.orEmpty()) }
        var expanded by remember { mutableStateOf(false) }
        val standard = listOf("Suspect", "Victim", "Witness", "Associate", "Family", "Other")

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tag contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(number)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name / known identity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Relation: $relation") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            standard.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { relation = option; expanded = false }) }
                        }
                    }
                    if (relation == "Other") {
                        OutlinedTextField(value = customRelation, onValueChange = { customRelation = it }, label = { Text("Custom relation / description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { Button(onClick = { onSave(ContactTag(name.trim(), if (relation == "Other") customRelation.trim().ifBlank { "Other" } else relation)) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable private fun Stat(label: String, value: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(10.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall); Text(label, style = MaterialTheme.typography.labelMedium) } } }
    private fun display(number: String, tags: Map<String, ContactTag>): String { val tag = tags[number]; return if (tag != null && tag.name.isNotBlank()) "${tag.name} ($number)" else number }

    @Composable
    private fun RecordList(rows: List<CdrRecord>, tags: Map<String, ContactTag>, onTag: (String) -> Unit) {
        if (rows.isEmpty()) { EmptyFeature("Import or search CDR", "No matching records."); return }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows.take(1000)) { record ->
                val number = record.otherParty.ifBlank { record.number }
                val details = mutableListOf<String>()
                tags[number]?.relation?.takeIf { it.isNotBlank() }?.let(details::add)
                normalizeDirection(record.direction).takeIf { it.isNotBlank() }?.let(details::add)
                record.dateTime.takeIf { it.isNotBlank() }?.let(details::add)
                record.duration.takeIf { it.isNotBlank() }?.let { details.add("${it}s") }
                ListItem(headlineContent = { Text(display(number, tags).ifBlank { "Unknown" }) }, supportingContent = { Text(details.joinToString(" • ")) }, modifier = Modifier.clickable { if (number.isNotBlank()) onTag(number) })
                HorizontalDivider()
            }
        }
    }

    @Composable
    private fun ContactList(rows: List<CdrRecord>, tags: Map<String, ContactTag>, onTag: (String) -> Unit) {
        val contacts = rows.filter { it.otherParty.isNotBlank() }.groupingBy { it.otherParty }.eachCount().entries.sortedByDescending { it.value }
        LazyColumn(Modifier.fillMaxSize()) {
            items(contacts) { entry ->
                val details = mutableListOf<String>()
                tags[entry.key]?.relation?.takeIf { it.isNotBlank() }?.let(details::add)
                details.add("${entry.value} interactions")
                ListItem(headlineContent = { Text(display(entry.key, tags)) }, supportingContent = { Text(details.joinToString(" • ")) }, modifier = Modifier.clickable { onTag(entry.key) })
                HorizontalDivider()
            }
        }
    }

    @Composable
    private fun DeviceList(rows: List<CdrRecord>) {
        val imeis = rows.filter { it.imei.isNotBlank() }.groupBy { it.imei }
        if (imeis.isEmpty()) { EmptyFeature("Device / SIM changes", "No IMEI column was detected in this file."); return }
        val changes = detectDeviceChanges(rows)
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Detected changes: ${changes.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp)) }
            if (changes.isEmpty()) item { Text("No sequential IMEI/IMSI change detected.", modifier = Modifier.padding(12.dp)) }
            else items(changes) { change -> ListItem(headlineContent = { Text(change.at.ifBlank { "Time unavailable" }) }, supportingContent = { Text("IMEI: ${change.oldImei.ifBlank { "—" }} → ${change.newImei.ifBlank { "—" }}\nIMSI: ${change.oldImsi.ifBlank { "—" }} → ${change.newImsi.ifBlank { "—" }}") }); HorizontalDivider() }
            item { Text("Device usage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp)) }
            items(imeis.entries.toList()) { entry ->
                val ordered = entry.value.filter { it.dateTime.isNotBlank() }.sortedBy { it.dateTime }
                val sims = entry.value.map { it.imsi }.filter { it.isNotBlank() }.distinct()
                ListItem(headlineContent = { Text("IMEI ${entry.key}") }, supportingContent = { Text("First use: ${ordered.firstOrNull()?.dateTime ?: "Unknown"}\nLast use: ${ordered.lastOrNull()?.dateTime ?: "Unknown"}\nIMSI: ${sims.joinToString()}\nRecords: ${entry.value.size}") })
                HorizontalDivider()
            }
        }
    }

    @Composable private fun TowerList(rows: List<CdrRecord>) { val towers = rows.filter { it.cellId.isNotBlank() }.groupingBy { "${it.lac}/${it.cellId}" }.eachCount().entries.sortedByDescending { it.value }; if (towers.isEmpty()) EmptyFeature("Tower analysis", "No Cell ID column was detected.") else SimpleList(towers.map { "LAC/Cell ${it.key} — ${it.value} records" }) }
    @Composable private fun SimpleList(lines: List<String>) { LazyColumn(Modifier.fillMaxSize()) { items(lines) { line -> ListItem(headlineContent = { Text(line) }); HorizontalDivider() } } }
    @Composable private fun EmptyFeature(title: String, body: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) } } }

    private fun detectDeviceChanges(rows: List<CdrRecord>): List<DeviceChange> { val ordered = rows.filter { it.imei.isNotBlank() || it.imsi.isNotBlank() }.sortedBy { it.dateTime }; val changes = mutableListOf<DeviceChange>(); var previous: CdrRecord? = null; for (current in ordered) { val prior = previous; if (prior != null) { val imeiChanged = prior.imei.isNotBlank() && current.imei.isNotBlank() && prior.imei != current.imei; val imsiChanged = prior.imsi.isNotBlank() && current.imsi.isNotBlank() && prior.imsi != current.imsi; if (imeiChanged || imsiChanged) changes.add(DeviceChange(current.dateTime, prior.imei, current.imei, prior.imsi, current.imsi)) }; previous = current }; return changes }
    private fun normalizeDirection(value: String): String { val s = value.lowercase(); return when { s.contains("incoming") || s == "in" || s.contains("mti") -> "Incoming"; s.contains("outgoing") || s == "out" || s.contains("moc") -> "Outgoing"; s.contains("sms") -> "SMS"; else -> value } }
    private fun norm(value: String) = value.lowercase().replace(" ", "").replace("_", "").replace("-", "")
    private fun find(headers: List<String>, vararg names: String): Int { val normalized = headers.map(::norm); return normalized.indexOfFirst { cell -> names.any { name -> cell.contains(norm(name)) } } }
    private fun detect(headers: List<String>) = ColumnMap(number = find(headers,"callingnumber","msisdn","anumber","subscriber"), other = find(headers,"callednumber","otherparty","bnumber","diallednumber","connectednumber"), direction = find(headers,"calltype","direction","type"), dateTime = find(headers,"datetime","calltime","starttime","date"), duration = find(headers,"duration","callduration"), imei = find(headers,"imei"), imsi = find(headers,"imsi"), cell = find(headers,"cellid","celltower","cgi"), lac = find(headers,"lac","locationareacode"))
    private fun readWorkbook(input: InputStream): List<CdrRecord> { input.use { stream -> WorkbookFactory.create(stream).use { workbook -> val sheet = workbook.getSheetAt(0); val formatter = DataFormatter(); val header = sheet.getRow(0) ?: return emptyList(); val headers = (0 until header.lastCellNum).map { formatter.formatCellValue(header.getCell(it)).trim() }; val map = detect(headers); val output = mutableListOf<CdrRecord>(); fun cell(row: Row, index: Int) = if (index < 0) "" else formatter.formatCellValue(row.getCell(index)).trim(); for (index in 1..sheet.lastRowNum) { val row = sheet.getRow(index) ?: continue; val record = CdrRecord(cell(row,map.number),cell(row,map.other),cell(row,map.direction),cell(row,map.dateTime),cell(row,map.duration),cell(row,map.imei),cell(row,map.imsi),cell(row,map.cell),cell(row,map.lac)); if (listOf(record.number,record.otherParty,record.dateTime,record.imei,record.cellId).any { it.isNotBlank() }) output.add(record) }; return output } } }
}
