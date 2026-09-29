package ink.clearexams.cdranalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        }
    }
}
@Composable private fun MultiMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}
