package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

data class DashboardLead(val title:String,val detail:String,val records:Int)

@Composable
fun CdrDashboardScreen(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onOpenContact:(String)->Unit={}){
    var burstMinutes by remember{mutableIntStateOf(10)};var burstMinimum by remember{mutableIntStateOf(3)};var longCallSec by remember{mutableIntStateOf(600)}
    var subjectScope by remember{mutableStateOf("")};var eventScope by remember{mutableStateOf("")};var subjectMenu by remember{mutableStateOf(false)};var eventMenu by remember{mutableStateOf(false)}
    val subjects=remember(rows){rows.map{it.number}.filter{it.isNotBlank()}.distinct().sorted()}
    val allEvents=remember(rows){rows.map{dashboardEvent(it.direction)}.distinct().sorted()}
    val scoped=remember(rows,subjectScope,eventScope){rows.filter{r->(subjectScope.isBlank()||r.number==subjectScope)&&(eventScope.isBlank()||dashboardEvent(r.direction)==eventScope)}}
    val contacts=remember(scoped){scoped.map{it.otherParty}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}}
    val towers=remember(scoped){scoped.filter{it.cellId.isNotBlank()}.groupingBy{listOf(it.lac,it.cellId).filter{v->v.isNotBlank()}.joinToString("/")}.eachCount().entries.sortedByDescending{it.value}}
    val imeis=remember(scoped){scoped.map{it.imei}.filter{it.isNotBlank()}.distinct()}
    val imsis=remember(scoped){scoped.map{it.imsi}.filter{it.isNotBlank()}.distinct()}
    val hourly=remember(scoped){(0..23).map{h->h to scoped.count{r->parseCdrTime(r.dateTime)?.let{ts->Calendar.getInstance().apply{timeInMillis=ts}.get(Calendar.HOUR_OF_DAY)==h}==true}}}
    val eventTypes=remember(scoped){scoped.groupingBy{dashboardEvent(it.direction)}.eachCount().entries.sortedByDescending{it.value}}
    val leads=remember(scoped,burstMinutes,burstMinimum,longCallSec){dashboardLeads(scoped,burstMinutes,burstMinimum,longCallSec)}
    val maxContact=(contacts.maxOfOrNull{it.value}?:1).coerceAtLeast(1);val maxHour=(hourly.maxOfOrNull{it.second}?:1).coerceAtLeast(1);val maxType=(eventTypes.maxOfOrNull{it.value}?:1).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Case dashboard",style=MaterialTheme.typography.titleLarge)}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Dashboard scope",style=MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.weight(1f)){OutlinedButton({subjectMenu=true},Modifier.fillMaxWidth()){Text(subjectScope.ifBlank{"All subjects"})};DropdownMenu(subjectMenu,{subjectMenu=false}){DropdownMenuItem({Text("All subjects")},{subjectScope="";subjectMenu=false});subjects.forEach{s->DropdownMenuItem({Text(s)},{subjectScope=s;subjectMenu=false})}}}
                Box(Modifier.weight(1f)){OutlinedButton({eventMenu=true},Modifier.fillMaxWidth()){Text(eventScope.ifBlank{"All event types"})};DropdownMenu(eventMenu,{eventMenu=false}){DropdownMenuItem({Text("All event types")},{eventScope="";eventMenu=false});allEvents.forEach{e->DropdownMenuItem({Text(e)},{eventScope=e;eventMenu=false})}}}
            }
            Text("Showing ${if(subjectScope.isBlank())"all subjects" else subjectScope} and ${if(eventScope.isBlank())"all event types" else eventScope} within the current filters.",style=MaterialTheme.typography.labelSmall)
        }}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DashboardMetric("Records",scoped.size.toString(),Modifier.weight(1f));DashboardMetric("Subjects",scoped.map{it.number}.filter{it.isNotBlank()}.distinct().size.toString(),Modifier.weight(1f));DashboardMetric("Contacts",contacts.size.toString(),Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DashboardMetric("Towers",towers.size.toString(),Modifier.weight(1f));DashboardMetric("IMEI",imeis.size.toString(),Modifier.weight(1f));DashboardMetric("IMSI",imsis.size.toString(),Modifier.weight(1f))}}
        item{Text("Top contacts",style=MaterialTheme.typography.titleMedium)}
        items(contacts.take(12)){e->val tag=tags[e.key];val label=if(tag!=null&&tag.name.isNotBlank())"${tag.name} (${e.key})" else e.key;Column(Modifier.fillMaxWidth().clickable{onOpenContact(e.key)}.padding(vertical=3.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.bodySmall);Text(e.value.toString(),style=MaterialTheme.typography.labelMedium)};LinearProgressIndicator(progress={e.value.toFloat()/maxContact.toFloat()},modifier=Modifier.fillMaxWidth())}}
        item{Text("Activity by hour",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(hourly){h->Column(Modifier.width(58.dp)){Text("%02d".format(Locale.US,h.first),style=MaterialTheme.typography.labelSmall);LinearProgressIndicator(progress={h.second.toFloat()/maxHour.toFloat()},modifier=Modifier.fillMaxWidth());Text(h.second.toString(),style=MaterialTheme.typography.labelSmall)}}}}
        item{Text("Event types",style=MaterialTheme.typography.titleMedium)}
        items(eventTypes.take(12)){e->Column(Modifier.fillMaxWidth().padding(vertical=2.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(e.key,style=MaterialTheme.typography.bodySmall);Text(e.value.toString(),style=MaterialTheme.typography.labelMedium)};LinearProgressIndicator(progress={e.value.toFloat()/maxType.toFloat()},modifier=Modifier.fillMaxWidth())}}
        item{Text("Top locations / towers",style=MaterialTheme.typography.titleMedium)}
        items(towers.take(10)){e->ListItem(headlineContent={Text(e.key)},supportingContent={Text("${e.value} record(s)")});HorizontalDivider()}
        item{Text("Device / SIM usage",style=MaterialTheme.typography.titleMedium)}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("${imeis.size} unique IMEI • ${imsis.size} unique IMSI");Text("${scoped.count{it.imei.isNotBlank()}} rows contain IMEI • ${scoped.count{it.imsi.isNotBlank()}} rows contain IMSI",style=MaterialTheme.typography.bodySmall)}}}
        item{Text("Review leads",style=MaterialTheme.typography.titleMedium)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(5,10,15,30)){v->FilterChip(burstMinutes==v,{burstMinutes=v},{Text("${v}m burst")})}}}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(3,4,5,8)){v->FilterChip(burstMinimum==v,{burstMinimum=v},{Text("$v events")})}}}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(300,600,1200,1800)){v->FilterChip(longCallSec==v,{longCallSec=v},{Text("${v/60}m call")})}}}
        if(leads.isEmpty())item{Text("No review lead meets the selected thresholds.",style=MaterialTheme.typography.bodySmall)}else items(leads.take(50)){lead->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(lead.title,style=MaterialTheme.typography.titleSmall);Text(lead.detail);Text("${lead.records} supporting record(s)",style=MaterialTheme.typography.labelSmall)}}}
        item{Text("Review leads are threshold-based prompts for manual verification, not findings of intent or wrongdoing.",style=MaterialTheme.typography.labelSmall)}
    }
}

