package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import java.util.Calendar

private fun movementHour(value:String):Int? {
    val epoch=run {
        for(pattern in listOf("dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss")){
            val parsed=runCatching{SimpleDateFormat(pattern,Locale.US).apply{isLenient=false}.parse(value.trim())?.time}.getOrNull()
            if(parsed!=null) return@run parsed
        }
        return@run null
    } ?: return null
    return Calendar.getInstance().apply{timeInMillis=epoch}.get(Calendar.HOUR_OF_DAY)
}
private fun movementTimePeriod(hour:Int):String = when(hour){ in 6..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..21 -> "Evening"; else -> "Night" }
private fun movementDayNight(hour:Int):String = if(hour in 6..17) "Day" else "Night"
private fun movementWeekday(value:String):String? {
    val epoch=run {
        for(pattern in listOf("dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss")){
            val parsed=runCatching{SimpleDateFormat(pattern,Locale.US).apply{isLenient=false}.parse(value.trim())?.time}.getOrNull()
            if(parsed!=null) return@run parsed
        }
        return@run null
    } ?: return null
    return when(Calendar.getInstance().apply{timeInMillis=epoch}.get(Calendar.DAY_OF_WEEK)){
        Calendar.MONDAY->"Mon";Calendar.TUESDAY->"Tue";Calendar.WEDNESDAY->"Wed";Calendar.THURSDAY->"Thu";Calendar.FRIDAY->"Fri";Calendar.SATURDAY->"Sat";else->"Sun"
    }
}
private fun movementTowerKey(point: GeoPoint): String = point.tower.ifBlank { "${"%.5f".format(Locale.US, point.latitude)}, ${"%.5f".format(Locale.US, point.longitude)}" }
private data class MapFocus(val points: List<GeoPoint>, val label: String)
private const val MOVEMENT_PREFS = "movement_review_settings"

