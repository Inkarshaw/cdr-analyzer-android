package ink.clearexams.cdranalyzer

data class GraphNode(val id:String,val interactions:Int,val datasets:Int,val imeis:Int,val towers:Int)
data class GraphEdge(val source:String,val target:String,val interactions:Int,val datasets:List<String>)
data class RelationshipGraphData(val nodes:List<GraphNode>,val edges:List<GraphEdge>)

object RelationshipGraph {
 fun build(workspace:CaseWorkspace):RelationshipGraphData {
  val subjects=workspace.datasets.mapNotNull { d -> d.records.map{it.number}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key }.toSet()
  val edgeMap=linkedMapOf<Pair<String,String>,MutableList<String>>()
  val nodeRecords=linkedMapOf<String,MutableList<CdrRecord>>()
  workspace.datasets.forEach { d ->
   d.records.forEach { r ->
    val a=r.number.trim(); val b=r.otherParty.trim()
    if(a.isNotBlank())nodeRecords.getOrPut(a){mutableListOf()}.add(r)
    if(b.isNotBlank())nodeRecords.getOrPut(b){mutableListOf()}.add(r)
    if(a.isNotBlank()&&b.isNotBlank()&&a!=b){
     val key=if(a<=b)a to b else b to a
     edgeMap.getOrPut(key){mutableListOf()}.add(d.name)
    }
   }
  }
  val nodes=nodeRecords.map { (id,rs) -> GraphNode(id,rs.size,workspace.datasets.count{d->d.records.any{it.number==id||it.otherParty==id}},rs.map{it.imei}.filter{it.isNotBlank()}.distinct().size,rs.filter{it.cellId.isNotBlank()}.map{"${it.lac}/${it.cellId}"}.distinct().size) }
   .sortedWith(compareByDescending<GraphNode>{if(it.id in subjects)Int.MAX_VALUE else it.interactions}.thenByDescending{it.interactions})
  val edges=edgeMap.map{(k,v)->GraphEdge(k.first,k.second,v.size,v.distinct())}.sortedByDescending{it.interactions}
  return RelationshipGraphData(nodes,edges)
 }
 fun neighbors(data:RelationshipGraphData,number:String):List<GraphEdge> = data.edges.filter{it.source==number||it.target==number}.sortedByDescending{it.interactions}
}
