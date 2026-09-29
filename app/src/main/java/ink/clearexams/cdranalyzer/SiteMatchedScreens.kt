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

@Composable
fun SiteRecordsScreen(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onTag:(String)->Unit){
    var pageSize by remember{mutableIntStateOf(100)}
    var page by remember{mutableIntStateOf(0)}
    val pageCount=((rows.size+pageSize-1)/pageSize).coerceAtLeast(1)
    if(page>=pageCount)page=pageCount-1
    val pageRows=rows.drop(page*pageSize).take(pageSize)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp)){
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Records",style=MaterialTheme.typography.titleLarge);Text("${rows.size} rows",style=MaterialTheme.typography.labelMedium)};Text("Quick review view • open Excel View for all original worksheet columns.",style=MaterialTheme.typography.bodySmall)}}}
        items(pageRows){r->
            val number=r.otherParty.ifBlank{r.number};val tag=tags[number];val label=if(tag!=null&&tag.name.isNotBlank())"${tag.name} ($number)" else number.ifBlank{"Unknown"}
            val tower=if(r.cellId.isBlank())"" else listOf(r.lac,r.cellId).filter{it.isNotBlank()}.joinToString("/")
            ListItem(
                headlineContent={Text(label)},
                supportingContent={Text(listOf(r.dateTime,r.direction,r.duration.takeIf{it.isNotBlank()}?.let{"${it}s"}.orEmpty(),tower.takeIf{it.isNotBlank()}?.let{"Tower $it"}.orEmpty()).filter{it.isNotBlank()}.joinToString(" • "))},
                modifier=Modifier.clickable{if(number.isNotBlank())onTag(number)}
            );HorizontalDivider()
        }
        item{Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton({page=(page-1).coerceAtLeast(0)},enabled=page>0){Text("Previous")};Text("Page ${page+1} of $pageCount",style=MaterialTheme.typography.labelMedium);OutlinedButton({page=(page+1).coerceAtMost(pageCount-1)},enabled=page<pageCount-1){Text("Next")}}}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(50,100,250,500)){size->FilterChip(pageSize==size,{pageSize=size;page=0},{Text("$size rows")})}}}
    }
}

@Composable
fun SiteLocationsScreen(rows:List<CdrRecord>){
    val subjects=remember(rows){rows.map{it.number}.filter{it.isNotBlank()}.distinct().sorted()}
    var selected by remember(subjects){mutableStateOf(subjects.toSet())}
    var matchAll by remember{mutableStateOf(true)}
    var window by remember{mutableIntStateOf(15)}
    var gap by remember{mutableIntStateOf(60)}
    val result=remember(rows,selected,matchAll,window,gap){MultiNumberAnalysis.build(rows,selected,window,gap,matchAll)}
    val towers=remember(rows){rows.filter{it.cellId.isNotBlank()}.groupBy{listOf(it.operator.ifBlank{it.lac},it.cellId).filter{v->v.isNotBlank()}.joinToString("/")}.entries.sortedByDescending{it.value.size}}
    val mapped=rows.count{it.latitude.toDoubleOrNull()!=null&&it.longitude.toDoubleOrNull()!=null}
    val repeated=towers.count{it.value.size>1}
    val operators=rows.map{it.operator}.filter{it.isNotBlank()}.distinct().size
    val sharedTowers=remember(rows){towers.mapNotNull{e->val s=e.value.map{it.number}.filter{it.isNotBlank()}.distinct();if(s.size<2)null else Triple(e.key,s,e.value.size)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Location / tower analysis",style=MaterialTheme.typography.titleLarge)}
        item{Text("Based on first cell ID / tower metadata. Same-tower timing is a network-metadata lead, not proof of exact physical co-presence.",style=MaterialTheme.typography.bodySmall)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){LocationMetric("Towers",towers.size.toString(),Modifier.weight(1f));LocationMetric("Mapped",mapped.toString(),Modifier.weight(1f));LocationMetric("Repeated",repeated.toString(),Modifier.weight(1f));LocationMetric("Operators",operators.toString(),Modifier.weight(1f))}}
        item{Text("Multi-number location match",style=MaterialTheme.typography.titleMedium)}
        if(subjects.size<2)item{Text("Load at least two subject numbers to compare matching tower observations.")}else{
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(matchAll,{matchAll=true},{Text("All selected numbers")});FilterChip(!matchAll,{matchAll=false},{Text("Any pair")})}}
            item{Text("Time window",style=MaterialTheme.typography.labelMedium);LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(5,15,30,60)){m->FilterChip(window==m,{window=m},{Text("${m}m")})}}}
            item{Text("Episode break gap",style=MaterialTheme.typography.labelMedium);LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(30,60,120,360)){m->FilterChip(gap==m,{gap=m},{Text("${m}m")})}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton({selected=subjects.toSet()},Modifier.weight(1f)){Text("Select all")};OutlinedButton({selected=emptySet()},Modifier.weight(1f)){Text("Clear")}}}
            item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(subjects){s->FilterChip(s in selected,{selected=if(s in selected)selected-s else selected+s},{Text(s)})}}}
            if(result.locations.isEmpty())item{Text("No matching location episode for the selected subjects/window.",style=MaterialTheme.typography.bodySmall)}else items(result.locations.take(100)){m->ListItem(headlineContent={Text("Tower ${m.tower}")},supportingContent={Text("${m.start} → ${m.end} • ${m.records} event(s) • ${m.durationMinutes} min\n${m.subjects.joinToString()}")});HorizontalDivider()}
        }
        item{Text("Repeated shared towers",style=MaterialTheme.typography.titleMedium)}
        if(sharedTowers.isEmpty())item{Text("No tower identity appears across multiple loaded subjects.")}else items(sharedTowers.take(100)){m->ListItem(headlineContent={Text(m.first)},supportingContent={Text("${m.third} record(s) • subjects: ${m.second.joinToString()}")});HorizontalDivider()}
        item{Text("Tower summary",style=MaterialTheme.typography.titleMedium)}
        items(towers.take(200)){e->val first=e.value.minByOrNull{parseCdrTime(it.dateTime)?:Long.MAX_VALUE};val last=e.value.maxByOrNull{parseCdrTime(it.dateTime)?:Long.MIN_VALUE};ListItem(headlineContent={Text(e.key)},supportingContent={Text("${e.value.size} record(s) • first ${first?.dateTime.orEmpty().ifBlank{"Unknown"}} • last ${last?.dateTime.orEmpty().ifBlank{"Unknown"}}")});HorizontalDivider()}
    }
}

@Composable private fun LocationMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(7.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}
