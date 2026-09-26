package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun CdrMovementMap(
    points: List<ink.clearexams.cdranalyzer.GeoPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxWidth().height(360.dp),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
            }
        },
        update = { map ->
            map.overlays.clear()
            val routePoints = points.map { GeoPoint(it.latitude, it.longitude) }

            if (routePoints.size > 1) {
                val route = Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.strokeWidth = 7f
                }
                map.overlays.add(route)
            }

            val unique = points.distinctBy { "${it.latitude},${it.longitude}" }
            unique.forEachIndexed { index, point ->
                val marker = Marker(map).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = point.tower
                    snippet = point.at.ifBlank { "Time unavailable" }
                    subDescription = when {
                        index == 0 -> "First mapped tower"
                        index == unique.lastIndex -> "Last mapped tower"
                        else -> "CDR tower"
                    }
                }
                map.overlays.add(marker)
            }

            if (routePoints.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(routePoints.first())
            } else {
                val north = routePoints.maxOf { it.latitude }
                val south = routePoints.minOf { it.latitude }
                val east = routePoints.maxOf { it.longitude }
                val west = routePoints.minOf { it.longitude }
                map.post {
                    map.zoomToBoundingBox(BoundingBox(north, east, south, west), true, 72)
                }
            }
            map.invalidate()
        }
    )
}
