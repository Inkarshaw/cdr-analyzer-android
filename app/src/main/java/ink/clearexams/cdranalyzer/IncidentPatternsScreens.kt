package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun IncidentAnalysisScreen(rows:List<CdrRecord>){
    var incidentText by remember{mutableStateOf("")};var beforeHours by remember{mutableIntStateOf(6)};var duringMinutes by remember{mutableIntStateOf(30)};var afterHours by remember{mutableIntStateOf(6)}
    val incident=parseCdrTime(incidentText)
    val result=remember(rows,incident,beforeHours,duringMinutes,afterHours){incident?.let{IncidentAnalysis.build(rows,it,beforeHours,duringMinutes,afterHours)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Before / During / After Incident",style=MaterialTheme.typography.titleLarge)}
        item{Text("Enter the incident date/time in the same timestamp format used by the CDR, for example 29-09-2026 18:30.",style=MaterialTheme.typography.bodySmall)}
        item{OutlinedTextField(incidentText,{incidentText=it},label={Text("Incident date/time")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            item{Text("Before",modifier=Modifier.padding(top=12.dp))};items(listOf(1,3,6,12,24)){v->FilterChip(beforeHours==v,{beforeHours=v},{Text("${v}h")})}
        }}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            item{Text("During ±",modifier=Modifier.padding(top=12.dp))};items(listOf(10,30,60,120)){v->FilterChip(duringMinutes==v,{duringMinutes=v},{Text("${v}m")})}
        }}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            item{Text("After",modifier=Modifier.padding(top=12.dp))};items(listOf(1,3,6,12,24)){v->FilterChip(afterHours==v,{afterHours=v},{Text("${v}h")})}
        }}
        if(incidentText.isNotBlank()&&incident==null)item{Text("Incident date/time could not be parsed.",color=MaterialTheme.colorScheme.error)}
        result?.let{r->
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){IncidentMetric("Before",r.before.size.toString(),Modifier.weight(1f));IncidentMetric("During",r.during.size.toString(),Modifier.weight(1f));IncidentMetric("After",r.after.size.toString(),Modifier.weight(1f))}}
            item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("Baseline comparison",style=MaterialTheme.typography.titleSmall);Text("Previous 7-day baseline before the selected window: ${r.baselineRecords} record(s)");Text("Incident ±${duringMinutes} min: ${r.incidentRecords} record(s)");Text("Counts describe CDR activity only and do not establish significance or intent.",style=MaterialTheme.typography.labelSmall)}}}
            item{Text("New / disappearing contacts",style=MaterialTheme.typography.titleMedium)}
            item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("New in during/after (${r.newContacts.size})",style=MaterialTheme.typography.labelLarge);Text(if(r.newContacts.isEmpty())"None" else r.newContacts.take(50).joinToString());Spacer(Modifier.height(6.dp));Text("Seen before but not after (${r.disappearingContacts.size})",style=MaterialTheme.typography.labelLarge);Text(if(r.disappearingContacts.isEmpty())"None" else r.disappearingContacts.take(50).joinToString())}}}
            item{Text("Incident timeline",style=MaterialTheme.typography.titleMedium)}
            items(r.timeline.take(1500)){e->ListItem(headlineContent={Text("${e.phase} • ${e.record.dateTime}")},supportingContent={Text("${e.record.number} ↔ ${e.record.otherParty} • ${e.record.direction} • ${if(e.record.cellId.isBlank())"tower unavailable" else "${e.record.lac}/${e.record.cellId}"}")});HorizontalDivider()}
        }
    }
}

@Composable private fun IncidentMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}

