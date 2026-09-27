package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.*
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

@Composable
fun CdrMovementMap(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val context = LocalContext.current
    var fromText by remember(points) { mutableStateOf("") }
    var toText by remember(points) { mutableStateOf("") }
    var playing by remember(points) { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    fun parse(value: String): Long? {
        if (value.isBlank()) return null
        val patterns = listOf(
            "dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    val fromMillis = parse(fromText)
    val toMillis = parse(toText)
    val filterValid = (fromText.isBlank() || fromMillis != null) &&
        (toText.isBlank() || toMillis != null) &&
        (fromMillis == null || toMillis == null || fromMillis <= toMillis)

    val filteredPoints = remember(points, fromText, toText) {
        if (!filterValid) points else points.filter { point ->
            val time = parse(point.at)
            if (time == null) false
            else (fromMillis == null || time >= fromMillis) && (toMillis == null || time <= toMillis)
        }
    }

    var step by remember(filteredPoints) { mutableIntStateOf((filteredPoints.size - 1).coerceAtLeast(0)) }

    LaunchedEffect(playing, filteredPoints) {
        if (!playing || filteredPoints.isEmpty()) return@LaunchedEffect
        if (step >= filteredPoints.lastIndex) step = 0
        while (playing && step < filteredPoints.lastIndex) {
            delay(900)
            step++
        }
        playing = false
    }

    Column(modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Movement period", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { playing = false; fromText = it },
                    label = { Text("From: DD-MM-YYYY HH:MM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { playing = false; toText = it },
                    label = { Text("To: DD-MM-YYYY HH:MM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!filterValid) {
                    Text("Invalid date/time range.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (fromText.isNotBlank() || toText.isNotBlank()) {
                    Text("${filteredPoints.size} of ${points.size} mapped records in selected period", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { playing = false; fromText = ""; toText = "" }, Modifier.fillMaxWidth()) {
                        Text("Reset movement period")
                    }
                } else {
                    Text("All ${points.size} mapped records", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (filterValid && filteredPoints.isEmpty()) {
            Text(
                "No mapped movement points are available in the selected period.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
            return@Column
        }

        val activePoints = if (filterValid) filteredPoints else points
        val safeStep = step.coerceIn(0, (activePoints.size - 1).coerceAtLeast(0))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (safeStep >= activePoints.lastIndex) step = 0; playing = !playing },
                enabled = filterValid && activePoints.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(if (playing) "Pause" else "Play movement") }
            OutlinedButton(onClick = { playing = false; step = 0 }, enabled = activePoints.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("First point") }
            OutlinedButton(onClick = { playing = false; step = activePoints.lastIndex }, enabled = activePoints.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("All points") }
        }

        if (activePoints.isNotEmpty()) {
            Text(
                if (playing || safeStep < activePoints.lastIndex)
                    "Playback: ${safeStep + 1} / ${activePoints.size} • ${activePoints[safeStep].at.ifBlank { "Time unavailable" }}"
                else "Showing all ${activePoints.size} mapped records",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    minZoomLevel = 3.0
                    maxZoomLevel = 20.0
                    mapRef = this
                }
            },
            update = { map ->
                map.overlays.clear()
                if (activePoints.isEmpty()) return@AndroidView
                val visible = if (safeStep >= activePoints.lastIndex) activePoints else activePoints.take(safeStep + 1)
                val routePoints = visible.map { OsmGeoPoint(it.latitude, it.longitude) }

                if (routePoints.size > 1) {
                    map.overlays.add(Polyline().apply {
                        setPoints(routePoints)
                        outlinePaint.strokeWidth = 7f
                    })
                }

                visible.forEachIndexed { index, point ->
                    map.overlays.add(Marker(map).apply {
                        position = OsmGeoPoint(point.latitude, point.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = point.tower.ifBlank { "Mapped CDR point" }
                        snippet = point.at.ifBlank { "Time unavailable" }
                        subDescription = when {
                            index == 0 -> "First mapped record"
                            index == visible.lastIndex && visible.size < activePoints.size -> "Current playback point"
                            index == visible.lastIndex -> "Last mapped record"
                            else -> "CDR mapped record"
                        }
                    })
                }

                routePoints.lastOrNull()?.let { current ->
                    if (visible.size < activePoints.size) {
                        map.controller.setZoom(maxOf(map.zoomLevelDouble, 15.0))
                        map.controller.animateTo(current)
                    } else if (routePoints.size == 1) {
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(current)
                    } else {
                        val north = routePoints.maxOf { it.latitude }
                        val south = routePoints.minOf { it.latitude }
                        val east = routePoints.maxOf { it.longitude }
                        val west = routePoints.minOf { it.longitude }
                        map.post { map.zoomToBoundingBox(BoundingBox(north, east, south, west), true, 72) }
                    }
                }
                map.invalidate()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            playing = false
            mapRef?.onDetach()
        }
    }
}
