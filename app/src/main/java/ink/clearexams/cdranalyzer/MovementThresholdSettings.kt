package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun MovementThresholdSettings(
    thresholds: MovementIntelligence.Thresholds,
    onChange: (MovementIntelligence.Thresholds) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var distance by remember(thresholds.rapidDistanceKm) { mutableStateOf(thresholds.rapidDistanceKm.toString()) }
    var rapidMinutes by remember(thresholds.rapidWindowMinutes) { mutableStateOf(thresholds.rapidWindowMinutes.toString()) }
    var gapMinutes by remember(thresholds.longGapMinutes) { mutableStateOf(thresholds.longGapMinutes.toString()) }
    var returnMinutes by remember(thresholds.returnWindowMinutes) { mutableStateOf(thresholds.returnWindowMinutes.toString()) }

    fun apply() {
        val d = distance.toDoubleOrNull()
        val r = rapidMinutes.toLongOrNull()
        val g = gapMinutes.toLongOrNull()
        val ret = returnMinutes.toLongOrNull()
        if (d != null && d > 0 && r != null && r > 0 && g != null && g > 0 && ret != null && ret > 0) {
            onChange(MovementIntelligence.Thresholds(d, r, g, ret))
        }
    }

    Card(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Review thresholds", style = MaterialTheme.typography.titleSmall)
                    Text("Rapid: ≥${thresholds.rapidDistanceKm} km / ${thresholds.rapidWindowMinutes} min • Long gap: ${thresholds.longGapMinutes} min • Return: ${thresholds.returnWindowMinutes} min", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Adjust") }
            }
            if (expanded) {
                OutlinedTextField(distance, { distance = it }, label = { Text("Rapid-change distance (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rapidMinutes, { rapidMinutes = it }, label = { Text("Rapid-change window (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(gapMinutes, { gapMinutes = it }, label = { Text("Long-gap threshold (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(returnMinutes, { returnMinutes = it }, label = { Text("Return-pattern window (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { apply() }, modifier = Modifier.weight(1f)) { Text("Apply") }
                    OutlinedButton(onClick = {
                        val defaults = MovementIntelligence.Thresholds()
                        distance = defaults.rapidDistanceKm.toString(); rapidMinutes = defaults.rapidWindowMinutes.toString(); gapMinutes = defaults.longGapMinutes.toString(); returnMinutes = defaults.returnWindowMinutes.toString(); onChange(defaults)
                    }, modifier = Modifier.weight(1f)) { Text("Reset defaults") }
                }
                Text("Thresholds only control automated review flags; they do not determine or prove actual handset movement.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