@Composable private fun DashboardMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}

private fun dashboardEvent(value:String):String{val s=value.lowercase();return when{"sms" in s->"SMS";"incoming" in s||s=="in"||"mti" in s->"Incoming";"outgoing" in s||s=="out"||"moc" in s->"Outgoing";"data" in s||"gprs" in s->"Data";value.isBlank()->"Unknown";else->value}}
private fun dashboardLeads(rows:List<CdrRecord>,burstMinutes:Int,burstMinimum:Int,longCallSec:Int):List<DashboardLead>{
    val out=mutableListOf<DashboardLead>()
    val longCalls=rows.filter{it.duration.replace(",","").toDoubleOrNull()?.let{d->d>=longCallSec}==true}
    if(longCalls.isNotEmpty())out+=DashboardLead("Long call review","Calls at or above ${longCallSec}s threshold.",longCalls.size)
    rows.filter{it.otherParty.isNotBlank()}.groupBy{it.otherParty}.forEach{(contact,x)->
        val timed=x.mapNotNull{r->parseCdrTime(r.dateTime)?.let{it to r}}.sortedBy{it.first};var best=0;var left=0
        for(right in timed.indices){while(left<=right&&timed[right].first-timed[left].first>burstMinutes*60000L)left++;best=maxOf(best,right-left+1)}
        if(best>=burstMinimum)out+=DashboardLead("Contact burst: $contact","At least $best records fall within a ${burstMinutes}-minute rolling window.",best)
    }
    val night=rows.count{r->parseCdrTime(r.dateTime)?.let{ts->val h=Calendar.getInstance().apply{timeInMillis=ts}.get(Calendar.HOUR_OF_DAY);h>=22||h<6}==true}
    if(night>0)out+=DashboardLead("Night activity","CDR events recorded between 22:00 and 05:59.",night)
    return out.sortedByDescending{it.records}
}
