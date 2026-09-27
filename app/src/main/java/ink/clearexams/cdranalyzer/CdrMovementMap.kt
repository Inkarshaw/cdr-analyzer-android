package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

private fun movementTowerKey(point: GeoPoint): String = point.tower.ifBlank {
    "${"%.5f".format(Locale.US, point.latitude)}, ${"%.5f".format(Locale.US, point.longitude)}"
}

@Composable
fun CdrMovementMap(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val context = LocalContext.current
    var fromText by remember(points) { mutableStateOf("") }
    var toText by remember(points) { mutableStateOf("") }
    var playing by remember(points) { mutableStateOf(false) }
    var showVisits by remember { mutableStateOf(false) }
    var showTransitions by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    fun parse(value: String): Long? {
        if (value.isBlank()) return null
        val patterns = listOf("dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    val fromMillis = parse(fromText)
    val toMillis = parse(toText)
    val filterValid = (fromText.isBlank() || fromMillis != null) && (toText.isBlank() || toMillis != null) && (fromMillis == null || toMillis == null || fromMillis <= toMillis)
    val filteredPoints = remember(points, fromText, toText) {
        if (!filterValid) points else points.filter { point ->
            val time = parse(point.at)
            time != null && (fromMillis == null || time >= fromMillis) && (toMillis == null || time <= toMillis)
        }
    }
    val activePoints = if (filterValid) filteredPoints else points
    val intelligence = remember(activePoints) { MovementIntelligence.build(activePoints) }
    var step by remember(filteredPoints) { mutableIntStateOf((filteredPoints.size - 1).coerceAtLeast(0)) }

    LaunchedEffect(playing, filteredPoints) {
        if (!playing || filteredPoints.isEmpty()) return@LaunchedEffect
        if (step >= filteredPoints.lastIndex) step = 0
        while (playing && step < filteredPoints.lastIndex) { delay(900); step++ }
        playing = false
    }

    if (showVisits) TowerVisitsDialog(intelligence.visits, activePoints) { showVisits = false }
    if (showTransitions) MovementTransitionsDialog(intelligence.transitions, activePoints) { showTransitions = false }

    Column(modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Movement period", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(fromText, { playing = false; fromText = it }, label = { Text("From: DD-MM-YYYY HH:MM") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(toText, { playing = false; toText = it }, label = { Text("To: DD-MM-YYYY HH:MM") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!filterValid) Text("Invalid date/time range.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else if (fromText.isNotBlank() || toText.isNotBlank()) {
                    Text("${filteredPoints.size} of ${points.size} mapped records in selected period", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ playing = false; fromText = ""; toText = "" }, Modifier.fillMaxWidth()) { Text("Reset movement period") }
                } else Text("All ${points.size} mapped records", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (filterValid && filteredPoints.isEmpty()) { Text("No mapped movement points are available in the selected period.", modifier = Modifier.padding(12.dp)); return@Column }

        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Movement Intelligence", style = MaterialTheme.typography.titleSmall)
                Text("${intelligence.visits.size} towers • ${intelligence.repeatedTowers} repeated towers • ${intelligence.transitions.size} tower transitions", style = MaterialTheme.typography.bodySmall)
                intelligence.visits.firstOrNull()?.let { top -> Text("Most observed: ${top.tower} • ${top.records} record(s) • first ${top.firstSeen} • last ${top.lastSeen}", style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showVisits = true }, Modifier.weight(1f), enabled = intelligence.visits.isNotEmpty()) { Text("Tower Visits") }
                    OutlinedButton({ showTransitions = true }, Modifier.weight(1f), enabled = intelligence.transitions.isNotEmpty()) { Text("Transitions") }
                }
                Text("Observed span is the interval between the first and last CDR records at a tower; it is not continuous presence.", style = MaterialTheme.typography.labelSmall)
            }
        }

        val safeStep = step.coerceIn(0, (activePoints.size - 1).coerceAtLeast(0))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (safeStep >= activePoints.lastIndex) step = 0; playing = !playing }, enabled = filterValid && activePoints.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (playing) "Pause" else "Play movement") }
            OutlinedButton({ playing = false; step = 0 }, enabled = activePoints.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("First point") }
            OutlinedButton({ playing = false; step = activePoints.lastIndex }, enabled = activePoints.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("All points") }
        }
        if (activePoints.isNotEmpty()) Text(if (playing || safeStep < activePoints.lastIndex) "Playback: ${safeStep + 1} / ${activePoints.size} • ${activePoints[safeStep].at.ifBlank { "Time unavailable" }}" else "Showing all ${activePoints.size} mapped records", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))

        AndroidView(modifier = Modifier.fillMaxWidth().height(360.dp), factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName; Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            MapView(ctx).apply { setMultiTouchControls(true); minZoomLevel = 3.0; maxZoomLevel = 20.0; mapRef = this }
        }, update = { map ->
            map.overlays.clear(); if (activePoints.isEmpty()) return@AndroidView
            val visible = if (safeStep >= activePoints.lastIndex) activePoints else activePoints.take(safeStep + 1)
            val routePoints = visible.map { OsmGeoPoint(it.latitude, it.longitude) }
            if (routePoints.size > 1) map.overlays.add(Polyline().apply { setPoints(routePoints); outlinePaint.strokeWidth = 7f })
            visible.forEachIndexed { index, point -> map.overlays.add(Marker(map).apply { position = OsmGeoPoint(point.latitude, point.longitude); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = point.tower.ifBlank { "Mapped CDR point" }; snippet = point.at.ifBlank { "Time unavailable" }; subDescription = when { index == 0 -> "First mapped record"; index == visible.lastIndex && visible.size < activePoints.size -> "Current playback point"; index == visible.lastIndex -> "Last mapped record"; else -> "CDR mapped record" } }) }
            routePoints.lastOrNull()?.let { current ->
                if (visible.size < activePoints.size) { map.controller.setZoom(maxOf(map.zoomLevelDouble, 15.0)); map.controller.animateTo(current) }
                else if (routePoints.size == 1) { map.controller.setZoom(16.0); map.controller.setCenter(current) }
                else { val north=routePoints.maxOf{it.latitude}; val south=routePoints.minOf{it.latitude}; val east=routePoints.maxOf{it.longitude}; val west=routePoints.minOf{it.longitude}; map.post { map.zoomToBoundingBox(BoundingBox(north,east,south,west),true,72) } }
            }; map.invalidate()
        })
    }
    DisposableEffect(Unit) { onDispose { playing = false; mapRef?.onDetach() } }
}

