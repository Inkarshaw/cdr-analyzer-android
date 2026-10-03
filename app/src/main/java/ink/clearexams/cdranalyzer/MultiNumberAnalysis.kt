package ink.clearexams.cdranalyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MultiContact(val number:String,val subjects:List<String>,val records:Int)
data class MultiLocationEpisode(val tower:String,val start:String,val end:String,val subjects:List<String>,val records:Int,val durationMinutes:Long)
data class MultiSharedIdentifier(val value:String,val subjects:List<String>,val records:Int)
data class MultiDirectLink(val first:String,val second:String,val records:Int)
data class MultiNumberResult(val contacts:List<MultiContact>,val locations:List<MultiLocationEpisode>,val sharedImeis:List<MultiSharedIdentifier>,val sharedImsis:List<MultiSharedIdentifier>,val directLinks:List<MultiDirectLink>)

object MultiNumberAnalysis{
    fun build(rows:List<CdrRecord>,subjects:Set<String>,windowMinutes:Int,episodeGapMinutes:Int,requireAll:Boolean):MultiNumberResult{
        if(subjects.size<2)return MultiNumberResult(emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val scoped=rows.filter{it.number in subjects}
        fun qualifies(found:Set<String>)=if(requireAll)found.containsAll(subjects) else found.size>=2
        val contacts=scoped.filter{it.otherParty.isNotBlank()}.groupBy{it.otherParty}.mapNotNull{(n,x)->val s=x.map{it.number}.distinct();if(!qualifies(s.toSet()))null else MultiContact(n,s.sorted(),x.size)}.sortedWith(compareByDescending<MultiContact>{it.subjects.size}.thenByDescending{it.records})
        data class T(val subject:String,val tower:String,val ts:Long,val at:String)
        val towerEvents=scoped.mapNotNull{r->val ts=parseCdrTime(r.dateTime)?:return@mapNotNull null;val tower=r.cellId.takeIf{it.isNotBlank()}?.let{listOf(r.operator.ifBlank{r.lac},it).filter{v->v.isNotBlank()}.joinToString("/") }?:return@mapNotNull null;T(r.number,tower,ts,r.dateTime)}
        val rawMatches=mutableListOf<MultiLocationEpisode>()
        towerEvents.groupBy{it.tower}.forEach{(tower,events)->
            val sorted=events.sortedBy{it.ts};var left=0
            for(right in sorted.indices){
                while(left<=right&&sorted[right].ts-sorted[left].ts>windowMinutes*60000L)left++
                val window=sorted.subList(left,right+1);val found=window.map{it.subject}.toSet()
                if(qualifies(found)){val first=window.first();val last=window.last();rawMatches+=MultiLocationEpisode(tower,first.at,last.at,found.sorted(),window.size,((last.ts-first.ts)/60000L).coerceAtLeast(0))}
            }
        }
        val episodes=mutableListOf<MultiLocationEpisode>()
        rawMatches.groupBy{it.tower}.forEach{(_,matches)->
            val sorted=matches.sortedBy{parseCdrTime(it.start)?:Long.MAX_VALUE};var current:MultiLocationEpisode?=null
            for(m in sorted){val c=current;if(c==null){current=m;continue};val cEnd=parseCdrTime(c.end)?:0L;val mStart=parseCdrTime(m.start)?:Long.MAX_VALUE;if(mStart-cEnd<=episodeGapMinutes*60000L){val mergedSubjects=(c.subjects+m.subjects).distinct().sorted();val startTs=parseCdrTime(c.start)?:mStart;val endTs=maxOf(parseCdrTime(c.end)?:mStart,parseCdrTime(m.end)?:mStart);current=c.copy(end=if((parseCdrTime(m.end)?:0L)>cEnd)m.end else c.end,subjects=mergedSubjects,records=maxOf(c.records,m.records),durationMinutes=((endTs-startTs)/60000L).coerceAtLeast(0))}else{episodes+=c;current=m}};current?.let(episodes::add)
        }
        fun shared(extractor:(CdrRecord)->String):List<MultiSharedIdentifier>{return scoped.mapNotNull{r->extractor(r).takeIf{it.isNotBlank()}?.let{it to r.number}}.groupBy{it.first}.mapNotNull{(value,x)->val s=x.map{it.second}.distinct();if(!qualifies(s.toSet()))null else MultiSharedIdentifier(value,s.sorted(),x.size)}.sortedWith(compareByDescending<MultiSharedIdentifier>{it.subjects.size}.thenByDescending{it.records})}
        val imeis=shared{it.imei};val imsis=shared{it.imsi}
        val direct=mutableMapOf<Pair<String,String>,Int>();scoped.forEach{r->if(r.otherParty in subjects&&r.otherParty!=r.number){val a=if(r.number<=r.otherParty)r.number else r.otherParty;val b=if(r.number<=r.otherParty)r.otherParty else r.number;direct[a to b]=(direct[a to b]?:0)+1}}
        val links=direct.map{(k,v)->MultiDirectLink(k.first,k.second,v)}.sortedByDescending{it.records}
        return MultiNumberResult(contacts,episodes.sortedByDescending{it.records},imeis,imsis,links)
    }
}

@Composable
fun MultiNumberAnalysisScreen(rows:List<CdrRecord>){
    val subjects=remember(rows){rows.map{it.number}.filter{it.isNotBlank()}.distinct().sorted()}
    var selected by remember(subjects){mutableStateOf(subjects.toSet())};var window by remember{mutableIntStateOf(15)};var gap by remember{mutableIntStateOf(60)};var requireAll by remember{mutableStateOf(true)}
    val result=remember(rows,selected,window,gap,requireAll){MultiNumberAnalysis.build(rows,selected,window,gap,requireAll)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Multi-number Analysis",style=MaterialTheme.typography.titleLarge)}
        item{Text("Shared contacts, same-tower timing and identifiers are correlation leads that require independent verification.",style=MaterialTheme.typography.bodySmall)}
        if(subjects.size<2)item{Text("Load CDR records for at least two different A-party / subject numbers.")}else{
            item{Text("Subjects",style=MaterialTheme.typography.titleSmall);LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(subjects){s->FilterChip(s in selected,{selected=if(s in selected)selected-s else selected+s},{Text(s)})}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(requireAll,{requireAll=true},{Text("All selected")});FilterChip(!requireAll,{requireAll=false},{Text("Any pair")})}}
            item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(5,15,30,60)){m->FilterChip(window==m,{window=m},{Text("±${m}m")})}}}
            item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf(30,60,120,360)){m->FilterChip(gap==m,{gap=m},{Text("Episode ${m}m")})}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){MultiMetric("Common contacts",result.contacts.size.toString(),Modifier.weight(1f));MultiMetric("Location episodes",result.locations.size.toString(),Modifier.weight(1f));MultiMetric("Direct links",result.directLinks.size.toString(),Modifier.weight(1f))}}
            item{CommunicationNetworkCard(selected.toList().sorted(),result.contacts.take(12),rows)}
            item{Text("Matching location episodes",style=MaterialTheme.typography.titleMedium)}
            if(result.locations.isEmpty())item{Text("No same-tower time-window match for the selected subjects.")}else items(result.locations.take(100)){m->ListItem(headlineContent={Text("Tower ${m.tower}")},supportingContent={Text("${m.start} → ${m.end} • ${m.records} event(s) • ${m.durationMinutes} min\nSubjects: ${m.subjects.joinToString()}")});HorizontalDivider()}
            item{Text("Common contacts",style=MaterialTheme.typography.titleMedium)}
            items(result.contacts.take(100)){m->ListItem(headlineContent={Text(m.number)},supportingContent={Text("${m.records} event(s) • ${m.subjects.size} subject(s): ${m.subjects.joinToString()}")});HorizontalDivider()}
            item{Text("Shared IMEI",style=MaterialTheme.typography.titleMedium)}
            if(result.sharedImeis.isEmpty())item{Text("No shared IMEI across the selected subjects.")}else items(result.sharedImeis.take(100)){m->ListItem(headlineContent={Text(m.value)},supportingContent={Text("${m.records} record(s) • ${m.subjects.joinToString()}")});HorizontalDivider()}
            item{Text("Shared IMSI",style=MaterialTheme.typography.titleMedium)}
            if(result.sharedImsis.isEmpty())item{Text("No shared IMSI across the selected subjects.")}else items(result.sharedImsis.take(100)){m->ListItem(headlineContent={Text(m.value)},supportingContent={Text("${m.records} record(s) • ${m.subjects.joinToString()}")});HorizontalDivider()}
            item{Text("Direct subject relationships",style=MaterialTheme.typography.titleMedium)}
            if(result.directLinks.isEmpty())item{Text("No direct subject-to-subject communication record found.")}else items(result.directLinks){l->ListItem(headlineContent={Text("${l.first} ↔ ${l.second}")},supportingContent={Text("${l.records} direct interaction record(s)")});HorizontalDivider()}
            item{NumberRelationshipAnalysis(rows)}
        }
    }
}
@Composable
private fun CommunicationNetworkCard(subjects:List<String>,contacts:List<MultiContact>,rows:List<CdrRecord>){
    if(subjects.isEmpty())return
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    val outline=MaterialTheme.colorScheme.outline
    val maxRecords=(contacts.maxOfOrNull{it.records}?:1).coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Communication Network",style=MaterialTheme.typography.titleMedium)
            Text("Selected subjects linked to the most frequent shared contacts.",style=MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(260.dp)){
                fun subjectPos(i:Int)=Offset(42.dp.toPx(),((i+1).toFloat()/(subjects.size+1).toFloat())*size.height)
                fun contactPos(i:Int)=Offset(size.width-42.dp.toPx(),((i+1).toFloat()/(contacts.size+1).toFloat())*size.height)
                subjects.forEachIndexed{si,subject->
                    contacts.forEachIndexed{ci,contact->
                        val count=rows.count{it.number==subject&&it.otherParty==contact.number}
                        if(count>0)drawLine(outline,subjectPos(si),contactPos(ci),strokeWidth=1.5f+5f*(count.toFloat()/maxRecords.toFloat()))
                    }
                }
                subjects.forEachIndexed{i,_->drawCircle(primary,12.dp.toPx(),subjectPos(i),style=Stroke(width=4.dp.toPx()))}
                contacts.forEachIndexed{i,_->drawCircle(secondary,9.dp.toPx(),contactPos(i))}
            }
            Text("Subjects: ${subjects.joinToString()}",style=MaterialTheme.typography.labelSmall)
            if(contacts.isNotEmpty())Text("Top shared contacts: ${contacts.joinToString{it.number}}",style=MaterialTheme.typography.labelSmall)
            Text("Edge weight reflects CDR record counts only and does not establish association or intent.",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun MultiMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}

@Composable
private fun NumberRelationshipAnalysis(rows:List<CdrRecord>){
    val subjects=remember(rows){rows.map{it.number}.filter{it.isNotBlank()}.distinct().sorted()}
    val allNumbers=remember(rows){(rows.map{it.number}+rows.map{it.otherParty}).filter{it.isNotBlank()}.distinct().sorted()}
    var personA by remember(subjects){mutableStateOf(subjects.firstOrNull().orEmpty())}
    var personB by remember{mutableStateOf("")}
    var dateFrom by remember{mutableStateOf("")};var dateTo by remember{mutableStateOf("")};var event by remember{mutableStateOf("")};var nightOnly by remember{mutableStateOf(false)}
    var aMenu by remember{mutableStateOf(false)};var bMenu by remember{mutableStateOf(false)}
    val filtered=remember(rows,personA,personB,dateFrom,dateTo,event,nightOnly){
        val from=relationshipDateBound(dateFrom,false);val to=relationshipDateBound(dateTo,true)
        rows.filter{r->
            val pair=((r.number==personA&&r.otherParty==personB)||(r.number==personB&&r.otherParty==personA))
            val ts=parseCdrTime(r.dateTime);val hour=ts?.let{Calendar.getInstance().apply{timeInMillis=it}.get(Calendar.HOUR_OF_DAY)}
            pair&&(from==null||(ts!=null&&ts>=from))&&(to==null||(ts!=null&&ts<=to))&&
                (event.isBlank()||r.direction.contains(event,true))&&(!nightOnly||hour?.let{it>=20||it<6}==true)
        }
    }
    val commonTowers=remember(rows,personA,personB){
        if(personA.isBlank()||personB.isBlank())emptyList() else {
            val a=rows.filter{it.number==personA||it.otherParty==personA}.mapNotNull{r->r.cellId.takeIf{it.isNotBlank()}?.let{listOf(r.lac,it).filter{v->v.isNotBlank()}.joinToString("/")}}.toSet()
            val b=rows.filter{it.number==personB||it.otherParty==personB}.mapNotNull{r->r.cellId.takeIf{it.isNotBlank()}?.let{listOf(r.lac,it).filter{v->v.isNotBlank()}.joinToString("/")}}.toSet()
            (a intersect b).sorted()
        }
    }
    val sharedImei=remember(rows,personA,personB){sharedIdentifierForPair(rows,personA,personB){it.imei}}
    val sharedImsi=remember(rows,personA,personB){sharedIdentifierForPair(rows,personA,personB){it.imsi}}
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Number Relationship Analysis",style=MaterialTheme.typography.titleMedium)
            Text("Summarizes communication metadata between two numbers. Treat shared towers and identifiers as review leads, not proof of identity, location, association or intent.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.weight(1f)){OutlinedButton({aMenu=true},Modifier.fillMaxWidth()){Text(personA.ifBlank{"Person A"})};DropdownMenu(aMenu,{aMenu=false}){subjects.forEach{s->DropdownMenuItem({Text(s)},{personA=s;aMenu=false})}}}
                Box(Modifier.weight(1f)){OutlinedButton({bMenu=true},Modifier.fillMaxWidth()){Text(personB.ifBlank{"Person B"})};DropdownMenu(bMenu,{bMenu=false}){allNumbers.filter{it!=personA}.take(500).forEach{s->DropdownMenuItem({Text(s)},{personB=s;bMenu=false})}}}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedTextField(dateFrom,{dateFrom=it},label={Text("From YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(dateTo,{dateTo=it},label={Text("To YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
            }
            OutlinedTextField(event,{event=it},label={Text("Event type contains")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(nightOnly,{nightOnly=!nightOnly},{Text("Night only")});OutlinedButton({val t=personA;personA=personB;personB=t}){Text("⇄ Swap")}}
            if(personA.isBlank()||personB.isBlank())Text("Select Person A and Person B to analyse the pair.",style=MaterialTheme.typography.bodySmall) else {
                Text("${filtered.size} direct communication record(s)",style=MaterialTheme.typography.labelLarge)
                Text("Common towers: ${if(commonTowers.isEmpty())"None" else commonTowers.take(20).joinToString()}",style=MaterialTheme.typography.bodySmall)
                Text("Shared IMEI: ${if(sharedImei.isEmpty())"None" else sharedImei.joinToString()}",style=MaterialTheme.typography.bodySmall)
                Text("Shared IMSI: ${if(sharedImsi.isEmpty())"None" else sharedImsi.joinToString()}",style=MaterialTheme.typography.bodySmall)
                if(filtered.isNotEmpty()){
                    Text("Communication timeline",style=MaterialTheme.typography.titleSmall)
                    filtered.take(100).forEach{r->Text(listOf(r.dateTime,r.direction,r.duration.takeIf{it.isNotBlank()}?.let{"${it}s"}.orEmpty(),r.cellId.takeIf{it.isNotBlank()}?.let{"Tower ${listOf(r.lac,it).filter{v->v.isNotBlank()}.joinToString("/")}"} .orEmpty()).filter{it.isNotBlank()}.joinToString(" • "),style=MaterialTheme.typography.bodySmall)}
                }
            }
        }
    }
}
private fun relationshipDateBound(value:String,end:Boolean):Long?{if(value.isBlank())return null;return runCatching{val d=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(value.trim())?:return@runCatching null;Calendar.getInstance().apply{time=d;set(Calendar.HOUR_OF_DAY,if(end)23 else 0);set(Calendar.MINUTE,if(end)59 else 0);set(Calendar.SECOND,if(end)59 else 0);set(Calendar.MILLISECOND,if(end)999 else 0)}.timeInMillis}.getOrNull()}
private fun sharedIdentifierForPair(rows:List<CdrRecord>,a:String,b:String,extract:(CdrRecord)->String):List<String>{
    if(a.isBlank()||b.isBlank())return emptyList()
    fun ids(n:String)=rows.filter{it.number==n||it.otherParty==n}.map(extract).filter{it.isNotBlank()}.toSet()
    return (ids(a) intersect ids(b)).sorted()
}
