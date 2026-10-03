package ink.clearexams.cdranalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class CaseWorkspace(val id:String=UUID.randomUUID().toString(),val title:String,val crimeNumber:String="",val notes:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val datasets:List<CdrDataset> = emptyList(),val incidentDateTime:String="")
data class CdrDataset(val id:String=UUID.randomUUID().toString(),val name:String,val importedAt:Long=System.currentTimeMillis(),val records:List<CdrRecord> = emptyList())
data class CommonContact(val number:String,val datasetCount:Int,val totalInteractions:Int,val datasetNames:List<String>)
data class LinkProfile(val number:String,val totalInteractions:Int,val datasetNames:List<String>,val imeis:List<String>,val imsis:List<String>,val towers:List<String>)
data class LinkedRecord(val datasetName:String,val record:CdrRecord)
data class TowerEvent(val datasetName:String,val dateTime:String,val direction:String)
data class SharedTower(val tower:String,val firstCount:Int,val secondCount:Int,val firstEvents:List<TowerEvent>,val secondEvents:List<TowerEvent>)
data class CoLocationMatch(val tower:String,val firstEvent:TowerEvent,val secondEvent:TowerEvent,val differenceMinutes:Long)

class CaseWorkspaceStore(private val context:Context){
 private val root:File by lazy{File(context.filesDir,"cdr_cases").apply{mkdirs()}}
 fun list():List<CaseWorkspace> = root.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{decode(it.readText())}.getOrNull()}?.sortedByDescending{it.updatedAt}?: emptyList()
 fun load(id:String):CaseWorkspace?{val f=File(root,"$id.json");return if(f.exists())runCatching{decode(f.readText())}.getOrNull() else null}
 fun save(w:CaseWorkspace):CaseWorkspace{val s=w.copy(updatedAt=System.currentTimeMillis());File(root,"${s.id}.json").writeText(encode(s).toString());return s}
 fun create(title:String,crimeNumber:String="")=save(CaseWorkspace(title=title.trim().ifBlank{"Untitled case"},crimeNumber=crimeNumber.trim()))
 fun addDataset(w:CaseWorkspace,name:String,records:List<CdrRecord>):CaseWorkspace{val d=CdrDataset(name=name.ifBlank{"CDR ${w.datasets.size+1}"},records=records);return save(w.copy(datasets=w.datasets+d))}
 fun updateNotes(w:CaseWorkspace,notes:String)=save(w.copy(notes=notes))
 fun updateIncidentDateTime(w:CaseWorkspace,value:String)=save(w.copy(incidentDateTime=value.trim()))
 fun delete(id:String)=File(root,"$id.json").delete()
 fun commonContacts(w:CaseWorkspace,minimumDatasets:Int=2):List<CommonContact>{if(w.datasets.size<minimumDatasets)return emptyList();val per=w.datasets.associate{d->d.name to d.records.map{it.otherParty.ifBlank{it.number}}.filter{it.isNotBlank()}.groupingBy{it}.eachCount()};return per.values.flatMap{it.keys}.toSet().mapNotNull{n->val hits=per.filterValues{it.containsKey(n)};if(hits.size<minimumDatasets)null else CommonContact(n,hits.size,hits.values.sumOf{it[n]?:0},hits.keys.toList())}.sortedWith(compareByDescending<CommonContact>{it.datasetCount}.thenByDescending{it.totalInteractions})}
 fun linkProfiles(w:CaseWorkspace):List<LinkProfile>{val all=w.datasets.flatMap{d->d.records.map{d.name to it}};val numbers=all.map{it.second.otherParty.ifBlank{it.second.number}}.filter{it.isNotBlank()}.distinct();return numbers.map{n->val hits=all.filter{(_,r)->r.otherParty==n||r.number==n};LinkProfile(n,hits.size,hits.map{it.first}.distinct(),hits.map{it.second.imei}.filter{it.isNotBlank()}.distinct(),hits.map{it.second.imsi}.filter{it.isNotBlank()}.distinct(),hits.map{it.second}.filter{it.cellId.isNotBlank()}.map{"${it.lac}/${it.cellId}"}.distinct())}.sortedByDescending{it.totalInteractions}}
 fun linkedRecords(w:CaseWorkspace,number:String):List<LinkedRecord>{if(number.isBlank())return emptyList();return w.datasets.flatMap{d->d.records.filter{r->r.otherParty==number||r.number==number}.map{LinkedRecord(d.name,it)}}.sortedBy{it.record.dateTime}}
 fun sharedTowers(w:CaseWorkspace,first:String,second:String):List<SharedTower>{
  fun events(number:String):Map<String,List<TowerEvent>>{
   val pairs=mutableListOf<Pair<String,TowerEvent>>()
   for(dataset in w.datasets){
    for(record in dataset.records){
     val matches=record.otherParty==number||record.number==number
     if(matches&&record.cellId.isNotBlank()){
      val tower="${record.lac}/${record.cellId}"
      pairs.add(tower to TowerEvent(dataset.name,record.dateTime,record.direction))
     }
    }
   }
   return pairs.groupBy({it.first},{it.second})
  }
  val a=events(first)
  val b=events(second)
  return a.keys.intersect(b.keys).map{tower->
   SharedTower(tower,a[tower]?.size?:0,b[tower]?.size?:0,a[tower].orEmpty().sortedBy{it.dateTime},b[tower].orEmpty().sortedBy{it.dateTime})
  }.sortedByDescending{it.firstCount+it.secondCount}
 }
 fun coLocationMatches(w:CaseWorkspace,first:String,second:String,windowMinutes:Int):List<CoLocationMatch>{
  val out=mutableListOf<CoLocationMatch>()
  for(shared in sharedTowers(w,first,second)){
   for(a in shared.firstEvents){
    val at=parseTime(a.dateTime)?:continue
    for(b in shared.secondEvents){
     val bt=parseTime(b.dateTime)?:continue
     val diff=abs(at-bt)/60000L
     if(diff<=windowMinutes)out.add(CoLocationMatch(shared.tower,a,b,diff))
    }
   }
  }
  return out.distinctBy{listOf(it.tower,it.firstEvent.datasetName,it.firstEvent.dateTime,it.secondEvent.datasetName,it.secondEvent.dateTime)}.sortedWith(compareBy<CoLocationMatch>{it.differenceMinutes}.thenBy{it.firstEvent.dateTime})
 }
 private fun parseTime(value:String):Long?{if(value.isBlank())return null;val patterns=listOf("dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss","dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm","MM/dd/yyyy HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss");for(p in patterns){try{val f=SimpleDateFormat(p,Locale.US);f.isLenient=false;return f.parse(value)?.time}catch(_:Exception){}};return value.toLongOrNull()?.let{if(it<100000000000L)it*1000 else it}}
 private fun encode(w:CaseWorkspace)=JSONObject().apply{put("id",w.id);put("title",w.title);put("crimeNumber",w.crimeNumber);put("notes",w.notes);put("incidentDateTime",w.incidentDateTime);put("createdAt",w.createdAt);put("updatedAt",w.updatedAt);put("datasets",JSONArray().apply{w.datasets.forEach{put(encodeDataset(it))}})}
 private fun encodeDataset(d:CdrDataset)=JSONObject().apply{put("id",d.id);put("name",d.name);put("importedAt",d.importedAt);put("records",JSONArray().apply{d.records.forEach{r->put(JSONObject().apply{put("number",r.number);put("otherParty",r.otherParty);put("direction",r.direction);put("dateTime",r.dateTime);put("duration",r.duration);put("imei",r.imei);put("imsi",r.imsi);put("cellId",r.cellId);put("lac",r.lac);put("latitude",r.latitude);put("longitude",r.longitude)})}})}
 private fun decode(text:String):CaseWorkspace{val o=JSONObject(text);val ds=o.optJSONArray("datasets")?:JSONArray();return CaseWorkspace(id=o.getString("id"),title=o.optString("title","Untitled case"),crimeNumber=o.optString("crimeNumber"),notes=o.optString("notes"),createdAt=o.optLong("createdAt"),updatedAt=o.optLong("updatedAt"),datasets=(0 until ds.length()).map{decodeDataset(ds.getJSONObject(it))},incidentDateTime=o.optString("incidentDateTime"))}
 private fun decodeDataset(o:JSONObject):CdrDataset{val rs=o.optJSONArray("records")?:JSONArray();return CdrDataset(o.getString("id"),o.optString("name","CDR"),o.optLong("importedAt"),(0 until rs.length()).map{i->val r=rs.getJSONObject(i);CdrRecord(r.optString("number"),r.optString("otherParty"),r.optString("direction"),r.optString("dateTime"),r.optString("duration"),r.optString("imei"),r.optString("imsi"),r.optString("cellId"),r.optString("lac"),r.optString("latitude"),r.optString("longitude"))})}
}