@Composable
fun PatternsScreen(rows:List<CdrRecord>){
    val result=remember(rows){PatternAnalysis.build(rows)}
    data class MatrixRow(val date:String,val hour:Int,val contact:String)
    val matrixRows=remember(rows){rows.mapNotNull{r->parseCdrTime(r.dateTime)?.let{ts->val cal=java.util.Calendar.getInstance().apply{timeInMillis=ts};MatrixRow(java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(cal.time),cal.get(java.util.Calendar.HOUR_OF_DAY),r.otherParty)}}}
    val matrixDates=remember(matrixRows){matrixRows.map{it.date}.distinct().sorted().takeLast(14)}
    val topMatrixContacts=remember(matrixRows){matrixRows.filter{it.contact.isNotBlank()}.groupingBy{it.contact}.eachCount().entries.sortedByDescending{it.value}.take(10).map{it.key}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Activity Patterns",style=MaterialTheme.typography.titleLarge)}
        item{Text("Pattern summaries are descriptive metadata. Lower-frequency hours and statistical high-activity dates are review indicators, not conclusions.",style=MaterialTheme.typography.bodySmall)}
        item{Text("Weekday pattern",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(result.weekdays){p->PatternMetric(p.first,p.second.toString())}}}
        item{Text("Event-type pattern",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(result.eventTypes){p->PatternMetric(p.first,p.second.toString())}}}
        item{Text("Time-band pattern",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(result.timeBands){p->PatternMetric(p.label,p.events.toString())}}}
        item{Text("Day-wise summary",style=MaterialTheme.typography.titleMedium)}
        items(result.daily.takeLast(60)){d->ListItem(headlineContent={Text(d.date)},supportingContent={Text("${d.events} event(s) • ${d.contacts} contact(s) • ${d.towers} tower(s)")});HorizontalDivider()}
        item{Text("Recurring contacts",style=MaterialTheme.typography.titleMedium)}
        if(result.recurringContacts.isEmpty())item{Text("No contact recurred across multiple active dates.")}else items(result.recurringContacts.take(100)){p->ListItem(headlineContent={Text(p.number)},supportingContent={Text("${p.activeDates} active date(s) • ${p.events} event(s)")});HorizontalDivider()}
        item{Text("Lower-frequency-hour activity",style=MaterialTheme.typography.titleMedium)}
        if(result.lowFrequencyHours.isEmpty())item{Text("No non-zero hour is at or below 20% of the peak-hour activity.")}else item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(result.lowFrequencyHours){h->PatternMetric("%02d:00".format(Locale.US,h.hour),h.events.toString())}}}
        item{Text("Statistical high-activity dates",style=MaterialTheme.typography.titleMedium)}
        if(result.highDates.isEmpty())item{Text("No date exceeds mean + 2 standard deviations.")}else items(result.highDates){d->ListItem(headlineContent={Text(d.date)},supportingContent={Text("${d.events} events • threshold ${"%.1f".format(Locale.US,d.threshold)}")});HorizontalDivider()}
        item{Text("Recurring contact + hour",style=MaterialTheme.typography.titleMedium)}
        items(result.contactHours.take(100)){p->ListItem(headlineContent={Text("${p.number} • %02d:00".format(Locale.US,p.hour))},supportingContent={Text("${p.activeDates} date(s) • ${p.events} event(s)")});HorizontalDivider()}
        item{Text("Recurring weekday + contact + hour",style=MaterialTheme.typography.titleMedium)}
        items(result.weekdayContactHours.take(100)){p->ListItem(headlineContent={Text("${p.weekday} • ${p.number} • %02d:00".format(Locale.US,p.hour))},supportingContent={Text("${p.activeDates} date(s) • ${p.events} event(s)")});HorizontalDivider()}
        item{Text("24-hour activity",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){items(result.hours){h->PatternMetric("%02d".format(Locale.US,h.hour),h.events.toString())}}}
        item{Text("Date × hour activity matrix",style=MaterialTheme.typography.titleMedium)}
        if(matrixDates.isEmpty())item{Text("No parseable timestamps for the matrix.")}else items(matrixDates){date->val counts=(0..23).map{h->matrixRows.count{it.date==date&&it.hour==h}};Column{Text(date,style=MaterialTheme.typography.labelMedium);LazyRow(horizontalArrangement=Arrangement.spacedBy(3.dp)){items(counts.mapIndexed{i,v->i to v}){cell->Surface(tonalElevation=if(cell.second>0)2.dp else 0.dp,shape=MaterialTheme.shapes.small){Column(Modifier.width(42.dp).padding(4.dp)){Text("%02d".format(Locale.US,cell.first),style=MaterialTheme.typography.labelSmall);Text(cell.second.toString(),style=MaterialTheme.typography.bodySmall)}}}}}}
        item{Text("Contact × day matrix",style=MaterialTheme.typography.titleMedium)}
        if(topMatrixContacts.isEmpty())item{Text("No B-party contacts available for the matrix.")}else items(topMatrixContacts){contact->Column{Text(contact,style=MaterialTheme.typography.labelMedium);LazyRow(horizontalArrangement=Arrangement.spacedBy(3.dp)){items(matrixDates){date->val count=matrixRows.count{it.contact==contact&&it.date==date};Surface(tonalElevation=if(count>0)2.dp else 0.dp,shape=MaterialTheme.shapes.small){Column(Modifier.width(82.dp).padding(4.dp)){Text(date.takeLast(5),style=MaterialTheme.typography.labelSmall);Text(count.toString(),style=MaterialTheme.typography.bodySmall)}}}}}}
    }
}

@Composable private fun PatternMetric(label:String,value:String){Surface(tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(horizontal=10.dp,vertical=7.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}
