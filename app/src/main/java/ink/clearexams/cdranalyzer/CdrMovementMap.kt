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

private fun movementTowerKey(point: GeoPoint): String = point.tower.ifBlank { "${"%.5f".format(Locale.US, point.latitude)}, ${"%.5f".format(Locale.US, point.longitude)}" }
private data class MapFocus(val points: List<GeoPoint>, val label: String)
private const val MOVEMENT_PREFS = "movement_review_settings"

@Composable
fun CdrMovementMap(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(MOVEMENT_PREFS, 0) }
    val defaultThresholds = remember { MovementIntelligence.Thresholds() }
    var fromText by remember(points) { mutableStateOf("") }; var toText by remember(points) { mutableStateOf("") }
    var playing by remember(points) { mutableStateOf(false) }; var showVisits by remember { mutableStateOf(false) }; var showTransitions by remember { mutableStateOf(false) }; var showAnomalies by remember { mutableStateOf(false) }
    var mapFocus by remember { mutableStateOf<MapFocus?>(null) }; var mapRef by remember { mutableStateOf<MapView?>(null) }
    var thresholds by remember {
        mutableStateOf(MovementIntelligence.Thresholds(
            rapidDistanceKm = prefs.getFloat("rapidDistanceKm", defaultThresholds.rapidDistanceKm.toFloat()).toDouble(),
            rapidWindowMinutes = prefs.getLong("rapidWindowMinutes", defaultThresholds.rapidWindowMinutes),
            longGapMinutes = prefs.getLong("longGapMinutes", defaultThresholds.longGapMinutes),
            returnWindowMinutes = prefs.getLong("returnWindowMinutes", defaultThresholds.returnWindowMinutes)
        ))
    }
    fun updateThresholds(value: MovementIntelligence.Thresholds) {
        thresholds = value; playing = false; mapFocus = null
        prefs.edit().putFloat("rapidDistanceKm", value.rapidDistanceKm.toFloat()).putLong("rapidWindowMinutes", value.rapidWindowMinutes).putLong("longGapMinutes", value.longGapMinutes).putLong("returnWindowMinutes", value.returnWindowMinutes).apply()
    }
    fun parse(value: String): Long? { if (value.isBlank()) return null; for (pattern in listOf("dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss")) { val p=runCatching{SimpleDateFormat(pattern,Locale.US).apply{isLenient=false}.parse(value.trim())?.time}.getOrNull(); if(p!=null)return p }; return null }
    val fromMillis=parse(fromText); val toMillis=parse(toText); val filterValid=(fromText.isBlank()||fromMillis!=null)&&(toText.isBlank()||toMillis!=null)&&(fromMillis==null||toMillis==null||fromMillis<=toMillis)
    val filteredPoints=remember(points,fromText,toText){if(!filterValid)points else points.filter{p->val t=parse(p.at);t!=null&&(fromMillis==null||t>=fromMillis)&&(toMillis==null||t<=toMillis)}}
    val activePoints=if(filterValid)filteredPoints else points
    val intelligence=remember(activePoints,thresholds){MovementIntelligence.build(activePoints,thresholds)}
    var step by remember(filteredPoints){mutableIntStateOf((filteredPoints.size-1).coerceAtLeast(0))}
    LaunchedEffect(playing,filteredPoints){if(!playing||filteredPoints.isEmpty())return@LaunchedEffect;if(step>=filteredPoints.lastIndex)step=0;while(playing&&step<filteredPoints.lastIndex){delay(900);step++};playing=false}
    if(showVisits) TowerVisitsDialog(intelligence.visits,activePoints,{playing=false;mapFocus=it;showVisits=false}){showVisits=false}
    if(showTransitions) MovementTransitionsDialog(intelligence.transitions,activePoints,{playing=false;mapFocus=it;showTransitions=false}){showTransitions=false}
    if(showAnomalies) MovementAnomaliesDialog(intelligence.anomalies,activePoints,{playing=false;mapFocus=it;showAnomalies=false}){showAnomalies=false}

    Column(modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Movement period",style=MaterialTheme.typography.titleSmall)
            OutlinedTextField(fromText,{playing=false;mapFocus=null;fromText=it},label={Text("From: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(toText,{playing=false;mapFocus=null;toText=it},label={Text("To: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(!filterValid)Text("Invalid date/time range.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) else if(fromText.isNotBlank()||toText.isNotBlank()){Text("${filteredPoints.size} of ${points.size} mapped records in selected period",style=MaterialTheme.typography.bodySmall);OutlinedButton({playing=false;mapFocus=null;fromText="";toText=""},Modifier.fillMaxWidth()){Text("Reset movement period")}} else Text("All ${points.size} mapped records",style=MaterialTheme.typography.bodySmall)
        }}
        if(filterValid&&filteredPoints.isEmpty()){Text("No mapped movement points are available in the selected period.",modifier=Modifier.padding(12.dp));return@Column}
        MovementThresholdSettings(thresholds=thresholds,onChange={updateThresholds(it)})
        Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Movement Intelligence",style=MaterialTheme.typography.titleSmall);Text("${intelligence.visits.size} towers • ${intelligence.repeatedTowers} repeated • ${intelligence.transitions.size} transitions • ${intelligence.anomalies.size} flags",style=MaterialTheme.typography.bodySmall)
            intelligence.visits.firstOrNull()?.let{top->Text("Most observed: ${top.tower} • ${top.records} record(s) • first ${top.firstSeen} • last ${top.lastSeen}",style=MaterialTheme.typography.bodySmall)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton({showVisits=true},Modifier.weight(1f),enabled=intelligence.visits.isNotEmpty()){Text("Visits")};OutlinedButton({showTransitions=true},Modifier.weight(1f),enabled=intelligence.transitions.isNotEmpty()){Text("Transitions")};OutlinedButton({showAnomalies=true},Modifier.weight(1f),enabled=intelligence.anomalies.isNotEmpty()){Text("Flags (${intelligence.anomalies.size})")}}
            Text("Flags identify patterns for review only. Tower observations do not establish the handset's exact position or continuous travel.",style=MaterialTheme.typography.labelSmall)
        }}
        mapFocus?.let{focus->Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Map focus",style=MaterialTheme.typography.titleSmall);Text("${focus.label} • ${focus.points.size} highlighted observation(s)",style=MaterialTheme.typography.bodySmall)};TextButton({mapFocus=null}){Text("Clear")}}}}
        val safeStep=step.coerceIn(0,(activePoints.size-1).coerceAtLeast(0));Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({mapFocus=null;if(safeStep>=activePoints.lastIndex)step=0;playing=!playing},enabled=filterValid&&activePoints.isNotEmpty(),modifier=Modifier.weight(1f)){Text(if(playing)"Pause" else "Play movement")};OutlinedButton({playing=false;mapFocus=null;step=0},enabled=activePoints.isNotEmpty(),modifier=Modifier.weight(1f)){Text("First point")};OutlinedButton({playing=false;mapFocus=null;step=activePoints.lastIndex},enabled=activePoints.isNotEmpty(),modifier=Modifier.weight(1f)){Text("All points")}}
        if(activePoints.isNotEmpty())Text(if(mapFocus!=null)"Showing selected movement evidence on map" else if(playing||safeStep<activePoints.lastIndex)"Playback: ${safeStep+1} / ${activePoints.size} • ${activePoints[safeStep].at.ifBlank{"Time unavailable"}}" else "Showing all ${activePoints.size} mapped records",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=12.dp,vertical=2.dp))
        AndroidView(modifier=Modifier.fillMaxWidth().height(360.dp),factory={ctx->Configuration.getInstance().userAgentValue=ctx.packageName;Configuration.getInstance().load(ctx,ctx.getSharedPreferences("osmdroid",0));MapView(ctx).apply{setMultiTouchControls(true);minZoomLevel=3.0;maxZoomLevel=20.0;mapRef=this}},update={map->
            map.overlays.clear();if(activePoints.isEmpty())return@AndroidView;val focus=mapFocus;val visible=if(focus!=null)focus.points else if(safeStep>=activePoints.lastIndex)activePoints else activePoints.take(safeStep+1);val routePoints=visible.map{OsmGeoPoint(it.latitude,it.longitude)}
            if(routePoints.size>1)map.overlays.add(Polyline().apply{setPoints(routePoints);outlinePaint.strokeWidth=if(focus!=null)11f else 7f});visible.forEachIndexed{index,p->map.overlays.add(Marker(map).apply{position=OsmGeoPoint(p.latitude,p.longitude);setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);title=if(focus!=null)"Selected • ${p.tower.ifBlank{"Mapped CDR point"}}" else p.tower.ifBlank{"Mapped CDR point"};snippet=p.at.ifBlank{"Time unavailable"};subDescription=if(focus!=null)"Highlighted movement observation" else if(index==0)"First mapped record" else if(index==visible.lastIndex&&visible.size<activePoints.size)"Current playback point" else if(index==visible.lastIndex)"Last mapped record" else "CDR mapped record"})}
            routePoints.lastOrNull()?.let{current->if(focus!=null){if(routePoints.size==1){map.controller.setZoom(17.0);map.controller.animateTo(current)}else{val n=routePoints.maxOf{it.latitude};val s=routePoints.minOf{it.latitude};val e=routePoints.maxOf{it.longitude};val w=routePoints.minOf{it.longitude};map.post{map.zoomToBoundingBox(BoundingBox(n,e,s,w),true,96)}}}else if(visible.size<activePoints.size){map.controller.setZoom(maxOf(map.zoomLevelDouble,15.0));map.controller.animateTo(current)}else if(routePoints.size==1){map.controller.setZoom(16.0);map.controller.setCenter(current)}else{val n=routePoints.maxOf{it.latitude};val s=routePoints.minOf{it.latitude};val e=routePoints.maxOf{it.longitude};val w=routePoints.minOf{it.longitude};map.post{map.zoomToBoundingBox(BoundingBox(n,e,s,w),true,72)}}};map.invalidate()
        })
    };DisposableEffect(Unit){onDispose{playing=false;mapRef?.onDetach()}}
}

