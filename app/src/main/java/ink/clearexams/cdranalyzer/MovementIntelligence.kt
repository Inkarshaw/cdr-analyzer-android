package ink.clearexams.cdranalyzer

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import kotlin.math.*

object MovementIntelligence {
    data class Thresholds(val rapidDistanceKm: Double = 10.0, val rapidWindowMinutes: Long = 30, val longGapMinutes: Long = 360, val returnWindowMinutes: Long = 360)
    data class TowerVisit(val tower: String, val records: Int, val firstSeen: String, val lastSeen: String, val observedSpanMinutes: Long)
    data class Transition(val fromTower: String, val toTower: String, val at: String, val gapMinutes: Long?, val distanceKm: Double? = null, val impliedSpeedKmh: Double? = null)
    enum class AnomalyType { RAPID_CHANGE, LONG_GAP, RETURN_PATTERN }
    data class MovementAnomaly(val type: AnomalyType, val title: String, val detail: String, val at: String, val towers: List<String>)
    data class TimePeriodSummary(val label: String, val observations: Int, val topTower: String?, val topTowerRecords: Int)
    data class DayNightSummary(val label: String, val observations: Int, val uniqueTowers: Int, val topTower: String?, val topTowerRecords: Int)
    data class HourlySummary(val hour: Int, val observations: Int, val uniqueTowers: Int, val topTower: String?, val topTowerRecords: Int)
    data class WeekdaySummary(val label: String, val observations: Int, val uniqueTowers: Int, val topTower: String?, val topTowerRecords: Int)
    data class DailySummary(val date: String, val observations: Int, val uniqueTowers: Int, val topTower: String?, val topTowerRecords: Int)
    data class TransitionPattern(val fromTower: String, val toTower: String, val count: Int, val averageGapMinutes: Double, val averageDistanceKm: Double)
    data class ObservationRun(val tower: String, val records: Int, val firstSeen: String, val lastSeen: String, val spanMinutes: Long)
    data class OvernightObservation(val nightDate: String, val tower: String, val records: Int, val firstSeen: String, val lastSeen: String, val spanMinutes: Long)
    data class RecurringOvernightTower(val tower: String, val nights: Int, val totalRecords: Int, val firstNight: String, val lastNight: String)
    data class Metrics(
        val mappedObservations: Int,
        val uniqueTowers: Int,
        val towerChanges: Int,
        val approximatePathKm: Double,
        val longestGapMinutes: Long,
        val mostObservedTower: String?,
        val mostObservedTowerRecords: Int
    )
    data class Summary(val visits: List<TowerVisit>, val transitions: List<Transition>, val repeatedTowers: Int, val anomalies: List<MovementAnomaly> = emptyList(), val metrics: Metrics = Metrics(0,0,0,0.0,0,null,0), val timePeriods: List<TimePeriodSummary> = emptyList(), val dayNight: List<DayNightSummary> = emptyList(), val hourly: List<HourlySummary> = emptyList(), val weekdays: List<WeekdaySummary> = emptyList(), val daily: List<DailySummary> = emptyList(), val transitionPatterns: List<TransitionPattern> = emptyList(), val observationRuns: List<ObservationRun> = emptyList(), val overnightObservations: List<OvernightObservation> = emptyList(), val recurringOvernightTowers: List<RecurringOvernightTower> = emptyList())