@Composable
fun CdrMovementMap(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val context = LocalContext.current; val prefs = remember(context) { context.getSharedPreferences(MOVEMENT_PREFS, 0) }; val defaults = remember { MovementIntelligence.Thresholds() }
    var fromText by remember(points){mutableStateOf("")}; var toText by remember(points){mutableStateOf("")}; var playing by remember(points){mutableStateOf(false)}; var showVisits by remember{mutableStateOf(false)}; var showTransitions by remember{mutableStateOf(false)}; var showAnomalies by remember{mutableStateOf(false)}; var mapFocus by remember{mutableStateOf<MapFocus?>(null)}; var mapRef by remember{mutableStateOf<MapView?>(null)}
    var showRapidFlags by remember{mutableStateOf(true)}; var showLongGapFlags by remember{mutableStateOf(true)}; var showReturnFlags by remember{mutableStateOf(true)}
    var thresholds by remember { mutableStateOf(MovementIntelligence.Thresholds(prefs.getFloat("rapidDistanceKm",defaults.rapidDistanceKm.toFloat()).toDouble(),prefs.getLong("rapidWindowMinutes",defaults.rapidWindowMinutes),prefs.getLong("longGapMinutes",defaults.longGapMinutes),prefs.getLong("returnWindowMinutes",defaults.returnWindowMinutes))) }
    fun updateThresholds(v:MovementIntelligence.Thresholds){thresholds=v;playing=false;mapFocus=null;prefs.edit().putFloat("rapidDistanceKm",v.rapidDistanceKm.toFloat()).putLong("rapidWindowMinutes",v.rapidWindowMinutes).putLong("longGapMinutes",v.longGapMinutes).putLong("returnWindowMinutes",v.returnWindowMinutes).apply()}
    fun parse(v:String):Long?{if(v.isBlank())return null;for(p in listOf("dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss")){val x=runCatching{SimpleDateFormat(p,Locale.US).apply{isLenient=false}.parse(v.trim())?.time}.getOrNull();if(x!=null)return x};return null}
    val fm=parse(fromText);val tm=parse(toText);val valid=(fromText.isBlank()||fm!=null)&&(toText.isBlank()||tm!=null)&&(fm==null||tm==null||fm<=tm);val filtered=remember(points,fromText,toText){if(!valid)points else points.filter{p->val t=parse(p.at);t!=null&&(fm==null||t>=fm)&&(tm==null||t<=tm)}};val active=if(valid)filtered else points;val intel=remember(active,thresholds){MovementIntelligence.build(active,thresholds)};var step by remember(filtered){mutableIntStateOf((filtered.size-1).coerceAtLeast(0))}
    val rapidFlagCount=intel.anomalies.count{it.type==MovementIntelligence.AnomalyType.RAPID_CHANGE};val longGapFlagCount=intel.anomalies.count{it.type==MovementIntelligence.AnomalyType.LONG_GAP};val returnFlagCount=intel.anomalies.count{it.type==MovementIntelligence.AnomalyType.RETURN_PATTERN};val visibleAnomalies=intel.anomalies.filter{when(it.type){MovementIntelligence.AnomalyType.RAPID_CHANGE->showRapidFlags;MovementIntelligence.AnomalyType.LONG_GAP->showLongGapFlags;MovementIntelligence.AnomalyType.RETURN_PATTERN->showReturnFlags}}
    LaunchedEffect(playing,filtered){if(!playing||filtered.isEmpty())return@LaunchedEffect;if(step>=filtered.lastIndex)step=0;while(playing&&step<filtered.lastIndex){delay(900);step++};playing=false}
    if(showVisits)TowerVisitsDialog(intel.visits,active,{playing=false;mapFocus=it;showVisits=false}){showVisits=false};if(showTransitions)MovementTransitionsDialog(intel.transitions,active,{playing=false;mapFocus=it;showTransitions=false}){showTransitions=false};if(showAnomalies)MovementAnomaliesDialog(visibleAnomalies,active,{playing=false;mapFocus=it;showAnomalies=false}){showAnomalies=false}
    Column(modifier.fillMaxWidth()){
        Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Movement period",style=MaterialTheme.typography.titleSmall);OutlinedTextField(fromText,{playing=false;mapFocus=null;fromText=it},label={Text("From: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(toText,{playing=false;mapFocus=null;toText=it},label={Text("To: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth());if(!valid)Text("Invalid date/time range.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)else if(fromText.isNotBlank()||toText.isNotBlank()){Text("${filtered.size} of ${points.size} mapped records in selected period",style=MaterialTheme.typography.bodySmall);OutlinedButton({playing=false;mapFocus=null;fromText="";toText=""},Modifier.fillMaxWidth()){Text("Reset movement period")}}else Text("All ${points.size} mapped records",style=MaterialTheme.typography.bodySmall)}}
        if(valid&&filtered.isEmpty()){Text("No mapped movement points are available in the selected period.",modifier=Modifier.padding(12.dp));return@Column}
        MovementSummaryCard(intel.metrics)
        TimeOfDayMovementCard(intel.timePeriods){label->
            val observations=active.filter{movementHour(it.at)?.let{h->movementTimePeriod(h)==label}==true}
            if(observations.isNotEmpty()){playing=false;mapFocus=MapFocus(observations,label)}
        }
        DayNightMovementCard(intel.dayNight){label->
            val observations=active.filter{movementHour(it.at)?.let{h->movementDayNight(h)==label}==true}
            if(observations.isNotEmpty()){playing=false;mapFocus=MapFocus(observations,label)}
        }
        HourlyActivityCard(intel.hourly){hour->
            val observations=active.filter{movementHour(it.at)==hour}
            if(observations.isNotEmpty()){
                playing=false
                mapFocus=MapFocus(observations,"%02d:00–%02d:59".format(Locale.US,hour,hour))
            }
        }
        WeekdayActivityCard(intel.weekdays){label->
            val observations=active.filter{movementWeekday(it.at)==label}
            if(observations.isNotEmpty()){playing=false;mapFocus=MapFocus(observations,label)}
        }
        MovementThresholdSettings(thresholds=thresholds,onChange={updateThresholds(it)})
        MovementFlagFilters(rapidFlagCount,longGapFlagCount,returnFlagCount,showRapidFlags,showLongGapFlags,showReturnFlags,{showRapidFlags=it;showAnomalies=false;mapFocus=null},{showLongGapFlags=it;showAnomalies=false;mapFocus=null},{showReturnFlags=it;showAnomalies=false;mapFocus=null})
        Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Movement Intelligence",style=MaterialTheme.typography.titleSmall);Text("${intel.visits.size} towers • ${intel.repeatedTowers} repeated • ${intel.transitions.size} transitions • ${visibleAnomalies.size} visible / ${intel.anomalies.size} total flags",style=MaterialTheme.typography.bodySmall);intel.visits.firstOrNull()?.let{top->Text("Most observed: ${top.tower} • ${top.records} record(s) • first ${top.firstSeen} • last ${top.lastSeen}",style=MaterialTheme.typography.bodySmall)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton({showVisits=true},Modifier.weight(1f),enabled=intel.visits.isNotEmpty()){Text("Visits")};OutlinedButton({showTransitions=true},Modifier.weight(1f),enabled=intel.transitions.isNotEmpty()){Text("Transitions")};OutlinedButton({showAnomalies=true},Modifier.weight(1f),enabled=visibleAnomalies.isNotEmpty()){Text("Flags (${visibleAnomalies.size})")}};if(visibleAnomalies.isEmpty()&&intel.anomalies.isNotEmpty())Text("No review flag types are currently selected.",style=MaterialTheme.typography.bodySmall);Text("Flags identify patterns for review only. Tower observations do not establish the handset's exact position or continuous travel.",style=MaterialTheme.typography.labelSmall)}}
        mapFocus?.let{f->Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Map focus",style=MaterialTheme.typography.titleSmall);Text("${f.label} • ${f.points.size} highlighted observation(s)",style=MaterialTheme.typography.bodySmall)};TextButton({mapFocus=null}){Text("Clear")}}}}
        val safe=step.coerceIn(0,(active.size-1).coerceAtLeast(0));Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({mapFocus=null;if(safe>=active.lastIndex)step=0;playing=!playing},enabled=valid&&active.isNotEmpty(),modifier=Modifier.weight(1f)){Text(if(playing)"Pause" else "Play movement")};OutlinedButton({playing=false;mapFocus=null;step=0},enabled=active.isNotEmpty(),modifier=Modifier.weight(1f)){Text("First point")};OutlinedButton({playing=false;mapFocus=null;step=active.lastIndex},enabled=active.isNotEmpty(),modifier=Modifier.weight(1f)){Text("All points")}}
        if(active.isNotEmpty())Text(if(mapFocus!=null)"Showing selected movement evidence on map" else if(playing||safe<active.lastIndex)"Playback: ${safe+1} / ${active.size} • ${active[safe].at.ifBlank{"Time unavailable"}}" else "Showing all ${active.size} mapped records",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=12.dp,vertical=2.dp))
        AndroidView(modifier=Modifier.fillMaxWidth().height(360.dp),factory={ctx->Configuration.getInstance().userAgentValue=ctx.packageName;Configuration.getInstance().load(ctx,ctx.getSharedPreferences("osmdroid",0));MapView(ctx).apply{setMultiTouchControls(true);minZoomLevel=3.0;maxZoomLevel=20.0;mapRef=this}},update={map->map.overlays.clear();if(active.isEmpty())return@AndroidView;val focus=mapFocus;val visible=if(focus!=null)focus.points else if(safe>=active.lastIndex)active else active.take(safe+1);val route=visible.map{OsmGeoPoint(it.latitude,it.longitude)};if(route.size>1)map.overlays.add(Polyline().apply{setPoints(route);outlinePaint.strokeWidth=if(focus!=null)11f else 7f});visible.forEachIndexed{i,p->map.overlays.add(Marker(map).apply{position=OsmGeoPoint(p.latitude,p.longitude);setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);title=if(focus!=null)"Selected • ${p.tower.ifBlank{"Mapped CDR point"}}" else p.tower.ifBlank{"Mapped CDR point"};snippet=p.at.ifBlank{"Time unavailable"};subDescription=if(focus!=null)"Highlighted movement observation" else if(i==0)"First mapped record" else if(i==visible.lastIndex&&visible.size<active.size)"Current playback point" else if(i==visible.lastIndex)"Last mapped record" else "CDR mapped record"})};route.lastOrNull()?.let{c->if(focus!=null){if(route.size==1){map.controller.setZoom(17.0);map.controller.animateTo(c)}else{val n=route.maxOf{it.latitude};val s=route.minOf{it.latitude};val e=route.maxOf{it.longitude};val w=route.minOf{it.longitude};map.post{map.zoomToBoundingBox(BoundingBox(n,e,s,w),true,96)}}}else if(visible.size<active.size){map.controller.setZoom(maxOf(map.zoomLevelDouble,15.0));map.controller.animateTo(c)}else if(route.size==1){map.controller.setZoom(16.0);map.controller.setCenter(c)}else{val n=route.maxOf{it.latitude};val s=route.minOf{it.latitude};val e=route.maxOf{it.longitude};val w=route.minOf{it.longitude};map.post{map.zoomToBoundingBox(BoundingBox(n,e,s,w),true,72)}}};map.invalidate()})
    };DisposableEffect(Unit){onDispose{playing=false;mapRef?.onDetach()}}
}