@Composable private fun TowerVisitsDialog(visits:List<MovementIntelligence.TowerVisit>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){var selected by remember{mutableStateOf<MovementIntelligence.TowerVisit?>(null)};selected?.let{v->val obs=points.filter{movementTowerKey(it)==v.tower};MovementObservationsDialog("Tower ${v.tower}",obs,onShowOnMap={onShowOnMap(MapFocus(obs,"Tower ${v.tower}"))}){selected=null}};AlertDialog(onDismissRequest=onDismiss,title={Text("Tower Visits")},text={LazyColumn(Modifier.heightIn(max=520.dp)){items(visits){v->ListItem(headlineContent={Text(v.tower)},supportingContent={Text("${v.records} record(s) • observed span ${v.observedSpanMinutes} min\nFirst: ${v.firstSeen}\nLast: ${v.lastSeen}\nTap to inspect")},modifier=Modifier.clickable{selected=v});HorizontalDivider()}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementTransitionsDialog(transitions:List<MovementIntelligence.Transition>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){var selected by remember{mutableStateOf<MovementIntelligence.Transition?>(null)};selected?.let{t->val obs=points.filter{movementTowerKey(it)==t.fromTower||movementTowerKey(it)==t.toTower};MovementObservationsDialog("${t.fromTower} → ${t.toTower}",obs,t.at,{onShowOnMap(MapFocus(obs,"${t.fromTower} → ${t.toTower}"))}){selected=null}};AlertDialog(onDismissRequest=onDismiss,title={Text("Movement Transitions")},text={LazyColumn(Modifier.heightIn(max=520.dp)){items(transitions){t->ListItem(headlineContent={Text("${t.fromTower} → ${t.toTower}")},supportingContent={Text("${t.at}${t.gapMinutes?.let{" • $it min"}?:""}${t.distanceKm?.let{" • ${"%.1f".format(Locale.US,it)} km"}?:""}\nTap to inspect")},modifier=Modifier.clickable{selected=t});HorizontalDivider()}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementAnomaliesDialog(anomalies:List<MovementIntelligence.MovementAnomaly>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Movement Review Flags")},text={Column{Text("Automated flags are prompts for review, not findings of exact handset movement.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(bottom=6.dp));LazyColumn(Modifier.heightIn(max=500.dp)){items(anomalies){a->val obs=points.filter{movementTowerKey(it) in a.towers};ListItem(headlineContent={Text(a.title)},supportingContent={Text("${a.at}\n${a.detail}\nTap to show related observations on map")},modifier=Modifier.clickable{onShowOnMap(MapFocus(obs,"Review flag: ${a.title}"))});HorizontalDivider()}}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementObservationsDialog(title:String,observations:List<GeoPoint>,focusTime:String="",onShowOnMap:()->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column{Text("${observations.size} mapped CDR observation(s)",style=MaterialTheme.typography.bodySmall);if(focusTime.isNotBlank())Text("Transition recorded at: $focusTime",style=MaterialTheme.typography.labelMedium);Text("Mapped tower coordinates do not establish the handset's exact position.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(vertical=6.dp));Button(onClick=onShowOnMap,enabled=observations.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Show on Map")};LazyColumn(Modifier.heightIn(max=400.dp)){items(observations){p->ListItem(headlineContent={Text(p.at.ifBlank{"Time unavailable"})},supportingContent={Text("Tower: ${movementTowerKey(p)}\n${"%.6f".format(Locale.US,p.latitude)}, ${"%.6f".format(Locale.US,p.longitude)}")});HorizontalDivider()}}}},confirmButton={TextButton(onDismiss){Text("Back")}})}
