package ink.clearexams.cdranalyzer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CdrFilters(
    val aParty: String = "", val bParty: String = "",
    val dateFrom: String = "", val dateTo: String = "",
    val timeFrom: String = "", val timeTo: String = "",
    val eventType: String = "", val imei: String = "", val imsi: String = "",
    val cellId: String = "", val towerAddress: String = "",
    val city: String = "", val subCity: String = "", val roaming: String = "",
    val provider: String = "", val operator: String = "", val sourceFile: String = "",
    val minDuration: String = "", val maxDuration: String = "",
    val callsOnly: Boolean = false, val smsOnly: Boolean = false,
    val nightOnly: Boolean = false, val weekendOnly: Boolean = false
)

fun duplicateSafeRows(rows: List<CdrRecord>): List<CdrRecord> = rows.distinctBy { r ->
    listOf(r.number.trim(), r.otherParty.trim(), normalizedEvent(r.direction), normalizeDateKey(r.dateTime),
        r.duration.trim(), r.imei.trim(), r.imsi.trim(), r.lac.trim(), r.cellId.trim()).joinToString("|")
}

fun applyCdrFilters(rows: List<CdrRecord>, filters: CdrFilters): List<CdrRecord> {
    val fromDate = parseDateOnly(filters.dateFrom, false)
    val toDate = parseDateOnly(filters.dateTo, true)
    val minDur = filters.minDuration.toDoubleOrNull()
    val maxDur = filters.maxDuration.toDoubleOrNull()
    return rows.filter { r ->
        val event = normalizedEvent(r.direction)
        val epoch = parseCdrTime(r.dateTime)
        val calendar = epoch?.let { Calendar.getInstance().apply { timeInMillis = it } }
        val timeMinutes = calendar?.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val weekend = calendar?.let { it.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY) } ?: false
        val night = timeMinutes?.let { it >= 20 * 60 || it < 6 * 60 } ?: false
        val duration = r.duration.replace(",", "").trim().toDoubleOrNull()
        val start = parseHm(filters.timeFrom); val end = parseHm(filters.timeTo)
        val timeOk = timeMinutes?.let { minutes ->
            when {
                start == null && end == null -> true
                start != null && end == null -> minutes >= start
                start == null && end != null -> minutes <= end
                start != null && end != null && start <= end -> minutes in start..end
                start != null && end != null -> minutes >= start || minutes <= end
                else -> true
            }
        } ?: (filters.timeFrom.isBlank() && filters.timeTo.isBlank())
        fun contains(value: String, query: String) = query.isBlank() || value.contains(query, ignoreCase = true)
        contains(r.number, filters.aParty) && contains(r.otherParty, filters.bParty) &&
            (fromDate == null || (epoch != null && epoch >= fromDate)) &&
            (toDate == null || (epoch != null && epoch <= toDate)) && timeOk &&
            contains(event, filters.eventType) && contains(r.imei, filters.imei) && contains(r.imsi, filters.imsi) &&
            contains(r.cellId, filters.cellId) && contains(r.towerAddress, filters.towerAddress) &&
            contains(r.mainCity, filters.city) && contains(r.subCity, filters.subCity) &&
            contains(r.roaming, filters.roaming) && contains(r.provider, filters.provider) &&
            contains(r.operator, filters.operator) && contains(r.sourceFile, filters.sourceFile) &&
            (minDur == null || (duration != null && duration >= minDur)) &&
            (maxDur == null || (duration != null && duration <= maxDur)) &&
            (!filters.callsOnly || event.contains("Call", true) || event in listOf("Incoming", "Outgoing")) &&
            (!filters.smsOnly || event.contains("SMS", true)) && (!filters.nightOnly || night) &&
            (!filters.weekendOnly || weekend)
    }
}

