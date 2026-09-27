package ink.clearexams.cdranalyzer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaseReportExporter {
    fun export(context: Context, workspace: CaseWorkspace, filter: CaseTimeFilter): File {
        val filtered = CaseTimeFiltering.apply(workspace, filter)
        val timeline = InvestigationTimeline.build(filtered)
        val graph = RelationshipGraph.build(filtered)
        val store = CaseWorkspaceStore(context)
        val common = store.commonContacts(filtered)
        val records = CaseTimeFiltering.count(filtered)
        val text = buildString {
            appendLine("CDR INVESTIGATION REPORT")
            appendLine("========================")
            appendLine("Case: ${workspace.title}")
            if (workspace.crimeNumber.isNotBlank()) appendLine("Crime No.: ${workspace.crimeNumber}")
            appendLine("Generated: ${SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(Date())}")
            appendLine("Datasets: ${workspace.datasets.size}")
            appendLine("Records analysed: $records")
            if (filter.active) appendLine("Analysis period: ${filter.fromText.ifBlank { "Beginning" }} to ${filter.toText.ifBlank { "End" }}")
            appendLine()
            appendLine("SUMMARY")
            appendLine("-------")
            appendLine("Unique numbers: ${graph.nodes.size}")
            appendLine("Communication links: ${graph.edges.size}")
            appendLine("IMEI changes detected: ${timeline.deviceChanges}")
            appendLine("IMSI changes detected: ${timeline.simChanges}")
            appendLine("Tower changes detected: ${timeline.towerChanges}")
            appendLine()
            appendLine("TOP CONTACTS")
            appendLine("------------")
            timeline.frequentContacts.take(20).forEachIndexed { i, p -> appendLine("${i+1}. ${p.first} — ${p.second} events") }
            appendLine()
            appendLine("COMMON CONTACTS ACROSS CDRs")
            appendLine("---------------------------")
            if (common.isEmpty()) appendLine("None detected in the selected period.")
            common.take(50).forEachIndexed { i, c -> appendLine("${i+1}. ${c.number} — ${c.totalInteractions} interactions across ${c.datasetCount} CDRs") }
            appendLine()
            appendLine("STRONGEST COMMUNICATION LINKS")
            appendLine("-----------------------------")
            graph.edges.take(50).forEachIndexed { i, e -> appendLine("${i+1}. ${e.source} <-> ${e.target} — ${e.interactions} interactions") }
            appendLine()
            appendLine("LATE-NIGHT CONTACTS (23:00–05:00)")
            appendLine("--------------------------------")
            if (timeline.lateNightContacts.isEmpty()) appendLine("None detected.")
            timeline.lateNightContacts.take(30).forEachIndexed { i, p -> appendLine("${i+1}. ${p.first} — ${p.second} events") }
            appendLine()
            appendLine("FLAGGED TIMELINE EVENTS")
            appendLine("-----------------------")
            val flagged = timeline.events.filter { it.flags.isNotEmpty() }
            if (flagged.isEmpty()) appendLine("No automatically flagged events.")
            flagged.take(250).forEach { e -> appendLine("${e.dateTime} | ${e.number} | ${e.type} | ${e.flags.joinToString()} | ${e.dataset}") }
            appendLine()
            appendLine("INVESTIGATOR NOTES")
            appendLine("------------------")
            appendLine(workspace.notes.ifBlank { "No case notes recorded." })
            appendLine()
            appendLine("NOTE: Automated CDR patterns and co-occurrences are investigative leads and should be verified against source records and other evidence.")
        }
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safe = workspace.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(50).ifBlank { "case" }
        return File(dir, "CDR_Report_${safe}_${System.currentTimeMillis()}.txt").apply { writeText(text) }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CDR investigation report"))
    }
}
