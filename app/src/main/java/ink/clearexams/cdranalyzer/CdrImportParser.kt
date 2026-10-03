package ink.clearexams.cdranalyzer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

data class CdrSourceSummary(
    val fileName: String,
    val sheetName: String,
    val rows: Int,
    val recognizedColumns: Int,
    val rawColumns: Int
)

data class CdrImportResult(
    val records: List<CdrRecord>,
    val sources: List<CdrSourceSummary>,
    val warnings: List<String>
)

private data class ImportColumnMap(
    val number: Int = -1,
    val other: Int = -1,
    val direction: Int = -1,
    val dateTime: Int = -1,
    val duration: Int = -1,
    val imei: Int = -1,
    val imsi: Int = -1,
    val cell: Int = -1,
    val lac: Int = -1,
    val latitude: Int = -1,
    val longitude: Int = -1,
    val towerAddress: Int = -1,
    val mainCity: Int = -1,
    val subCity: Int = -1,
    val roaming: Int = -1,
    val provider: Int = -1,
    val operator: Int = -1
) {
    fun recognizedCount(): Int = listOf(
        number, other, direction, dateTime, duration, imei, imsi, cell, lac, latitude, longitude,
        towerAddress, mainCity, subCity, roaming, provider, operator
    ).count { it >= 0 }
}