@Composable
fun AnalysisIntegrityCard(rawRows: List<CdrRecord>, duplicateSafe: Boolean, onDuplicateSafeChange: (Boolean) -> Unit) {
    val unique = remember(rawRows) { duplicateSafeRows(rawRows) }
    val duplicateCount = (rawRows.size - unique.size).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Analysis Integrity", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!duplicateSafe, { onDuplicateSafeChange(false) }, { Text("Raw records") })
                FilterChip(duplicateSafe, { onDuplicateSafeChange(true) }, { Text("Duplicate-safe") })
            }
            Text(if (duplicateSafe) "${unique.size} records used • $duplicateCount duplicate candidate(s) excluded" else "${rawRows.size} raw records used • $duplicateCount duplicate candidate(s) detected", style = MaterialTheme.typography.bodySmall)
            Text("Raw imported rows are preserved. Duplicate-safe mode changes analysis calculations only.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AdvancedFiltersPanel(filters: CdrFilters, sourceFiles: List<String>, onChange: (CdrFilters) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CDR Filters", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Advanced") }
            }
            OutlinedTextField(filters.aParty, { onChange(filters.copy(aParty = it)) }, label = { Text("CDR / A Party") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(filters.bParty, { onChange(filters.copy(bParty = it)) }, label = { Text("B Party / connected number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(filters.dateFrom, { onChange(filters.copy(dateFrom = it)) }, label = { Text("Date from YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filters.dateTo, { onChange(filters.copy(dateTo = it)) }, label = { Text("Date to") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(filters.timeFrom, { onChange(filters.copy(timeFrom = it)) }, label = { Text("Time from HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filters.timeTo, { onChange(filters.copy(timeTo = it)) }, label = { Text("Time to HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(filters.callsOnly, { onChange(filters.copy(callsOnly = !filters.callsOnly, smsOnly = false)) }, { Text("Calls") }) }
                item { FilterChip(filters.smsOnly, { onChange(filters.copy(smsOnly = !filters.smsOnly, callsOnly = false)) }, { Text("SMS") }) }
                item { FilterChip(filters.nightOnly, { onChange(filters.copy(nightOnly = !filters.nightOnly)) }, { Text("Night") }) }
                item { FilterChip(filters.weekendOnly, { onChange(filters.copy(weekendOnly = !filters.weekendOnly)) }, { Text("Weekend") }) }
            }
            if (expanded) {
                OutlinedTextField(filters.eventType, { onChange(filters.copy(eventType = it)) }, label = { Text("Event type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(filters.imei, { onChange(filters.copy(imei = it)) }, label = { Text("IMEI") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(filters.imsi, { onChange(filters.copy(imsi = it)) }, label = { Text("IMSI") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(filters.minDuration, { onChange(filters.copy(minDuration = it)) }, label = { Text("Min duration sec") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(filters.maxDuration, { onChange(filters.copy(maxDuration = it)) }, label = { Text("Max duration sec") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(filters.cellId, { onChange(filters.copy(cellId = it)) }, label = { Text("Cell ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(filters.towerAddress, { onChange(filters.copy(towerAddress = it)) }, label = { Text("Tower address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(filters.city, { onChange(filters.copy(city = it)) }, label = { Text("Main city") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(filters.subCity, { onChange(filters.copy(subCity = it)) }, label = { Text("Sub city") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(filters.provider, { onChange(filters.copy(provider = it)) }, label = { Text("Provider") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(filters.operator, { onChange(filters.copy(operator = it)) }, label = { Text("Operator") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(filters.roaming, { onChange(filters.copy(roaming = it)) }, label = { Text("Roaming") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (sourceFiles.isNotEmpty()) {
                    Text("Source file", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(filters.sourceFile.isBlank(), { onChange(filters.copy(sourceFile = "")) }, { Text("All") }) }
                        items(sourceFiles) { source -> FilterChip(filters.sourceFile == source, { onChange(filters.copy(sourceFile = source)) }, { Text(source) }) }
                    }
                }
            }
            val activeCount = listOf(filters.aParty, filters.bParty, filters.dateFrom, filters.dateTo, filters.timeFrom, filters.timeTo, filters.eventType, filters.imei, filters.imsi, filters.cellId, filters.towerAddress, filters.city, filters.subCity, filters.roaming, filters.provider, filters.operator, filters.sourceFile, filters.minDuration, filters.maxDuration).count { it.isNotBlank() } + listOf(filters.callsOnly, filters.smsOnly, filters.nightOnly, filters.weekendOnly).count { it }
            if (activeCount > 0) OutlinedButton(onClick = { onChange(CdrFilters()) }, modifier = Modifier.fillMaxWidth()) { Text("Clear $activeCount active filter(s)") }
        }
    }
}

@Composable
fun DataQualityScreen(rows: List<CdrRecord>) {
    if (rows.isEmpty()) { Box(Modifier.fillMaxSize()) { Text("Import CDR files to inspect data quality.", modifier = Modifier.padding(16.dp)) }; return }
    val unique = remember(rows) { duplicateSafeRows(rows) }
    val duplicates = rows.size - unique.size
    val missingA = rows.count { it.number.isBlank() }; val missingB = rows.count { it.otherParty.isBlank() }
    val missingTime = rows.count { it.dateTime.isBlank() || parseCdrTime(it.dateTime) == null }
    val missingTower = rows.count { it.cellId.isBlank() }; val missingImei = rows.count { it.imei.isBlank() }; val missingImsi = rows.count { it.imsi.isBlank() }
    val validCoordinates = rows.count { validCoordinates(it) }
    val invalidCoordinateRows = rows.count { (it.latitude.isNotBlank() || it.longitude.isNotBlank()) && !validCoordinates(it) }
    val sources = rows.groupBy { "${it.sourceFile.ifBlank { "Unknown file" }} / ${it.sourceSheet.ifBlank { "Unknown sheet" }}" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Data Health & Quality", style = MaterialTheme.typography.titleLarge) }
        item { Text("Structural checks describe imported metadata and never modify raw rows.", style = MaterialTheme.typography.bodySmall) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { QualityMetric("Rows", rows.size.toString(), Modifier.weight(1f)); QualityMetric("Duplicates", duplicates.toString(), Modifier.weight(1f)); QualityMetric("Coordinates", validCoordinates.toString(), Modifier.weight(1f)) } }
        item { Text("Missing / invalid fields", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("A Party missing: $missingA"); Text("B Party missing: $missingB"); Text("Timestamp missing/unparseable: $missingTime"); Text("Cell ID missing: $missingTower"); Text("IMEI missing: $missingImei"); Text("IMSI missing: $missingImsi"); Text("Coordinate rows invalid: $invalidCoordinateRows")
        } } }
        item { Text("Source breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
        items(sources.entries.toList().sortedByDescending { it.value.size }) { entry ->
            val rawColumns = entry.value.flatMap { it.rawRow.keys }.distinct().size
            ListItem(headlineContent = { Text(entry.key) }, supportingContent = { Text("${entry.value.size} row(s) • $rawColumns original column(s)") }); HorizontalDivider()
        }
    }
}

@Composable
private fun QualityMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) { Column(Modifier.padding(8.dp)) { Text(value, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelSmall) } }
}

@Composable
fun ExcelViewScreen(rows: List<CdrRecord>) {
    val rawRows = rows.filter { it.rawRow.isNotEmpty() }
    if (rawRows.isEmpty()) { Box(Modifier.fillMaxSize()) { Text("Original source columns are available for files imported with the parity importer.", modifier = Modifier.padding(16.dp)) }; return }
    val subjects = remember(rawRows) { rawRows.map { it.number }.filter { it.isNotBlank() }.distinct().sorted() }
    val sources = remember(rawRows) { rawRows.map { "${it.sourceFile} / ${it.sourceSheet}" }.distinct().sorted() }
    var subject by remember { mutableStateOf("") }; var source by remember { mutableStateOf("") }; var query by remember { mutableStateOf("") }
    var pageSize by remember { mutableIntStateOf(100) }; var page by remember { mutableIntStateOf(0) }
    val filtered = remember(rawRows, subject, source, query) { rawRows.filter { r -> (subject.isBlank() || r.number == subject) && (source.isBlank() || "${r.sourceFile} / ${r.sourceSheet}" == source) && (query.isBlank() || r.rawRow.values.any { it.contains(query, true) }) } }
    val pageCount = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (page >= pageCount) page = pageCount - 1
    val pageRows = filtered.drop(page * pageSize).take(pageSize)
    val columns = remember(pageRows, filtered) { (if (pageRows.isNotEmpty()) pageRows else filtered.take(50)).flatMap { it.rawRow.keys }.distinct() }
    Column(Modifier.fillMaxSize()) {
        Text("Excel View", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        Text("Original worksheet column names and values. Duplicate-safe analysis does not change this view.", style = MaterialTheme.typography.bodySmall)
        if (subjects.isNotEmpty()) { Text("Subject", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item { FilterChip(subject.isBlank(), { subject = ""; page = 0 }, { Text("All") }) }; items(subjects) { value -> FilterChip(subject == value, { subject = value; page = 0 }, { Text(value) }) } } }
        Text("Source", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item { FilterChip(source.isBlank(), { source = ""; page = 0 }, { Text("All") }) }; items(sources) { value -> FilterChip(source == value, { source = value; page = 0 }, { Text(value) }) } }
        OutlinedTextField(query, { query = it; page = 0 }, label = { Text("Search any original Excel value") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(50, 100, 250).forEach { size -> FilterChip(pageSize == size, { pageSize = size; page = 0 }, { Text("$size rows") }) } }
        Text("${filtered.size} matching row(s) • page ${page + 1} of $pageCount", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Row { columns.forEach { col -> Surface(Modifier.width(190.dp), tonalElevation = 2.dp) { Text(col, modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelMedium) } } }
            LazyColumn(Modifier.heightIn(max = 460.dp)) { items(pageRows) { r -> Row { columns.forEach { col -> Surface(Modifier.width(190.dp)) { Text(r.rawRow[col].orEmpty(), modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall) } } }; HorizontalDivider() } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Previous") }; OutlinedButton(onClick = { page = (page + 1).coerceAtMost(pageCount - 1) }, enabled = page < pageCount - 1) { Text("Next") } }
    }
}

private fun normalizedEvent(value: String): String {
    val s = value.trim().lowercase()
    return when { s.contains("sms") -> "SMS"; s.contains("incoming") || s == "in" || s.contains("mti") -> "Incoming"; s.contains("outgoing") || s == "out" || s.contains("moc") -> "Outgoing"; else -> value.trim() }
}
private fun normalizeDateKey(value: String): String = parseCdrTime(value)?.toString() ?: value.trim()
private fun parseDateOnly(value: String, endOfDay: Boolean): Long? {
    if (value.isBlank()) return null
    return runCatching { val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value.trim()) ?: return@runCatching null; Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0); set(Calendar.MINUTE, if (endOfDay) 59 else 0); set(Calendar.SECOND, if (endOfDay) 59 else 0); set(Calendar.MILLISECOND, if (endOfDay) 999 else 0) }.timeInMillis }.getOrNull()
}
private fun parseHm(value: String): Int? { if (value.isBlank()) return null; val parts = value.trim().split(":"); if (parts.size < 2) return null; val h = parts[0].toIntOrNull() ?: return null; val m = parts[1].toIntOrNull() ?: return null; return if (h in 0..23 && m in 0..59) h * 60 + m else null }
fun parseCdrTime(value: String): Long? {
    if (value.isBlank()) return null
    val patterns = listOf("dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "MM/dd/yyyy HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    for (pattern in patterns) { val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())?.time }.getOrNull(); if (parsed != null) return parsed }
    return value.toLongOrNull()?.let { if (it < 100000000000L) it * 1000 else it }
}
private fun validCoordinates(r: CdrRecord): Boolean { val lat = r.latitude.replace(",", "").trim().toDoubleOrNull(); val lon = r.longitude.replace(",", "").trim().toDoubleOrNull(); return lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 }
