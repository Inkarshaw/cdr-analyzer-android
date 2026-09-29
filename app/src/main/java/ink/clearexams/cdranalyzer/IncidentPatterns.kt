package ink.clearexams.cdranalyzer

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

data class IncidentPhaseRecord(val phase:String,val record:CdrRecord,val timestamp:Long)
data class IncidentAnalysisResult(
    val before:List<CdrRecord>, val during:List<CdrRecord>, val after:List<CdrRecord>,
    val baselineRecords:Int, val incidentRecords:Int,
    val newContacts:List<String>, val disappearingContacts:List<String>,
    val timeline:List<IncidentPhaseRecord>
)

object IncidentAnalysis {
    fun build(rows:List<CdrRecord>,incident:Long,beforeHours:Int,duringMinutes:Int,afterHours:Int):IncidentAnalysisResult{
        val beforeStart=incident-beforeHours.coerceAtLeast(1)*3600000L
        val duringStart=incident-duringMinutes.coerceAtLeast(1)*60000L
        val duringEnd=incident+duringMinutes.coerceAtLeast(1)*60000L
        val afterEnd=incident+afterHours.coerceAtLeast(1)*3600000L
        val stamped=rows.mapNotNull{r->parseCdrTime(r.dateTime)?.let{it to r}}
        val before=stamped.filter{it.first in beforeStart until duringStart}.map{it.second}
        val during=stamped.filter{it.first in duringStart..duringEnd}.map{it.second}
        val after=stamped.filter{it.first in (duringEnd+1)..afterEnd}.map{it.second}
        val beforeContacts=before.map{it.otherParty}.filter{it.isNotBlank()}.toSet()
        val duringAfterContacts=(during+after).map{it.otherParty}.filter{it.isNotBlank()}.toSet()
        val afterContacts=after.map{it.otherParty}.filter{it.isNotBlank()}.toSet()
        val newContacts=(duringAfterContacts-beforeContacts).sorted()
        val disappearing=(beforeContacts-afterContacts).sorted()
        val baselineStart=incident-7L*24*3600000L
        val baseline=stamped.count{it.first in baselineStart until beforeStart}
        val timeline=mutableListOf<IncidentPhaseRecord>()
        stamped.forEach{(ts,r)->when{ts in beforeStart until duringStart->timeline+=IncidentPhaseRecord("Before",r,ts);ts in duringStart..duringEnd->timeline+=IncidentPhaseRecord("During",r,ts);ts in (duringEnd+1)..afterEnd->timeline+=IncidentPhaseRecord("After",r,ts)}}
        return IncidentAnalysisResult(before,during,after,baseline,during.size,newContacts,disappearing,timeline.sortedBy{it.timestamp})
    }
}

data class DailyPattern(val date:String,val events:Int,val contacts:Int,val towers:Int)
data class RecurringContactPattern(val number:String,val activeDates:Int,val events:Int)
data class HourPattern(val hour:Int,val events:Int)
data class TimeBandPattern(val label:String,val events:Int)
data class HighActivityDate(val date:String,val events:Int,val threshold:Double)
data class ContactHourPattern(val number:String,val hour:Int,val activeDates:Int,val events:Int)
data class WeekdayContactHourPattern(val weekday:String,val number:String,val hour:Int,val activeDates:Int,val events:Int)
data class PatternAnalysisResult(
    val daily:List<DailyPattern>, val weekdays:List<Pair<String,Int>>, val eventTypes:List<Pair<String,Int>>,
    val recurringContacts:List<RecurringContactPattern>, val hours:List<HourPattern>, val lowFrequencyHours:List<HourPattern>,
    val timeBands:List<TimeBandPattern>, val highDates:List<HighActivityDate>,
    val contactHours:List<ContactHourPattern>, val weekdayContactHours:List<WeekdayContactHourPattern>
)

