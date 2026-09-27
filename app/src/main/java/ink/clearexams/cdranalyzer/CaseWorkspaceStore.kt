package ink.clearexams.cdranalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CaseWorkspace(val id:String=UUID.randomUUID().toString(),val title:String,val crimeNumber:String="",val notes:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val datasets:List<CdrDataset> = emptyList())
data class CdrDataset(val id:String=UUID.randomUUID().toString(),val name:String,val importedAt:Long=System.currentTimeMillis(),val records:List<CdrRecord> = emptyList())
data class CommonContact(val number:String,val datasetCount:Int,val totalInteractions:Int,val datasetNames:List<String>)
data class LinkProfile(val number:String,val totalInteractions:Int,val datasetNames:List<String>,val imeis:List<String>,val imsis:List<String>,val towers:List<String>)
data class LinkedRecord(val datasetName:String,val record:CdrRecord)
data class TowerEvent(val datasetName:String,val dateTime:String,val direction:String)
data class SharedTower(val tower:String,val firstCount:Int,val secondCount:Int,val firstEvents:List<TowerEvent>,val secondEvents:List<TowerEvent>)

class CaseWorkspaceStore(private val context:Context){
 private val root:File by lazy{File(context.filesDir,"cdr_cases").apply{mkdirs()}}
 fun list():List<CaseWorkspace> = root.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{decode(it.readText())}.getOrNull()}?.sortedByDescending{it.updatedAt}?: emptyList()
 fun load(id:String):CaseWorkspace?{val f=File(root,"$id.json");return if(f.exists())runCatching{decode(f.readText())}.getOrNull() else null}
 fun save(w:CaseWorkspace):CaseWorkspace{val s=w.copy(updatedAt=System.currentTimeMillis());File(root,"${s.id}.json").writeText(encode(s).toString());return s}
 fun create(title:String,crimeNumber:String="")=save(CaseWorkspace(title=title.trim().ifBlank{"Untitled case"},crimeNumber=crimeNumber.trim()))
 fun addDataset(w:CaseWorkspace,name:String,records:List<CdrRecord>):CaseWorkspace{val d=CdrDataset(name=name.ifBlank{"CDR ${w.datasets.size+1}"},records=records);return save(w.copy(datasets=w.datasets+d))}
 fun updateNotes(w:CaseWorkspace,notes:String)=save(w.copy(notes=notes))
 fun delete(id:String)=File(root,"$id.json").delete()
 fun commonContacts(w:CaseWorkspace,minimumDatasets:Int=2):List<CommonContact>{if(w.datasets.size<minimumDatasets)return emptyList();val per=w.datasets.associate{d->d.name to d.records.map{it.otherParty.ifBlank{it.number}}.filter{it.isNotBlank()}.groupingBy{it}.eachCount()};return per.values.flatMap{it.keys}.toSet().mapNotNull{n->val hits=per.filterValues{it.containsKey(n)};if(hits.size<minimumDatasets)null else CommonContact(n,hits.size,hits.values.sumOf{it[n]?:0},hits.keys.toList())}.sortedWith(compareByDescending<CommonContact>{it.datasetCount}.thenByDescending{it.totalInteractions})}
 fun linkProfiles(w:CaseWorkspace):List<LinkProfile>{val all=w.datasets.flatMap{d->d.records.map{d.name to it}};val numbers=all.map{it.second.otherParty.ifBlank{it.second.number}}.filter{it.isNotBlank()}.distinct();return numbers.map{n->val hits=all.filter{(_,r)->r.otherParty==n||r.number==n};LinkProfile(n,hits.size,hits.map{it.first}.distinct(),hits.map{it.second.imei}.filter{it.isNotBlank()}.distinct(),hits.map{it.second.imsi}.filter{it.isNotBlank()}.distinct(),hits.map{it.second}.filter{it.cellId.isNotBlank()}.map{"${it.lac}/${it.cellId}"}.distinct())}.sortedByDescending{it.totalInteractions}}
 fun linkedRecords(w:CaseWorkspace,number:String):List<LinkedRecord>{if(number.isBlank())return emptyList();return w.datasets.flatMap{d->d.records.filter{r->r.otherParty==number||r.number==number}.map{LinkedRecord(d.name,it)}}.sortedBy{it.record.dateTime}}
 fun sharedTowers(w:CaseWorkspace,first:String,second:String):List<SharedTower>{
  fun events(number:String)=w.datasets.flatMap{d->d.records.filter{r->(r.otherParty==number||r.number==number)&&r.cellId.isNotBlank()}.map{r->("${r.lac}/${r.cellId}") to TowerEvent(d.name,r.dateTime,r.direction)}}.groupBy({it.first},{it.second})
  val a=events(first);val b=events(second)
  return a.keys.intersect(b.keys).map{tower->SharedTower(tower,a[tower]?.size?:0,b[tower]?.size?:0,a[tower].orEmpty().sortedBy{it.dateTime},b[tower].orEmpty().sortedBy{it.dateTime})}.sortedByDescending{it.firstCount+it.secondCount}
 }
 private fun encode(w:CaseWorkspace)=JSONObject().apply{put("id",w.id);put("title",w.title);put("crimeNumber",w.crimeNumber);put("notes",w.notes);put("createdAt",w.createdAt);put("updatedAt",w.updatedAt);put("datasets",JSONArray().apply{w.datasets.forEach{put(encodeDataset(it))}})}
 private fun encodeDataset(d:CdrDataset)=JSONObject().apply{put("id",d.id);put("name",d.name);put("importedAt",d.importedAt);put("records",JSONArray().apply{d.records.forEach{r->put(JSONObject().apply{put("number",r.number);put("otherParty",r.otherParty);put("direction",r.direction);put("dateTime",r.dateTime);put("duration",r.duration);put("imei",r.imei);put("imsi",r.imsi);put("cellId",r.cellId);put("lac",r.lac);put("latitude",r.latitude);put("longitude",r.longitude)})}})}
 private fun decode(text:String):CaseWorkspace{val o=JSONObject(text);val ds=o.optJSONArray("datasets")?:JSONArray();return CaseWorkspace(o.getString("id"),o.optString("title","Untitled case"),o.optString("crimeNumber"),o.optString("notes"),o.optLong("createdAt"),o.optLong("updatedAt"),(0 until ds.length()).map{decodeDataset(ds.getJSONObject(it))})}
 private fun decodeDataset(o:JSONObject):CdrDataset{val rs=o.optJSONArray("records")?:JSONArray();return CdrDataset(o.getString("id"),o.optString("name","CDR"),o.optLong("importedAt"),(0 until rs.length()).map{i->val r=rs.getJSONObject(i);CdrRecord(r.optString("number"),r.optString("otherParty"),r.optString("direction"),r.optString("dateTime"),r.optString("duration"),r.optString("imei"),r.optString("imsi"),r.optString("cellId"),r.optString("lac"),r.optString("latitude"),r.optString("longitude"))})}
}
