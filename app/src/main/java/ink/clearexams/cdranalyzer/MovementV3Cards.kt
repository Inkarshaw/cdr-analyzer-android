package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
