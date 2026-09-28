package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UnusualTowerReviewCard(
    towers: List<MovementIntelligence.UnusualTowerReview>,
    onTowerSelected: (MovementIntelligence.UnusualTowerReview) -> Unit
) {
    if (towers.isEmpty()) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Unusual Tower Review", style = MaterialTheme.typography.titleSmall)
            Text("${towers.size} tower(s) meet the current review rules", style = MaterialTheme.typography.bodySmall)
            towers.take(8).forEach { item ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onTowerSelected(item) },
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.tower, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            SuggestionChip(onClick = { onTowerSelected(item) }, label = { Text("Review ${item.reviewScore}") })
                        }
                        Text("${item.observations} observation(s) • ${item.connectedTowers} connected tower(s)", style = MaterialTheme.typography.bodySmall)
                        Text("First: ${item.firstSeen} • Last: ${item.lastSeen}", style = MaterialTheme.typography.labelSmall)
                        Text(item.reasons.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                "Review rules flag rare or short-span tower observations with limited repeated-network context. These are prompts for manual review, not findings that a tower, device, or person is suspicious.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TowerHeatActivityCard(
    towers: List<MovementIntelligence.HeatTower>,
    heatMapEnabled: Boolean,
    onHeatMapToggle: (Boolean) -> Unit,
    onTowerSelected: (MovementIntelligence.HeatTower) -> Unit
) {
    if (towers.isEmpty()) return
    val top = towers.take(10)
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Tower Heat & Activity Map", style = MaterialTheme.typography.titleSmall)
                    Text("${towers.size} mapped tower(s) ranked by observation density", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = heatMapEnabled, onCheckedChange = onHeatMapToggle)
            }
            val hottest = towers.firstOrNull()
            hottest?.let {
                Text("Highest activity: ${it.tower} • ${it.observations} record(s) • ${"%.1f".format(it.sharePercent)}% of mapped observations", style = MaterialTheme.typography.bodySmall)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(top) { item ->
                    Surface(
                        Modifier.widthIn(min = 150.dp, max = 220.dp).clickable { onTowerSelected(item) },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.tower, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text("${item.observations} observation(s)", style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(
                                progress = { item.relativeIntensity.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Day ${item.dayObservations} • Night ${item.nightObservations}", style = MaterialTheme.typography.labelSmall)
                            Text("${"%.1f".format(item.sharePercent)}% of mapped records", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(
                if (heatMapEnabled) "Heat mode is ON. Larger/stronger circles represent more mapped observations at a tower." else "Turn on heat mode to replace the movement route with activity-density circles.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Heat intensity reflects record frequency at mapped tower coordinates only. It does not show exact handset position, coverage area, or confirmed presence within the displayed circle.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