    fun build(points: List<GeoPoint>, thresholds: Thresholds = Thresholds()): Summary {
        val rapidDistance = thresholds.rapidDistanceKm.coerceAtLeast(0.1); val rapidWindow = thresholds.rapidWindowMinutes.coerceAtLeast(1); val longGap = thresholds.longGapMinutes.coerceAtLeast(1); val returnWindow = thresholds.returnWindowMinutes.coerceAtLeast(1)
        val timed = points.mapNotNull { point -> parse(point.at)?.let { Triple(point, it, towerKey(point)) } }.sortedBy { it.second }
        val visits = timed.groupBy { it.third }.map { (tower, rows) -> val sorted=rows.sortedBy{it.second}; TowerVisit(tower,sorted.size,sorted.first().first.at,sorted.last().first.at,((sorted.last().second-sorted.first().second)/60000L).coerceAtLeast(0)) }.sortedWith(compareByDescending<TowerVisit>{it.records}.thenByDescending{it.observedSpanMinutes})
        val transitions=mutableListOf<Transition>(); val anomalies=mutableListOf<MovementAnomaly>(); var previous:Triple<GeoPoint,Long,String>?=null; var approximatePathKm=0.0; var longestGapMinutes=0L
        for(current in timed){val prior=previous;if(prior!=null){val gap=((current.second-prior.second)/60000L).coerceAtLeast(0);longestGapMinutes=maxOf(longestGapMinutes,gap);val distance=haversineKm(prior.first.latitude,prior.first.longitude,current.first.latitude,current.first.longitude);approximatePathKm+=distance
            if(gap>=longGap)anomalies+=MovementAnomaly(AnomalyType.LONG_GAP,"Long observation gap","$gap minutes between mapped CDR observations (review threshold: $longGap min)",current.first.at,listOf(prior.third,current.third).distinct())
            if(prior.third!=current.third){val speed=if(gap>0)distance/(gap/60.0) else null;transitions+=Transition(prior.third,current.third,current.first.at,gap,distance,speed);if(gap in 1..rapidWindow&&distance>=rapidDistance)anomalies+=MovementAnomaly(AnomalyType.RAPID_CHANGE,"Rapid tower change","Mapped towers are ${"%.1f".format(Locale.US,distance)} km apart with a $gap minute observation interval (review threshold: ≥${"%.1f".format(Locale.US,rapidDistance)} km within $rapidWindow min)${speed?.let{"; implied rate ${"%.0f".format(Locale.US,it)} km/h"}?:""}",current.first.at,listOf(prior.third,current.third))}}
            previous=current}
        for(i in 2 until timed.size){val a=timed[i-2];val b=timed[i-1];val c=timed[i];val elapsed=((c.second-a.second)/60000L).coerceAtLeast(0);if(a.third==c.third&&a.third!=b.third&&elapsed<=returnWindow)anomalies+=MovementAnomaly(AnomalyType.RETURN_PATTERN,"Return to earlier tower","Observed ${a.third} → ${b.third} → ${c.third} within $elapsed minutes (review threshold: $returnWindow min)",c.first.at,listOf(a.third,b.third,c.third))}
        val top=visits.firstOrNull();val metrics=Metrics(timed.size,visits.size,transitions.size,approximatePathKm,longestGapMinutes,top?.tower,top?.records?:0)
        val periodOrder=listOf("Morning","Afternoon","Evening","Night");val grouped=timed.groupBy{timePeriod(it.second)};val timePeriods=periodOrder.map{label->val rows=grouped[label].orEmpty();val towerTop=rows.groupingBy{it.third}.eachCount().maxByOrNull{it.value};TimePeriodSummary(label,rows.size,towerTop?.key,towerTop?.value?:0)}
        val dayNightGroups=timed.groupBy{dayNightPeriod(it.second)};val dayNight=listOf("Day","Night").map{label->val rows=dayNightGroups[label].orEmpty();val towerTop=rows.groupingBy{it.third}.eachCount().maxByOrNull{it.value};DayNightSummary(label,rows.size,rows.map{it.third}.distinct().size,towerTop?.key,towerTop?.value?:0)}
        val hourlyGroups=timed.groupBy{hourOfDay(it.second)};val hourly=(0..23).map{hour->val rows=hourlyGroups[hour].orEmpty();val towerTop=rows.groupingBy{it.third}.eachCount().maxByOrNull{it.value};HourlySummary(hour,rows.size,rows.map{it.third}.distinct().size,towerTop?.key,towerTop?.value?:0)}
        val weekdayOrder=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun");val weekdayGroups=timed.groupBy{weekdayLabel(it.second)};val weekdays=weekdayOrder.map{label->val rows=weekdayGroups[label].orEmpty();val towerTop=rows.groupingBy{it.third}.eachCount().maxByOrNull{it.value};WeekdaySummary(label,rows.size,rows.map{it.third}.distinct().size,towerTop?.key,towerTop?.value?:0)}
        val daily=timed.groupBy{dateLabel(it.second)}.map{(date,rows)->val towerTop=rows.groupingBy{it.third}.eachCount().maxByOrNull{it.value};DailySummary(date,rows.size,rows.map{it.third}.distinct().size,towerTop?.key,towerTop?.value?:0)}.sortedBy{it.date}
        val transitionPatterns=transitions.groupBy{it.fromTower to it.toTower}.map{(pair,rows)->TransitionPattern(pair.first,pair.second,rows.size,rows.mapNotNull{it.gapMinutes}.average().takeIf{!it.isNaN()}?:0.0,rows.mapNotNull{it.distanceKm}.average().takeIf{!it.isNaN()}?:0.0)}.sortedWith(compareByDescending<TransitionPattern>{it.count}.thenBy{it.averageGapMinutes})
        val observationRuns=mutableListOf<ObservationRun>();if(timed.isNotEmpty()){var start=0;for(i in 1..timed.size){if(i==timed.size||timed[i].third!=timed[start].third){val first=timed[start];val last=timed[i-1];val count=i-start;val span=((last.second-first.second)/60000L).coerceAtLeast(0);observationRuns+=ObservationRun(first.third,count,first.first.at,last.first.at,span);start=i}}};val sortedRuns=observationRuns.filter{it.records>1}.sortedWith(compareByDescending<ObservationRun>{it.spanMinutes}.thenByDescending{it.records})
        val overnightObservations=timed.mapNotNull{row->overnightKey(row.second)?.let{key->key to row}}.groupBy({it.first to it.second.third},{it.second}).mapNotNull{(key,rows)->val sorted=rows.sortedBy{it.second};val hasLate=sorted.any{hourOfDay(it.second)>=22};val hasEarly=sorted.any{hourOfDay(it.second)<=5};if(!hasLate||!hasEarly)return@mapNotNull null;val first=sorted.first();val last=sorted.last();OvernightObservation(key.first,key.second,sorted.size,first.first.at,last.first.at,((last.second-first.second)/60000L).coerceAtLeast(0))}.sortedWith(compareByDescending<OvernightObservation>{it.spanMinutes}.thenByDescending{it.records})
        val recurringOvernightTowers=overnightObservations.groupBy{it.tower}.map{(tower,rows)->val nights=rows.map{it.nightDate}.distinct().sorted();RecurringOvernightTower(tower,nights.size,rows.sumOf{it.records},nights.first(),nights.last())}.filter{it.nights>1}.sortedWith(compareByDescending<RecurringOvernightTower>{it.nights}.thenByDescending{it.totalRecords})
        return Summary(visits,transitions,visits.count{it.records>1},anomalies.distinctBy{"${it.type}|${it.at}|${it.towers.joinToString()}"},metrics,timePeriods,dayNight,hourly,weekdays,daily,transitionPatterns,sortedRuns,overnightObservations,recurringOvernightTowers)
    }

