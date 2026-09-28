package ink.clearexams.cdranalyzer

data class CorrelationDatasetProfile(
    val name: String,
    val subjectNumber: String?,
    val records: Int,
    val uniqueContacts: Int,
    val uniqueTowers: Int,
    val uniqueImeis: Int,
    val uniqueImsis: Int
)

data class CorrelationSharedEntity(
    val type: String,
    val value: String,
    val datasetCount: Int,
    val totalRecords: Int,
    val datasetNames: List<String>
)

data class CorrelationDatasetPair(
    val firstDataset: String,
    val secondDataset: String,
    val firstSubject: String?,
    val secondSubject: String?,
    val commonContacts: List<String>,
    val commonTowers: List<String>,
    val commonImeis: List<String>,
    val commonImsis: List<String>,
    val directSubjectInteractions: Int
)

data class CorrelationDashboardData(
    val datasets: List<CorrelationDatasetProfile>,
    val totalRecords: Int,
    val commonContacts: List<CommonContact>,
    val sharedTowers: List<CorrelationSharedEntity>,
    val sharedImeis: List<CorrelationSharedEntity>,
    val sharedImsis: List<CorrelationSharedEntity>,
    val crossDatasetLinks: List<GraphEdge>,
    val pairs: List<CorrelationDatasetPair>
)

object CorrelationDashboard {
    fun build(workspace: CaseWorkspace, store: CaseWorkspaceStore): CorrelationDashboardData {
        val datasetSubjects = workspace.datasets.associate { dataset ->
            val subject = dataset.records
                .map { it.number.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            dataset.name to subject
        }

        fun contact(record: CdrRecord, subject: String?): String? {
            val candidate = record.otherParty.trim().ifBlank { record.number.trim() }
            return candidate.takeIf { it.isNotBlank() && it != subject }
        }
        fun tower(record: CdrRecord): String? =
            record.cellId.trim().takeIf { it.isNotBlank() }?.let { cell ->
                listOf(record.lac.trim(), cell).filter { it.isNotBlank() }.joinToString("/")
            }

        val profiles = workspace.datasets.map { dataset ->
            val subject = datasetSubjects[dataset.name]
            CorrelationDatasetProfile(
                name = dataset.name,
                subjectNumber = subject,
                records = dataset.records.size,
                uniqueContacts = dataset.records.mapNotNull { contact(it, subject) }.distinct().size,
                uniqueTowers = dataset.records.mapNotNull(::tower).distinct().size,
                uniqueImeis = dataset.records.map { it.imei.trim() }.filter { it.isNotBlank() }.distinct().size,
                uniqueImsis = dataset.records.map { it.imsi.trim() }.filter { it.isNotBlank() }.distinct().size
            )
        }

        fun sharedEntities(type: String, extractor: (CdrRecord) -> String?): List<CorrelationSharedEntity> {
            val perDataset = workspace.datasets.associate { dataset ->
                dataset.name to dataset.records.mapNotNull(extractor).groupingBy { it }.eachCount()
            }
            return perDataset.values.flatMap { it.keys }.toSet().mapNotNull { value ->
                val hits = perDataset.filterValues { value in it }
                if (hits.size < 2) null else CorrelationSharedEntity(
                    type = type,
                    value = value,
                    datasetCount = hits.size,
                    totalRecords = hits.values.sumOf { it[value] ?: 0 },
                    datasetNames = hits.keys.sorted()
                )
            }.sortedWith(
                compareByDescending<CorrelationSharedEntity> { it.datasetCount }
                    .thenByDescending { it.totalRecords }
                    .thenBy { it.value }
            )
        }

        val sharedTowers = sharedEntities("Tower", ::tower)
        val sharedImeis = sharedEntities("IMEI") { it.imei.trim().takeIf(String::isNotBlank) }
        val sharedImsis = sharedEntities("IMSI") { it.imsi.trim().takeIf(String::isNotBlank) }
        val graph = RelationshipGraph.build(workspace)
        val crossDatasetLinks = graph.edges.filter { it.datasets.size >= 2 }
            .sortedWith(compareByDescending<GraphEdge> { it.datasets.size }.thenByDescending { it.interactions })

        val pairs = mutableListOf<CorrelationDatasetPair>()
        for (i in 0 until workspace.datasets.size) {
            for (j in i + 1 until workspace.datasets.size) {
                val a = workspace.datasets[i]
                val b = workspace.datasets[j]
                val aSubject = datasetSubjects[a.name]
                val bSubject = datasetSubjects[b.name]
                val aContacts = a.records.mapNotNull { contact(it, aSubject) }.toSet()
                val bContacts = b.records.mapNotNull { contact(it, bSubject) }.toSet()
                val aTowers = a.records.mapNotNull(::tower).toSet()
                val bTowers = b.records.mapNotNull(::tower).toSet()
                val aImeis = a.records.map { it.imei.trim() }.filter { it.isNotBlank() }.toSet()
                val bImeis = b.records.map { it.imei.trim() }.filter { it.isNotBlank() }.toSet()
                val aImsis = a.records.map { it.imsi.trim() }.filter { it.isNotBlank() }.toSet()
                val bImsis = b.records.map { it.imsi.trim() }.filter { it.isNotBlank() }.toSet()
                val direct = if (aSubject.isNullOrBlank() || bSubject.isNullOrBlank()) 0 else {
                    (a.records + b.records).count { record ->
                        val x = record.number.trim()
                        val y = record.otherParty.trim()
                        (x == aSubject && y == bSubject) || (x == bSubject && y == aSubject)
                    }
                }
                pairs += CorrelationDatasetPair(
                    firstDataset = a.name,
                    secondDataset = b.name,
                    firstSubject = aSubject,
                    secondSubject = bSubject,
                    commonContacts = aContacts.intersect(bContacts).sorted(),
                    commonTowers = aTowers.intersect(bTowers).sorted(),
                    commonImeis = aImeis.intersect(bImeis).sorted(),
                    commonImsis = aImsis.intersect(bImsis).sorted(),
                    directSubjectInteractions = direct
                )
            }
        }

        return CorrelationDashboardData(
            datasets = profiles,
            totalRecords = workspace.datasets.sumOf { it.records.size },
            commonContacts = store.commonContacts(workspace),
            sharedTowers = sharedTowers,
            sharedImeis = sharedImeis,
            sharedImsis = sharedImsis,
            crossDatasetLinks = crossDatasetLinks,
            pairs = pairs.sortedWith(
                compareByDescending<CorrelationDatasetPair> { it.commonContacts.size }
                    .thenByDescending { it.commonTowers.size }
                    .thenByDescending { it.directSubjectInteractions }
            )
        )
    }
}
