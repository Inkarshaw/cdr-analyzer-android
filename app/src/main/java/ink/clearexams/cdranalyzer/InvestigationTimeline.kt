package ink.clearexams.cdranalyzer

import java.text.SimpleDateFormat
import java.util.Locale

data class TimelineEvent(
    val timestamp: Long?,
    val dateTime: String,
    val dataset: String,
    val type: String,
    val number: String,
    val direction: String,
    val duration: String,
    val imei: String,
    val imsi: String,
    val tower: String,
    val flags: List<String> = emptyList()
)

data class TimelineSummary(
    val events: List<TimelineEvent>,
    val frequentContacts: List<Pair<String, Int>>,
    val lateNightContacts: List<Pair<String, Int>>,
    val deviceChanges: Int,
    val simChanges: Int,
    val towerChanges: Int
)

object InvestigationTimeline {
    fun build(workspace: CaseWorkspace): TimelineSummary {
        val raw = workspace.datasets.flatMap { dataset ->
            dataset.records.map { r ->
                TimelineEvent(
                    timestamp = parseTime(r.dateTime),
                    dateTime = r.dateTime,
                    dataset = dataset.name,
                    type = classify(r.direction),
                    number = r.otherParty.ifBlank { r.number },
                    direction = r.direction,
                    duration = r.duration,
                    imei = r.imei,
                    imsi = r.imsi,
                    tower = if (r.cellId.isNotBlank()) listOf(r.lac, r.cellId).filter { it.isNotBlank() }.joinToString("/") else ""
                )
            }
        }.sortedWith(compareBy<TimelineEvent> { it.timestamp ?: Long.MAX_VALUE }.thenBy { it.dateTime })

        val counts = raw.filter { it.number.isNotBlank() }.groupingBy { it.number }.eachCount()
        val lateCounts = raw.filter { isLateNight(it.timestamp) && it.number.isNotBlank() }.groupingBy { it.number }.eachCount()
        var deviceChanges = 0; var simChanges = 0; var towerChanges = 0
        val enriched = raw.mapIndexed { index, e ->
            val flags = mutableListOf<String>()
            val prev = raw.getOrNull(index - 1)
            if ((counts[e.number] ?: 0) >= 10) flags += "Frequent contact"
            if (isLateNight(e.timestamp)) flags += "Late-night activity"
            if (prev != null) {
                if (e.imei.isNotBlank() && prev.imei.isNotBlank() && e.imei != prev.imei) { flags += "IMEI changed"; deviceChanges++ }
                if (e.imsi.isNotBlank() && prev.imsi.isNotBlank() && e.imsi != prev.imsi) { flags += "IMSI changed"; simChanges++ }
                if (e.tower.isNotBlank() && prev.tower.isNotBlank() && e.tower != prev.tower) { flags += "Tower changed"; towerChanges++ }
            }
            e.copy(flags = flags)
        }
        return TimelineSummary(
            events = enriched,
            frequentContacts = counts.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value },
            lateNightContacts = lateCounts.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value },
            deviceChanges = deviceChanges,
            simChanges = simChanges,
            towerChanges = towerChanges
        )
    }

    private fun classify(direction: String): String {
        val d = direction.lowercase(Locale.US)
        return when {
            "sms" in d -> "SMS"
            "data" in d || "gprs" in d -> "Data"
            "incoming" in d || d == "in" -> "Incoming"
            "outgoing" in d || d == "out" -> "Outgoing"
            else -> direction.ifBlank { "Event" }
        }
    }

    private fun isLateNight(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }.get(java.util.Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    private fun parseTime(value: String): Long? {
        if (value.isBlank()) return null
        val patterns = listOf("dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss","dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","MM/dd/yyyy HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss")
        for (pattern in patterns) try {
            val f = SimpleDateFormat(pattern, Locale.US); f.isLenient = false
            return f.parse(value)?.time
        } catch (_: Exception) { }
        return value.toLongOrNull()?.let { if (it < 100000000000L) it * 1000 else it }
    }
}