object CdrImportParser {
    fun parse(contentResolver: ContentResolver, uri: Uri): CdrImportResult {
        val fileName = displayName(contentResolver, uri)
        val lower = fileName.lowercase()
        return if (lower.endsWith(".csv")) parseCsv(contentResolver, uri, fileName)
        else parseWorkbook(contentResolver, uri, fileName)
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0) ?: "CDR file"
            }
        }
        return uri.lastPathSegment?.substringAfterLast("/") ?: "CDR file"
    }

    private fun parseWorkbook(resolver: ContentResolver, uri: Uri, fileName: String): CdrImportResult {
        val output = mutableListOf<CdrRecord>()
        val sources = mutableListOf<CdrSourceSummary>()
        val warnings = mutableListOf<String>()
        resolver.openInputStream(uri)?.use { rawStream ->
            // ContentResolver streams from Android's Storage Access Framework are not
            // guaranteed to support mark/reset. Apache POI's WorkbookFactory requires
            // a mark-capable InputStream, so always buffer the selected document.
            BufferedInputStream(rawStream).use { stream ->
                WorkbookFactory.create(stream).use { workbook ->
                val formatter = DataFormatter()
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    val headerRowIndex = detectHeaderRow(sheet.lastRowNum) { rowIndex ->
                        val row = sheet.getRow(rowIndex)
                        if (row == null) emptyList() else (0 until row.lastCellNum.coerceAtLeast(0)).map {
                            formatter.formatCellValue(row.getCell(it)).trim()
                        }
                    }
                    if (headerRowIndex < 0) continue
                    val header = sheet.getRow(headerRowIndex) ?: continue
                    val headers = uniqueHeaders((0 until header.lastCellNum.coerceAtLeast(0)).map {
                        formatter.formatCellValue(header.getCell(it)).trim()
                    })
                    val map = detect(headers)
                    if (map.recognizedCount() < 2) {
                        warnings += "$fileName / ${sheet.sheetName}: no standard CDR columns mapped"
                        continue
                    }
                    var added = 0
                    for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val raw = linkedMapOf<String, String>()
                        headers.forEachIndexed { index, name -> raw[name] = formatter.formatCellValue(row.getCell(index)).trim() }
                        recordFromRow(
                            raw = raw,
                            headers = headers,
                            map = map,
                            sourceFile = fileName,
                            sourceSheet = sheet.sheetName,
                            value = { index -> cell(formatter, row, index) }
                        )?.let { output += it; added++ }
                    }
                    sources += CdrSourceSummary(fileName, sheet.sheetName, added, map.recognizedCount(), headers.size)
                }
            }
        } ?: warnings.add("$fileName: unable to open file")
        if (sources.isEmpty() && warnings.isEmpty()) warnings += "$fileName: no usable worksheet found"
        return CdrImportResult(output, sources, warnings)
    }

    private fun parseCsv(resolver: ContentResolver, uri: Uri, fileName: String): CdrImportResult {
        val output = mutableListOf<CdrRecord>()
        val sources = mutableListOf<CdrSourceSummary>()
        val warnings = mutableListOf<String>()
        resolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val lines = reader.lineSequence().toList()
                if (lines.isEmpty()) return@use
                val parsed = lines.map(::parseCsvLine)
                val headerRowIndex = detectHeaderRow(parsed.lastIndex) { idx -> parsed.getOrNull(idx).orEmpty() }
                if (headerRowIndex < 0) { warnings += "$fileName: no usable CSV header found"; return@use }
                val headers = uniqueHeaders(parsed[headerRowIndex])
                val map = detect(headers)
                if (map.recognizedCount() < 2) { warnings += "$fileName: no standard CDR columns mapped"; return@use }
                var added = 0
                for (rowIndex in (headerRowIndex + 1)..parsed.lastIndex) {
                    val values = parsed[rowIndex]
                    val raw = linkedMapOf<String, String>()
                    headers.forEachIndexed { index, name -> raw[name] = values.getOrElse(index) { "" }.trim() }
                    recordFromRow(
                        raw = raw,
                        headers = headers,
                        map = map,
                        sourceFile = fileName,
                        sourceSheet = "CSV",
                        value = { index -> if (index < 0) "" else values.getOrElse(index) { "" }.trim() }
                    )?.let { output += it; added++ }
                }
                sources += CdrSourceSummary(fileName, "CSV", added, map.recognizedCount(), headers.size)
            }
        } ?: warnings.add("$fileName: unable to open file")
        return CdrImportResult(output, sources, warnings)
    }

    private fun recordFromRow(
        raw: Map<String, String>,
        headers: List<String>,
        map: ImportColumnMap,
        sourceFile: String,
        sourceSheet: String,
        value: (Int) -> String
    ): CdrRecord? {
        val record = CdrRecord(
            number = value(map.number),
            otherParty = value(map.other),
            direction = value(map.direction),
            dateTime = value(map.dateTime),
            duration = value(map.duration),
            imei = value(map.imei),
            imsi = value(map.imsi),
            cellId = value(map.cell),
            lac = value(map.lac),
            latitude = value(map.latitude),
            longitude = value(map.longitude),
            sourceFile = sourceFile,
            sourceSheet = sourceSheet,
            rawRow = raw,
            towerAddress = value(map.towerAddress),
            mainCity = value(map.mainCity),
            subCity = value(map.subCity),
            roaming = value(map.roaming),
            provider = value(map.provider),
            operator = value(map.operator)
        )
        val hasData = listOf(
            record.number, record.otherParty, record.dateTime, record.imei, record.imsi, record.cellId,
            record.latitude, record.longitude
        ).any { it.isNotBlank() }
        return record.takeIf { hasData }
    }

    private fun cell(formatter: DataFormatter, row: Row, index: Int): String =
        if (index < 0) "" else formatter.formatCellValue(row.getCell(index)).trim()

    private fun detectHeaderRow(lastRow: Int, rowProvider: (Int) -> List<String>): Int {
        if (lastRow < 0) return -1
        var bestIndex = -1
        var bestScore = 0
        for (i in 0..minOf(lastRow, 25)) {
            val row = rowProvider(i)
            if (row.isEmpty()) continue
            val score = detect(uniqueHeaders(row)).recognizedCount()
            if (score > bestScore) { bestScore = score; bestIndex = i }
        }
        return if (bestScore >= 2) bestIndex else -1
    }

    private fun uniqueHeaders(input: List<String>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return input.mapIndexed { index, original ->
            val base = original.trim().ifBlank { "Column ${index + 1}" }
            val count = (seen[base] ?: 0) + 1
            seen[base] = count
            if (count == 1) base else "$base ($count)"
        }
    }

    private fun norm(value: String): String = value.lowercase()
        .replace(" ", "").replace("_", "").replace("-", "").replace(".", "").replace("/", "")
        .replace("(", "").replace(")", "")

    private fun find(headers: List<String>, vararg names: String): Int {
        val normalized = headers.map(::norm)
        return normalized.indexOfFirst { candidate -> names.any { target -> candidate == norm(target) || candidate.contains(norm(target)) } }
    }

    private fun detect(headers: List<String>): ImportColumnMap = ImportColumnMap(
        number = find(headers, "callingnumber", "msisdn", "anumber", "aparty", "subscriber", "cdrnumber", "mobilenumber"),
        other = find(headers, "callednumber", "otherparty", "bnumber", "bparty", "diallednumber", "connectednumber", "destinationnumber"),
        direction = find(headers, "calltype", "direction", "eventtype", "type"),
        dateTime = find(headers, "datetime", "calldatetime", "startdatetime", "starttime", "eventtime", "dateandtime", "date time", "date"),
        duration = find(headers, "duration", "callduration", "durationsec", "callseconds"),
        imei = find(headers, "imei", "equipmentidentity"),
        imsi = find(headers, "imsi", "subscriberidentity"),
        cell = find(headers, "firstcellid", "cellid", "celltower", "cgi", "firstcgi", "cell"),
        lac = find(headers, "lac", "locationareacode", "firstlac"),
        latitude = find(headers, "towerlatitude", "celllatitude", "latitude", "lat"),
        longitude = find(headers, "towerlongitude", "celllongitude", "longitude", "lng", "lon", "long"),
        towerAddress = find(headers, "firstaddress", "toweraddress", "celladdress", "locationaddress"),
        mainCity = find(headers, "maincity", "city"),
        subCity = find(headers, "subcity", "locality", "area"),
        roaming = find(headers, "roaming"),
        provider = find(headers, "provider", "serviceprovider", "networkprovider"),
        operator = find(headers, "operator", "networkoperator")
    )

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(ch)
            }
            i++
        }
        out += current.toString()
        return out
    }
}
