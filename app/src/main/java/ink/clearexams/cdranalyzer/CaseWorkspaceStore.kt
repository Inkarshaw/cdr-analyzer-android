package ink.clearexams.cdranalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CaseWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val crimeNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val datasets: List<CdrDataset> = emptyList()
)

data class CdrDataset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val importedAt: Long = System.currentTimeMillis(),
    val records: List<CdrRecord> = emptyList()
)

data class CommonContact(
    val number: String,
    val datasetCount: Int,
    val totalInteractions: Int,
    val datasetNames: List<String>
)

class CaseWorkspaceStore(private val context: Context) {
    private val root: File by lazy { File(context.filesDir, "cdr_cases").apply { mkdirs() } }

    fun list(): List<CaseWorkspace> = root.listFiles()
        ?.filter { it.extension == "json" }
        ?.mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun load(id: String): CaseWorkspace? {
        val file = File(root, "$id.json")
        return if (file.exists()) runCatching { decode(file.readText()) }.getOrNull() else null
    }

    fun save(workspace: CaseWorkspace): CaseWorkspace {
        val saved = workspace.copy(updatedAt = System.currentTimeMillis())
        File(root, "${saved.id}.json").writeText(encode(saved).toString())
        return saved
    }

    fun create(title: String, crimeNumber: String = ""): CaseWorkspace =
        save(CaseWorkspace(title = title.trim().ifBlank { "Untitled case" }, crimeNumber = crimeNumber.trim()))

    fun addDataset(workspace: CaseWorkspace, name: String, records: List<CdrRecord>): CaseWorkspace {
        val dataset = CdrDataset(name = name.ifBlank { "CDR ${workspace.datasets.size + 1}" }, records = records)
        return save(workspace.copy(datasets = workspace.datasets + dataset))
    }

    fun updateNotes(workspace: CaseWorkspace, notes: String): CaseWorkspace = save(workspace.copy(notes = notes))

    fun delete(id: String): Boolean = File(root, "$id.json").delete()

    fun commonContacts(workspace: CaseWorkspace, minimumDatasets: Int = 2): List<CommonContact> {
        if (workspace.datasets.size < minimumDatasets) return emptyList()
        val perDataset = workspace.datasets.associate { dataset ->
            dataset.name to dataset.records
                .map { it.otherParty.ifBlank { it.number } }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
        }
        val allNumbers = perDataset.values.flatMap { it.keys }.toSet()
        return allNumbers.mapNotNull { number ->
            val hits = perDataset.filterValues { it.containsKey(number) }
            if (hits.size < minimumDatasets) null else CommonContact(
                number = number,
                datasetCount = hits.size,
                totalInteractions = hits.values.sumOf { it[number] ?: 0 },
                datasetNames = hits.keys.toList()
            )
        }.sortedWith(compareByDescending<CommonContact> { it.datasetCount }.thenByDescending { it.totalInteractions })
    }

    private fun encode(w: CaseWorkspace): JSONObject = JSONObject().apply {
        put("id", w.id); put("title", w.title); put("crimeNumber", w.crimeNumber); put("notes", w.notes)
        put("createdAt", w.createdAt); put("updatedAt", w.updatedAt)
        put("datasets", JSONArray().apply { w.datasets.forEach { put(encodeDataset(it)) } })
    }

    private fun encodeDataset(d: CdrDataset): JSONObject = JSONObject().apply {
        put("id", d.id); put("name", d.name); put("importedAt", d.importedAt)
        put("records", JSONArray().apply { d.records.forEach { r -> put(JSONObject().apply {
            put("number",r.number); put("otherParty",r.otherParty); put("direction",r.direction); put("dateTime",r.dateTime)
            put("duration",r.duration); put("imei",r.imei); put("imsi",r.imsi); put("cellId",r.cellId); put("lac",r.lac)
            put("latitude",r.latitude); put("longitude",r.longitude)
        }) } })
    }

    private fun decode(text: String): CaseWorkspace {
        val o = JSONObject(text); val ds = o.optJSONArray("datasets") ?: JSONArray()
        return CaseWorkspace(
            id=o.getString("id"), title=o.optString("title","Untitled case"), crimeNumber=o.optString("crimeNumber"), notes=o.optString("notes"),
            createdAt=o.optLong("createdAt"), updatedAt=o.optLong("updatedAt"),
            datasets=(0 until ds.length()).map { decodeDataset(ds.getJSONObject(it)) }
        )
    }

    private fun decodeDataset(o: JSONObject): CdrDataset {
        val rs=o.optJSONArray("records") ?: JSONArray()
        return CdrDataset(id=o.getString("id"),name=o.optString("name","CDR"),importedAt=o.optLong("importedAt"),records=(0 until rs.length()).map { i ->
            val r=rs.getJSONObject(i); CdrRecord(r.optString("number"),r.optString("otherParty"),r.optString("direction"),r.optString("dateTime"),r.optString("duration"),r.optString("imei"),r.optString("imsi"),r.optString("cellId"),r.optString("lac"),r.optString("latitude"),r.optString("longitude"))
        })
    }
}