object PatternAnalysis {
    fun build(rows:List<CdrRecord>):PatternAnalysisResult{
        data class R(val record:CdrRecord,val ts:Long,val date:String,val hour:Int,val weekday:String)
        val parsed=rows.mapNotNull{r->parseCdrTime(r.dateTime)?.let{ts->val cal=Calendar.getInstance().apply{timeInMillis=ts};R(r,ts,SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time),cal.get(Calendar.HOUR_OF_DAY),weekday(cal))}}
        val daily=parsed.groupBy{it.date}.map{(d,x)->DailyPattern(d,x.size,x.map{it.record.otherParty}.filter{it.isNotBlank()}.distinct().size,x.mapNotNull{tower(it.record)}.distinct().size)}.sortedBy{it.date}
        val weekdays=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").map{day->day to parsed.count{it.weekday==day}}
        val eventTypes=parsed.groupingBy{eventLabel(it.record.direction)}.eachCount().entries.sortedByDescending{it.value}.map{it.key to it.value}
        val recurring=parsed.filter{it.record.otherParty.isNotBlank()}.groupBy{it.record.otherParty}.map{(n,x)->RecurringContactPattern(n,x.map{it.date}.distinct().size,x.size)}.filter{it.activeDates>1}.sortedWith(compareByDescending<RecurringContactPattern>{it.activeDates}.thenByDescending{it.events})
        val hours=(0..23).map{h->HourPattern(h,parsed.count{it.hour==h})}
        val peak=(hours.maxOfOrNull{it.events}?:0)
        val low=if(peak==0)emptyList() else hours.filter{it.events>0&&it.events<=peak*0.2}.sortedBy{it.events}
        val bands=listOf("00–05" to (0..5),"06–11" to (6..11),"12–17" to (12..17),"18–23" to (18..23)).map{(l,range)->TimeBandPattern(l,parsed.count{it.hour in range})}
        val values=daily.map{it.events.toDouble()};val mean=values.average().takeIf{!it.isNaN()}?:0.0;val sd=if(values.isEmpty())0.0 else sqrt(values.sumOf{(it-mean)*(it-mean)}/values.size);val threshold=mean+2*sd
        val high=daily.filter{it.events>threshold&&it.events>0}.map{HighActivityDate(it.date,it.events,threshold)}
        val contactHours=parsed.filter{it.record.otherParty.isNotBlank()}.groupBy{it.record.otherParty to it.hour}.map{(k,x)->ContactHourPattern(k.first,k.second,x.map{it.date}.distinct().size,x.size)}.filter{it.activeDates>1}.sortedWith(compareByDescending<ContactHourPattern>{it.activeDates}.thenByDescending{it.events})
        val weekdayContactHours=parsed.filter{it.record.otherParty.isNotBlank()}.groupBy{Triple(it.weekday,it.record.otherParty,it.hour)}.map{(k,x)->WeekdayContactHourPattern(k.first,k.second,k.third,x.map{it.date}.distinct().size,x.size)}.filter{it.activeDates>1}.sortedWith(compareByDescending<WeekdayContactHourPattern>{it.activeDates}.thenByDescending{it.events})
        return PatternAnalysisResult(daily,weekdays,eventTypes,recurring,hours,low,bands,high,contactHours,weekdayContactHours)
    }
    private fun weekday(cal:Calendar)=when(cal.get(Calendar.DAY_OF_WEEK)){Calendar.MONDAY->"Mon";Calendar.TUESDAY->"Tue";Calendar.WEDNESDAY->"Wed";Calendar.THURSDAY->"Thu";Calendar.FRIDAY->"Fri";Calendar.SATURDAY->"Sat";else->"Sun"}
    private fun eventLabel(value:String):String{val s=value.lowercase();return when{ "sms" in s->"SMS";"incoming" in s||s=="in"||"mti" in s->"Incoming";"outgoing" in s||s=="out"||"moc" in s->"Outgoing";value.isBlank()->"Unknown";else->value}}
    private fun tower(r:CdrRecord):String?=r.cellId.takeIf{it.isNotBlank()}?.let{listOf(r.lac,it).filter{v->v.isNotBlank()}.joinToString("/")}
}
