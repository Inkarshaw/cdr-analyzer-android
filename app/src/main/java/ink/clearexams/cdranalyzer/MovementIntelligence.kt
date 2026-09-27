package ink.clearexams.cdranalyzer

import java.text.SimpleDateFormat
import java.util.Locale

object MovementIntelligence {
    data class TowerVisit(
        val tower: String,
        val records: Int,
        val firstSeen: String,
        val lastSeen: String,
        val observedSpanMinutes: Long
    )

    data class Transition(
        val fromTower: String,
        val toTower: String,
        val at: String,
        val gapMinutes: Long?
    )

    data class Summary(
        val visits: List<TowerVisit>,
        val transitions: List<Transition>,
        val repeatedTowers: Int
    )

    fun build(points: List<GeoPoint>): Summary {
        val timed = points.mapNotNull { point -> parse(point.at)?.let { Triple(point, it, towerKey(point)) } }
            .sortedBy { it.second }

        val visits = timed.groupBy { it.third }.map { (tower, rows) ->
            val sorted = rows.sortedBy { it.second }
            TowerVisit(
                tower = tower,
                records = sorted.size,
                firstSeen = sorted.first().first.at,
                lastSeen = sorted.last().first.at,
                observedSpanMinutes = ((sorted.last().second - sorted.first().second) / 60000L).coerceAtLeast(0)
            )
        }.sortedWith(compareByDescending<TowerVisit> { it.records }.thenByDescending { it.observedSpanMinutes })

        val transitions = mutableListOf<Transition>()
        var previous: Triple<GeoPoint, Long, String>? = null
        for (current in timed) {
            val prior = previous
            if (prior != null && prior.third != current.third) {
                transitions += Transition(
                    fromTower = prior.third,
                    toTower = current.third,
                    at = current.first.at,
                    gapMinutes = ((current.second - prior.second) / 60000L).coerceAtLeast(0)
                )
            }
            previous = current
        }

        return Summary(visits, transitions, visits.count { it.records > 1 })
    }

    private fun towerKey(point: GeoPoint): String = point.tower.ifBlank {
        "${"%.5f".format(Locale.US, point.latitude)}, ${"%.5f".format(Locale.US, point.longitude)}"
    }

    private fun parse(value: String): Long? {
        val patterns = listOf(
            "dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            val result = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())?.time
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }
}