@Composable private fun WeekdayActivityCard(days:List<MovementIntelligence.WeekdaySummary>,onDaySelected:(String)->Unit){
    val peak=days.maxByOrNull{it.observations}
    Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Weekday Activity",style=MaterialTheme.typography.titleSmall)
            peak?.takeIf{it.observations>0}?.let{p->
                Text("Peak weekday: ${p.label} • ${p.observations} observation(s) • ${p.uniqueTowers} tower(s)",style=MaterialTheme.typography.bodySmall)
                p.topTower?.let{Text("Most observed tower on ${p.label}: $it • ${p.topTowerRecords} record(s)",style=MaterialTheme.typography.bodySmall)}
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(days){d->
                    Surface(modifier=Modifier.clickable(enabled=d.observations>0){onDaySelected(d.label)},tonalElevation=1.dp,shape=MaterialTheme.shapes.small){
                        Column(Modifier.padding(horizontal=10.dp,vertical=7.dp)){
                            Text(d.label,style=MaterialTheme.typography.labelMedium)
                            Text(d.observations.toString(),style=MaterialTheme.typography.titleMedium)
                            Text("obs",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("Tap a weekday with observations to highlight matching mapped CDR records. Counts reflect only timestamped tower observations.",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun MovementFlagFilters(rapidCount:Int,longGapCount:Int,returnCount:Int,rapidSelected:Boolean,longGapSelected:Boolean,returnSelected:Boolean,onRapid:(Boolean)->Unit,onLongGap:(Boolean)->Unit,onReturn:(Boolean)->Unit){
    Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Review Flag Filters",style=MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                item{FilterChip(selected=rapidSelected,onClick={onRapid(!rapidSelected)},label={Text("Rapid ($rapidCount)")})}
                item{FilterChip(selected=longGapSelected,onClick={onLongGap(!longGapSelected)},label={Text("Long gap ($longGapCount)")})}
                item{FilterChip(selected=returnSelected,onClick={onReturn(!returnSelected)},label={Text("Return ($returnCount)")})}
            }
            Text("Filters change only which review flags are shown. Threshold calculations and movement summary metrics are unchanged.",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun HourlyActivityCard(hours:List<MovementIntelligence.HourlySummary>,onHourSelected:(Int)->Unit){
    val peak=hours.maxByOrNull{it.observations}
    Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Hourly Tower Activity",style=MaterialTheme.typography.titleSmall)
            peak?.takeIf{it.observations>0}?.let{p->
                Text("Peak hour: ${"%02d".format(Locale.US,p.hour)}:00 • ${p.observations} observation(s) • ${p.uniqueTowers} tower(s)",style=MaterialTheme.typography.bodySmall)
                p.topTower?.let{Text("Most observed tower in peak hour: $it • ${p.topTowerRecords} record(s)",style=MaterialTheme.typography.bodySmall)}
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(hours){h->
                    Surface(modifier=Modifier.clickable(enabled=h.observations>0){onHourSelected(h.hour)},tonalElevation=1.dp,shape=MaterialTheme.shapes.small){
                        Column(Modifier.padding(horizontal=9.dp,vertical=7.dp)){
                            Text("%02d".format(Locale.US,h.hour),style=MaterialTheme.typography.labelMedium)
                            Text(h.observations.toString(),style=MaterialTheme.typography.titleMedium)
                            Text("obs",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("Tap an hour with observations to highlight those mapped CDR records on the map. 00–23 counts do not represent continuous handset tracking.",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun DayNightMovementCard(periods:List<MovementIntelligence.DayNightSummary>,onPeriodSelected:(String)->Unit){
    Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Day / Night Summary",style=MaterialTheme.typography.titleSmall)
            periods.forEach{p->
                Surface(Modifier.fillMaxWidth().clickable(enabled=p.observations>0){onPeriodSelected(p.label)},tonalElevation=1.dp,shape=MaterialTheme.shapes.small){
                    Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Text("${p.label} • ${p.observations} observation(s)",style=MaterialTheme.typography.bodyMedium)
                        Text("${p.uniqueTowers} unique tower(s)",style=MaterialTheme.typography.bodySmall)
                        Text(p.topTower?.let{"Most observed tower: $it • ${p.topTowerRecords} record(s)"}?:"No mapped observations",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Tap Day or Night to highlight matching mapped CDR records. Day is 06:00–17:59 and Night is 18:00–05:59; this does not establish exact handset location.",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun TimeOfDayMovementCard(periods:List<MovementIntelligence.TimePeriodSummary>,onPeriodSelected:(String)->Unit){Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Time-of-day observations",style=MaterialTheme.typography.titleSmall);periods.forEach{p->Surface(Modifier.fillMaxWidth().clickable(enabled=p.observations>0){onPeriodSelected(p.label)},tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text("${p.label} • ${p.observations} observation(s)",style=MaterialTheme.typography.bodyMedium);Text(p.topTower?.let{"Most observed tower: $it • ${p.topTowerRecords} record(s)"}?:"No mapped observations",style=MaterialTheme.typography.bodySmall)}}};Text("Tap a period with observations to highlight those mapped CDR records. Periods summarize tower observations only and do not establish exact handset location.",style=MaterialTheme.typography.labelSmall)}}}

@Composable private fun MovementSummaryCard(m:MovementIntelligence.Metrics){Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("Movement Summary",style=MaterialTheme.typography.titleSmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SummaryMetric("Observations",m.mappedObservations.toString(),Modifier.weight(1f));SummaryMetric("Towers",m.uniqueTowers.toString(),Modifier.weight(1f));SummaryMetric("Changes",m.towerChanges.toString(),Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SummaryMetric("Approx. path","${"%.1f".format(Locale.US,m.approximatePathKm)} km",Modifier.weight(1f));SummaryMetric("Longest gap",formatMinutes(m.longestGapMinutes),Modifier.weight(1f))};m.mostObservedTower?.let{Text("Most observed tower: $it • ${m.mostObservedTowerRecords} record(s)",style=MaterialTheme.typography.bodySmall)};Text("Approx. path is the sum of straight-line distances between consecutive mapped CDR observations; it is not actual handset travel distance.",style=MaterialTheme.typography.labelSmall)}}}
@Composable private fun SummaryMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier=modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}
private fun formatMinutes(minutes:Long):String=if(minutes>=60)"${minutes/60}h ${minutes%60}m" else "${minutes}m"

@Composable private fun TowerVisitsDialog(visits:List<MovementIntelligence.TowerVisit>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){var selected by remember{mutableStateOf<MovementIntelligence.TowerVisit?>(null)};selected?.let{v->val obs=points.filter{movementTowerKey(it)==v.tower};MovementObservationsDialog("Tower ${v.tower}",obs,onShowOnMap={onShowOnMap(MapFocus(obs,"Tower ${v.tower}"))}){selected=null}};AlertDialog(onDismissRequest=onDismiss,title={Text("Tower Visits")},text={LazyColumn(Modifier.heightIn(max=520.dp)){items(visits){v->ListItem(headlineContent={Text(v.tower)},supportingContent={Text("${v.records} record(s) • observed span ${v.observedSpanMinutes} min\nFirst: ${v.firstSeen}\nLast: ${v.lastSeen}\nTap to inspect")},modifier=Modifier.clickable{selected=v});HorizontalDivider()}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementTransitionsDialog(transitions:List<MovementIntelligence.Transition>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){var selected by remember{mutableStateOf<MovementIntelligence.Transition?>(null)};selected?.let{t->val obs=points.filter{movementTowerKey(it)==t.fromTower||movementTowerKey(it)==t.toTower};MovementObservationsDialog("${t.fromTower} → ${t.toTower}",obs,t.at,{onShowOnMap(MapFocus(obs,"${t.fromTower} → ${t.toTower}"))}){selected=null}};AlertDialog(onDismissRequest=onDismiss,title={Text("Movement Transitions")},text={LazyColumn(Modifier.heightIn(max=520.dp)){items(transitions){t->ListItem(headlineContent={Text("${t.fromTower} → ${t.toTower}")},supportingContent={Text("${t.at}${t.gapMinutes?.let{" • $it min"}?:""}${t.distanceKm?.let{" • ${"%.1f".format(Locale.US,it)} km"}?:""}\nTap to inspect")},modifier=Modifier.clickable{selected=t});HorizontalDivider()}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementAnomaliesDialog(anomalies:List<MovementIntelligence.MovementAnomaly>,points:List<GeoPoint>,onShowOnMap:(MapFocus)->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Movement Review Flags")},text={Column{Text("Automated flags are prompts for review, not findings of exact handset movement.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(bottom=6.dp));LazyColumn(Modifier.heightIn(max=500.dp)){items(anomalies){a->val obs=points.filter{movementTowerKey(it) in a.towers};ListItem(headlineContent={Text(a.title)},supportingContent={Text("${a.at}\n${a.detail}\nTap to show related observations on map")},modifier=Modifier.clickable{onShowOnMap(MapFocus(obs,"Review flag: ${a.title}"))});HorizontalDivider()}}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
@Composable private fun MovementObservationsDialog(title:String,observations:List<GeoPoint>,focusTime:String="",onShowOnMap:()->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column{Text("${observations.size} mapped CDR observation(s)",style=MaterialTheme.typography.bodySmall);if(focusTime.isNotBlank())Text("Transition recorded at: $focusTime",style=MaterialTheme.typography.labelMedium);Text("Mapped tower coordinates do not establish the handset's exact position.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(vertical=6.dp));Button(onClick=onShowOnMap,enabled=observations.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Show on Map")};LazyColumn(Modifier.heightIn(max=400.dp)){items(observations){p->ListItem(headlineContent={Text(p.at.ifBlank{"Time unavailable"})},supportingContent={Text("Tower: ${movementTowerKey(p)}\n${"%.6f".format(Locale.US,p.latitude)}, ${"%.6f".format(Locale.US,p.longitude)}")});HorizontalDivider()}}}},confirmButton={TextButton(onDismiss){Text("Back")}})}
