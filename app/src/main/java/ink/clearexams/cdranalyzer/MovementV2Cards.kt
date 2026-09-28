package ink.clearexams.cdranalyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.max

@Composable
fun MovementReviewSummaryCard(summary: MovementIntelligence.ReviewSummary, onTopTower: (String) -> Unit, onTopRoute: (String, String) -> Unit, onFlags: () -> Unit, onOvernight: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val totalFlags = summary.rapidFlags + summary.longGapFlags + summary.returnFlags
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Movement Review Summary", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Collapse" else "Expand") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V2Metric("Observations", summary.observations.toString(), Modifier.weight(1f))
                V2Metric("Towers", summary.uniqueTowers.toString(), Modifier.weight(1f))
                V2Metric("Transitions", summary.transitions.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V2Metric("Flags", totalFlags.toString(), Modifier.weight(1f))
                V2Metric("Runs", summary.observationRuns.toString(), Modifier.weight(1f))
                V2Metric("Overnight", summary.overnightCandidates.toString(), Modifier.weight(1f))
            }
            if (expanded) {
                summary.topTower?.let { tower ->
                    Surface(Modifier.fillMaxWidth().clickable { onTopTower(tower) }, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Top observed tower", style = MaterialTheme.typography.labelMedium)
                            Text("$tower • ${summary.topTowerRecords} record(s)", style = MaterialTheme.typography.bodyMedium)
                            Text("Tap to highlight mapped observations", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (summary.topRouteFrom != null && summary.topRouteTo != null) {
                    Surface(Modifier.fillMaxWidth().clickable { onTopRoute(summary.topRouteFrom, summary.topRouteTo) }, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Most repeated directional transition", style = MaterialTheme.typography.labelMedium)
                            Text("${summary.topRouteFrom} → ${summary.topRouteTo} • ${summary.topRouteCount} time(s)", style = MaterialTheme.typography.bodyMedium)
                            Text("Tap to highlight observations for the two towers", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Repeated towers: ${summary.repeatedTowers} • Recurring overnight towers: ${summary.recurringOvernightTowers}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onFlags, enabled = totalFlags > 0, modifier = Modifier.weight(1f)) { Text("Flags ($totalFlags)") }
                    OutlinedButton(onClick = onOvernight, enabled = summary.overnightCandidates > 0, modifier = Modifier.weight(1f)) { Text("Overnight (${summary.overnightCandidates})") }
                }
                Text("Summary values are based on timestamped mapped tower observations. They are review aids and do not establish exact handset location or continuous travel.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun TowerRelationshipGraphCard(nodes: List<MovementIntelligence.TowerGraphNode>, edges: List<MovementIntelligence.TowerGraphEdge>, onTowerSelected: (String) -> Unit, onEdgeSelected: (MovementIntelligence.TowerGraphEdge) -> Unit) {
    if (nodes.isEmpty()) return
    val topNodes = nodes.take(5)
    val nodeNames = topNodes.map { it.tower }.toSet()
    val visibleEdges = edges.filter { it.fromTower in nodeNames && it.toTower in nodeNames }.take(10)
    val maxCount = max(1, visibleEdges.maxOfOrNull { it.count } ?: 1)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val positions = listOf(140.dp to 12.dp, 30.dp to 82.dp, 250.dp to 82.dp, 75.dp to 165.dp, 205.dp to 165.dp)
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Tower Relationship Graph", style = MaterialTheme.typography.titleSmall)
            Text("${nodes.size} tower node(s) • ${edges.size} directional link(s)", style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(225.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    fun center(index: Int): Offset { val p = positions[index]; return Offset(p.first.toPx() + 30.dp.toPx(), p.second.toPx() + 24.dp.toPx()) }
                    visibleEdges.forEach { edge ->
                        val a = topNodes.indexOfFirst { it.tower == edge.fromTower }; val b = topNodes.indexOfFirst { it.tower == edge.toTower }
                        if (a >= 0 && b >= 0) drawLine(outline, center(a), center(b), strokeWidth = 2f + (edge.count.toFloat() / maxCount.toFloat()) * 8f)
                    }
                    topNodes.forEachIndexed { index, _ ->
                        drawCircle(surface, 25.dp.toPx(), center(index))
                        drawCircle(primary, 25.dp.toPx(), center(index), style = Stroke(width = 3.dp.toPx()))
                    }
                }
                topNodes.forEachIndexed { index, node ->
                    val p = positions[index]
                    V2NodeChip(node, Modifier.offset(p.first, p.second)) { onTowerSelected(node.tower) }
                }
            }
            Text("Top directional links", style = MaterialTheme.typography.labelMedium)
            visibleEdges.take(6).forEach { edge ->
                Surface(Modifier.fillMaxWidth().clickable { onEdgeSelected(edge) }, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(7.dp)) {
                        Text("${edge.fromTower} → ${edge.toTower}", style = MaterialTheme.typography.bodySmall)
                        Text("${edge.count} transition(s) • avg gap ${"%.0f".format(Locale.US, edge.averageGapMinutes)} min • approx. ${"%.1f".format(Locale.US, edge.averageDistanceKm)} km tower separation", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Line weight reflects repeated directional tower transitions. The graph represents observation relationships, not an exact travel route.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun V2NodeChip(node: MovementIntelligence.TowerGraphNode, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.width(60.dp).height(48.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
            Text(shortTower(node.tower), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text("${node.observations} obs", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TowerClusterAnalysisCard(clusters: List<MovementIntelligence.TowerCluster>, isolatedTowers: List<String>, onClusterSelected: (MovementIntelligence.TowerCluster) -> Unit, onIsolatedTowerSelected: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Tower Cluster Analysis", style = MaterialTheme.typography.titleSmall)
            Text("${clusters.size} repeated-link cluster(s) • ${isolatedTowers.size} tower(s) outside repeated-link clusters", style = MaterialTheme.typography.bodySmall)
            clusters.take(6).forEach { cluster ->
                Surface(Modifier.fillMaxWidth().clickable { onClusterSelected(cluster) }, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Cluster ${cluster.id} • ${cluster.towers.size} tower(s)", style = MaterialTheme.typography.bodyMedium)
                        Text("${cluster.totalObservations} observation(s) • ${cluster.totalTransitions} internal transition(s)", style = MaterialTheme.typography.bodySmall)
                        cluster.dominantTower?.let { Text("Dominant observed tower: $it", style = MaterialTheme.typography.labelSmall) }
                        if (cluster.strongestFrom != null && cluster.strongestTo != null) Text("Strongest link: ${cluster.strongestFrom} → ${cluster.strongestTo} • ${cluster.strongestCount}", style = MaterialTheme.typography.labelSmall)
                        Text("Tap to highlight this cluster on the map", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (isolatedTowers.isNotEmpty()) {
                Text("Towers outside repeated-link clusters", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(isolatedTowers.take(12)) { tower -> AssistChip(onClick = { onIsolatedTowerSelected(tower) }, label = { Text(shortTower(tower)) }) }
                }
            }
            Text("Clusters are connected groups formed from tower-to-tower links observed more than once. They are analytical groupings, not geographic boundaries or confirmed movement zones.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun V2Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(7.dp)) { Text(value, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun shortTower(value: String): String = if (value.length <= 9) value else value.take(8) + "…"
