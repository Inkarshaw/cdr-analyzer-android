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

@Composable
fun CdrMovementMap(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val context = LocalContext.current
    var playing by remember(points) { mutableStateOf(false) }
    var step by remember(points) { mutableIntStateOf(points.lastIndex) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(playing, points) {
        if (!playing) return@LaunchedEffect
        if (step >= points.lastIndex) step = 0
        while (playing && step < points.lastIndex) {
            delay(900)
            step++
        }
        playing = false
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { if (step >= points.lastIndex) step = 0; playing = !playing }, Modifier.weight(1f)) {
                Text(if (playing) "Pause" else "Play movement")
            }
            OutlinedButton(onClick = { playing = false; step = 0 }, Modifier.weight(1f)) { Text("First point") }
            OutlinedButton(onClick = { playing = false; step = points.lastIndex }, Modifier.weight(1f)) { Text("All points") }
        }
        Text(
            if (playing || step < points.lastIndex) "Playback: ${step + 1} / ${points.size} • ${points[step].at.ifBlank { "Time unavailable" }}" else "Showing all ${points.size} mapped records",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
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
                val visible = if (step >= points.lastIndex) points else points.take(step + 1)
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
                            index == visible.lastIndex && visible.size < points.size -> "Current playback point"
                            index == visible.lastIndex -> "Last mapped record"
                            else -> "CDR mapped record"
                        }
                    })
                }

                routePoints.lastOrNull()?.let { current ->
                    if (visible.size < points.size) {
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
