package ink.clearexams.cdranalyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object CaseBackupManager {
    private const val FORMAT = "clearexams-cdr-case-backup"
    private const val VERSION = 1

    fun export(context: Context, workspace: CaseWorkspace): File {
        val payload = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("case", encodeCase(workspace))
        }
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val safe = workspace.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(50).ifBlank { "case" }
        return File(dir, "CDR_Case_${safe}.cdrbackup").apply { writeText(payload.toString()) }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share case backup"))
    }

    fun restore(context: Context, uri: Uri, store: CaseWorkspaceStore): CaseWorkspace {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read backup file")
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Not a CDR Analyzer case backup" }
        require(root.optInt("version", 0) in 1..VERSION) { "Unsupported backup version" }
        val imported = decodeCase(root.getJSONObject("case"))
        val restored = imported.copy(
            id = UUID.randomUUID().toString(),
            title = imported.title + " (Restored)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            datasets = imported.datasets.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        return store.save(restored)
    }

    private fun encodeCase(w: CaseWorkspace) = JSONObject().apply {
        put("id", w.id); put("title", w.title); put("crimeNumber", w.crimeNumber); put("notes", w.notes); put("incidentDateTime", w.incidentDateTime)
        put("createdAt", w.createdAt); put("updatedAt", w.updatedAt)
        put("datasets", JSONArray().apply { w.datasets.forEach { put(encodeDataset(it)) } })
    }

    private fun encodeDataset(d: CdrDataset) = JSONObject().apply {
        put("id", d.id); put("name", d.name); put("importedAt", d.importedAt)
        put("records", JSONArray().apply { d.records.forEach { r -> put(JSONObject().apply {
            put("number",r.number);put("otherParty",r.otherParty);put("direction",r.direction);put("dateTime",r.dateTime)
            put("duration",r.duration);put("imei",r.imei);put("imsi",r.imsi);put("cellId",r.cellId);put("lac",r.lac)
            put("latitude",r.latitude);put("longitude",r.longitude)
        }) } })
    }

    private fun decodeCase(o: JSONObject): CaseWorkspace {
        val ds=o.optJSONArray("datasets")?:JSONArray()
        return CaseWorkspace(
            id=o.optString("id",UUID.randomUUID().toString()), title=o.optString("title","Restored case"),
            crimeNumber=o.optString("crimeNumber"), notes=o.optString("notes"), createdAt=o.optLong("createdAt"), updatedAt=o.optLong("updatedAt"),
            datasets=(0 until ds.length()).map { decodeDataset(ds.getJSONObject(it)) }, incidentDateTime=o.optString("incidentDateTime")
        )
    }

    private fun decodeDataset(o:JSONObject):CdrDataset {
        val rs=o.optJSONArray("records")?:JSONArray()
        return CdrDataset(o.optString("id",UUID.randomUUID().toString()),o.optString("name","CDR"),o.optLong("importedAt"),(0 until rs.length()).map { i ->
            val r=rs.getJSONObject(i); CdrRecord(r.optString("number"),r.optString("otherParty"),r.optString("direction"),r.optString("dateTime"),r.optString("duration"),r.optString("imei"),r.optString("imsi"),r.optString("cellId"),r.optString("lac"),r.optString("latitude"),r.optString("longitude"))
        })
    }
}