    private fun overnightKey(epochMillis:Long):String? { val cal=Calendar.getInstance().apply{timeInMillis=epochMillis};val hour=cal.get(Calendar.HOUR_OF_DAY);if(hour in 6..21)return null;if(hour<=5)cal.add(Calendar.DAY_OF_MONTH,-1);return SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time) }
    private fun dateLabel(epochMillis:Long):String = SimpleDateFormat("yyyy-MM-dd",Locale.US).format(java.util.Date(epochMillis))
    private fun weekdayLabel(epochMillis:Long):String { return when(Calendar.getInstance().apply{timeInMillis=epochMillis}.get(Calendar.DAY_OF_WEEK)){Calendar.MONDAY->"Mon";Calendar.TUESDAY->"Tue";Calendar.WEDNESDAY->"Wed";Calendar.THURSDAY->"Thu";Calendar.FRIDAY->"Fri";Calendar.SATURDAY->"Sat";else->"Sun"} }
    private fun hourOfDay(epochMillis:Long):Int = Calendar.getInstance().apply{timeInMillis=epochMillis}.get(Calendar.HOUR_OF_DAY)
    private fun dayNightPeriod(epochMillis:Long):String { val hour=Calendar.getInstance().apply{timeInMillis=epochMillis}.get(Calendar.HOUR_OF_DAY); return if(hour in 6..17) "Day" else "Night" }
    private fun timePeriod(epochMillis:Long):String { val hour=Calendar.getInstance().apply{timeInMillis=epochMillis}.get(Calendar.HOUR_OF_DAY); return when(hour){ in 6..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..21 -> "Evening"; else -> "Night" } }
    private fun haversineKm(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double{val r=6371.0088;val dLat=Math.toRadians(lat2-lat1);val dLon=Math.toRadians(lon2-lon1);val a=sin(dLat/2).pow(2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2).pow(2);return 2*r*asin(sqrt(a.coerceIn(0.0,1.0)))}
    private fun towerKey(point:GeoPoint):String=point.tower.ifBlank{"${"%.5f".format(Locale.US,point.latitude)}, ${"%.5f".format(Locale.US,point.longitude)}"}
    private fun parse(value:String):Long?{for(pattern in listOf("dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss")){val result=runCatching{SimpleDateFormat(pattern,Locale.US).apply{isLenient=false}.parse(value.trim())?.time}.getOrNull();if(result!=null)return result};return null}
}