@Composable
private fun TowerVisitsDialog(visits: List<MovementIntelligence.TowerVisit>, points: List<GeoPoint>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<MovementIntelligence.TowerVisit?>(null) }
    selected?.let { visit ->
        val observations = points.filter { movementTowerKey(it) == visit.tower }
        MovementObservationsDialog("Tower ${visit.tower}", observations) { selected = null }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tower Visits") }, text = {
        LazyColumn(Modifier.heightIn(max = 520.dp)) { items(visits) { visit ->
            ListItem(
                headlineContent = { Text(visit.tower) },
                supportingContent = { Text("${visit.records} record(s) • observed span ${visit.observedSpanMinutes} min\nFirst: ${visit.firstSeen}\nLast: ${visit.lastSeen}\nTap to inspect observations") },
                modifier = Modifier.clickable { selected = visit }
            ); HorizontalDivider()
        } }
    }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
private fun MovementTransitionsDialog(transitions: List<MovementIntelligence.Transition>, points: List<GeoPoint>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<MovementIntelligence.Transition?>(null) }
    selected?.let { transition ->
        val observations = points.filter { movementTowerKey(it) == transition.fromTower || movementTowerKey(it) == transition.toTower }
        MovementObservationsDialog("${transition.fromTower} → ${transition.toTower}", observations, transition.at) { selected = null }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Movement Transitions") }, text = {
        if (transitions.isEmpty()) Text("No tower-to-tower transitions detected.") else LazyColumn(Modifier.heightIn(max = 520.dp)) { items(transitions) { transition ->
            ListItem(
                headlineContent = { Text("${transition.fromTower} → ${transition.toTower}") },
                supportingContent = { Text("${transition.at}${transition.gapMinutes?.let { " • $it min after previous record" } ?: ""}\nTap to inspect related observations") },
                modifier = Modifier.clickable { selected = transition }
            ); HorizontalDivider()
        } }
    }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
private fun MovementObservationsDialog(title: String, observations: List<GeoPoint>, focusTime: String = "", onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("${observations.size} mapped CDR observation(s)", style = MaterialTheme.typography.bodySmall)
                if (focusTime.isNotBlank()) Text("Transition recorded at: $focusTime", style = MaterialTheme.typography.labelMedium)
                Text("Coordinates represent the mapped tower/location supplied or resolved for the CDR record; they do not establish the handset's exact position.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(observations) { point ->
                        ListItem(
                            headlineContent = { Text(point.at.ifBlank { "Time unavailable" }) },
                            supportingContent = {
                                Text("Tower: ${movementTowerKey(point)}\nLatitude: ${"%.6f".format(Locale.US, point.latitude)} • Longitude: ${"%.6f".format(Locale.US, point.longitude)}")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Back") } }
    )
}
