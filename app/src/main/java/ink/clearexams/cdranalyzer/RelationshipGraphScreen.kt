package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RelationshipGraphDialog(workspace:CaseWorkspace,onDismiss:()->Unit){
 val data=remember(workspace){RelationshipGraph.build(workspace)}
 var query by remember{mutableStateOf("")}
 var selected by remember{mutableStateOf<GraphNode?>(null)}
 val filtered=remember(data,query){if(query.isBlank())data.nodes else data.nodes.filter{it.id.contains(query,true)}}
 selected?.let{node->RelationshipNodeDialog(workspace,data,node){selected=null}}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Relationship Graph")},text={Column{
  Text("${data.nodes.size} numbers • ${data.edges.size} communication links",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(query,{query=it},label={Text("Search number")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
  Text("Numbers are ranked by observed CDR interactions. Tap a number to inspect its links.",style=MaterialTheme.typography.labelSmall)
  LazyColumn(Modifier.heightIn(max=500.dp)){items(filtered.take(1000)){n->val links=RelationshipGraph.neighbors(data,n.id);ListItem(headlineContent={Text(n.id)},supportingContent={Text("${n.interactions} records • ${links.size} linked number(s) • ${n.datasets} CDR(s) • ${n.imeis} IMEI • ${n.towers} tower(s)")},modifier=Modifier.clickable{selected=n});HorizontalDivider()}}
 }},confirmButton={TextButton(onDismiss){Text("Close")}})
}

@Composable
private fun RelationshipNodeDialog(workspace:CaseWorkspace,data:RelationshipGraphData,node:GraphNode,onDismiss:()->Unit){
 val links=remember(data,node){RelationshipGraph.neighbors(data,node.id)}
 var selectedEdge by remember{mutableStateOf<GraphEdge?>(null)}
 selectedEdge?.let{edge->RelationshipEdgeDialog(workspace,node.id,edge){selectedEdge=null}}
 AlertDialog(onDismissRequest=onDismiss,title={Text(node.id)},text={Column{
  Text("${node.interactions} records • ${links.size} direct links",style=MaterialTheme.typography.bodySmall)
  Text("Connected numbers",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=8.dp))
  if(links.isEmpty())Text("No direct communication links found.") else LazyColumn(Modifier.heightIn(max=480.dp)){items(links){e->val other=if(e.source==node.id)e.target else e.source;ListItem(headlineContent={Text(other)},supportingContent={Text("${e.interactions} interaction(s) • ${e.datasets.size} CDR dataset(s)\n${e.datasets.joinToString()}")},modifier=Modifier.clickable{selectedEdge=e});HorizontalDivider()}}
 }},confirmButton={TextButton(onDismiss){Text("Back")}})
}

@Composable
private fun RelationshipEdgeDialog(workspace:CaseWorkspace,focus:String,edge:GraphEdge,onDismiss:()->Unit){
 val other=if(edge.source==focus)edge.target else edge.source
 val records=remember(workspace,focus,other){workspace.datasets.flatMap{d->d.records.filter{r->(r.number==focus&&r.otherParty==other)||(r.number==other&&r.otherParty==focus)}.map{LinkedRecord(d.name,it)}}.sortedBy{it.record.dateTime}}
 AlertDialog(onDismissRequest=onDismiss,title={Text("$focus ↔ $other")},text={Column{
  Text("${records.size} matching communication record(s)",style=MaterialTheme.typography.bodySmall)
  if(records.isEmpty())Text("No underlying records found.") else LazyColumn(Modifier.heightIn(max=500.dp)){items(records.take(1500)){item->val r=item.record;val tower=if(r.cellId.isNotBlank())listOf(r.lac,r.cellId).filter{it.isNotBlank()}.joinToString("/") else "";ListItem(headlineContent={Text(r.dateTime.ifBlank{"Time unavailable"})},supportingContent={Text(listOf(item.datasetName,r.direction,r.duration.takeIf{it.isNotBlank()}?.let{"${it}s"}.orEmpty(),r.imei.takeIf{it.isNotBlank()}?.let{"IMEI $it"}.orEmpty(),tower.takeIf{it.isNotBlank()}?.let{"Tower $it"}.orEmpty()).filter{it.isNotBlank()}.joinToString(" • "))});HorizontalDivider()}}
 }},confirmButton={TextButton(onDismiss){Text("Back")}})
}
