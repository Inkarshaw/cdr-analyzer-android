package ink.clearexams.cdranalyzer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TacEntry(val tac:String,val manufacturer:String,val model:String,val deviceType:String="",val os:String="")

class TacDatabaseStore(private val context:Context){
    private val file=File(context.filesDir,"tac_cache.json")
    fun all():Map<String,TacEntry>{
        if(!file.exists())return emptyMap()
        return runCatching{
            val arr=JSONArray(file.readText());val out=linkedMapOf<String,TacEntry>()
            for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val e=TacEntry(o.optString("tac"),o.optString("manufacturer"),o.optString("model"),o.optString("deviceType"),o.optString("os"));if(e.tac.length>=8)out[e.tac.take(8)]=e}
            out
        }.getOrDefault(emptyMap())
    }
    fun save(entries:Collection<TacEntry>){
        val merged=all().toMutableMap();entries.forEach{if(it.tac.length>=8)merged[it.tac.take(8)]=it.copy(tac=it.tac.take(8))}
        val arr=JSONArray();merged.values.sortedBy{it.tac}.forEach{e->arr.put(JSONObject().apply{put("tac",e.tac);put("manufacturer",e.manufacturer);put("model",e.model);put("deviceType",e.deviceType);put("os",e.os)})};file.writeText(arr.toString())
    }
    fun clear(){if(file.exists())file.delete()}
}

object TacDatabaseImporter{
    fun parse(resolver:ContentResolver,uri:Uri):List<TacEntry>{
        val type=resolver.getType(uri).orEmpty().lowercase();val name=uri.lastPathSegment.orEmpty().lowercase()
        return when{type.contains("json")||name.endsWith(".json")->parseJson(resolver,uri);type.contains("csv")||name.endsWith(".csv")->parseCsv(resolver,uri);else->parseWorkbook(resolver,uri)}
    }
    private fun parseJson(resolver:ContentResolver,uri:Uri):List<TacEntry>{
        val text=resolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:return emptyList()
        val arr=runCatching{JSONArray(text)}.getOrElse{val root=JSONObject(text);root.optJSONArray("entries")?:JSONArray()}
        return (0 until arr.length()).mapNotNull{i->val o=arr.optJSONObject(i)?:return@mapNotNull null;entry(o.optString("tac",o.optString("TAC")),o.optString("manufacturer",o.optString("brand")),o.optString("model"),o.optString("deviceType",o.optString("type")),o.optString("os"))}
    }
    private fun parseCsv(resolver:ContentResolver,uri:Uri):List<TacEntry>{
        val lines=resolver.openInputStream(uri)?.use{BufferedReader(InputStreamReader(it)).readLines()}?:return emptyList();if(lines.isEmpty())return emptyList()
        val header=csv(lines.first()).map{it.lowercase().replace(" ","").replace("_","")};fun idx(vararg n:String)=header.indexOfFirst{h->n.any{h.contains(it)}}
        val ti=idx("tac");val mi=idx("manufacturer","brand","maker");val model=idx("model");val di=idx("devicetype","type");val oi=idx("os","operatingsystem")
        if(ti<0)return emptyList();return lines.drop(1).mapNotNull{line->val v=csv(line);entry(v.getOrElse(ti){""},v.getOrElse(mi){""},v.getOrElse(model){""},v.getOrElse(di){""},v.getOrElse(oi){""})}
    }
    private fun parseWorkbook(resolver:ContentResolver,uri:Uri):List<TacEntry>{
        val out=mutableListOf<TacEntry>()
        resolver.openInputStream(uri)?.use{stream->
            WorkbookFactory.create(stream).use{wb->
                val fmt=DataFormatter()
                for(si in 0 until wb.numberOfSheets){
                    val sh=wb.getSheetAt(si)
                    if(sh.lastRowNum<1) continue
                    val h=sh.getRow(0)?:continue
                    val header=(0 until h.lastCellNum.coerceAtLeast(0)).map{
                        fmt.formatCellValue(h.getCell(it)).lowercase().replace(" ","").replace("_","")
                    }
                    fun indexOf(vararg names:String)=header.indexOfFirst{value->names.any{value.contains(it)}}
                    val ti=indexOf("tac")
                    if(ti<0) continue
                    val mi=indexOf("manufacturer","brand","maker")
                    val modelIndex=indexOf("model")
                    val di=indexOf("devicetype","type")
                    val oi=indexOf("os","operatingsystem")
                    for(ri in 1..sh.lastRowNum){
                        val row=sh.getRow(ri)?:continue
                        fun value(index:Int)=if(index<0)"" else fmt.formatCellValue(row.getCell(index)).trim()
                        entry(value(ti),value(mi),value(modelIndex),value(di),value(oi))?.let(out::add)
                    }
                }
            }
        }
        return out
    }
    suspend fun fetchRemoteMatches(rows:List<CdrRecord>,existing:Map<String,TacEntry>):List<TacEntry> = withContext(Dispatchers.IO){
        val wanted=rows.map{it.imei.filter(Char::isDigit).take(8)}.filter{it.length==8&&!existing.containsKey(it)}.toMutableSet()
        if(wanted.isEmpty())return@withContext emptyList()
        val out=mutableListOf<TacEntry>()
        URL("https://raw.githubusercontent.com/MoazEb/tac-database/main/tac_full.csv").openStream().bufferedReader().use{reader->
            val first=reader.readLine()?:return@use
            val header=csv(first).map{it.trim().lowercase().replace(" ","").replace("_","")}
            val ti=header.indexOf("tac");val bi=header.indexOf("brand");val si=header.indexOf("specs")
            if(ti<0||bi<0||si<0)throw IllegalArgumentException("Unsupported remote TAC database schema")
            var line=reader.readLine()
            while(line!=null&&wanted.isNotEmpty()){
                if(line.isNotBlank()){
                    val v=csv(line);val tac=v.getOrElse(ti){""}.filter(Char::isDigit).take(8)
                    if(tac in wanted){
                        entry(tac,v.getOrElse(bi){""},v.getOrElse(si){""},"","")?.let(out::add)
                        wanted.remove(tac)
                    }
                }
                line=reader.readLine()
            }
        }
        out
    }
    private fun entry(t:String,m:String,model:String,d:String,o:String):TacEntry?{val tac=t.filter{it.isDigit()}.take(8);return if(tac.length==8)TacEntry(tac,m.trim(),model.trim(),d.trim(),o.trim()) else null}
    private fun csv(line:String):List<String>{val out=mutableListOf<String>();val b=StringBuilder();var q=false;var i=0;while(i<line.length){val c=line[i];when{c=='"'&&q&&i+1<line.length&&line[i+1]=='"'->{b.append('"');i++};c=='"'->q=!q;c==','&&!q->{out+=b.toString();b.clear()};else->b.append(c)};i++};out+=b.toString();return out}
}

data class ImeiStructure(val imei:String,val tac:String,val serial:String,val check:String,val validCheckDigit:Boolean?,val model:String?,val manufacturer:String?)
data class IdentifierRelationship(val msisdn:String,val imsis:List<String>,val imeis:List<String>,val records:Int)
data class IdentifierChangeEvent(val at:String,val msisdn:String,val oldImsi:String,val newImsi:String,val oldImei:String,val newImei:String)
data class CrossImsiLink(val imsi:String,val subjects:List<String>,val records:Int)
data class MultiImsiDevice(val imei:String,val imsis:List<String>,val records:Int)
data class DeviceIntelligenceResult(val imeis:List<ImeiStructure>,val relationships:List<IdentifierRelationship>,val changes:List<IdentifierChangeEvent>,val crossImsi:List<CrossImsiLink>,val multiImsi:List<MultiImsiDevice>)

object DeviceIntelligence{
    fun build(rows:List<CdrRecord>,tac:Map<String,TacEntry>):DeviceIntelligenceResult{
        val imeis=rows.map{it.imei.filter(Char::isDigit)}.filter{it.length>=8}.distinct().map{i->val prefix=i.take(8);val e=tac[prefix];ImeiStructure(i,prefix,if(i.length>=14)i.substring(8,14) else i.drop(8),if(i.length>=15)i.substring(14,15) else "",if(i.length==15)luhnValid(i) else null,e?.model,e?.manufacturer)}.sortedBy{it.imei}
        val relationships=rows.groupBy{it.number.ifBlank{"Unknown subject"}}.map{(n,x)->IdentifierRelationship(n,x.map{it.imsi}.filter{it.isNotBlank()}.distinct(),x.map{it.imei}.filter{it.isNotBlank()}.distinct(),x.size)}.sortedByDescending{it.records}
        val changes=mutableListOf<IdentifierChangeEvent>();rows.groupBy{it.number}.forEach{(n,x)->var prev:CdrRecord?=null;x.sortedBy{parseCdrTime(it.dateTime)?:Long.MAX_VALUE}.forEach{r->val p=prev;if(p!=null){val imsiChanged=p!!.imsi.isNotBlank()&&r.imsi.isNotBlank()&&p!!.imsi!=r.imsi;val imeiChanged=p!!.imei.isNotBlank()&&r.imei.isNotBlank()&&p!!.imei!=r.imei;if(imsiChanged||imeiChanged)changes+=IdentifierChangeEvent(r.dateTime,n,p!!.imsi,r.imsi,p!!.imei,r.imei)};prev=r}}
        val cross=rows.filter{it.imsi.isNotBlank()}.groupBy{it.imsi}.mapNotNull{(imsi,x)->val subjects=x.map{it.number}.filter{it.isNotBlank()}.distinct();if(subjects.size<2)null else CrossImsiLink(imsi,subjects,x.size)}.sortedByDescending{it.records}
        val multi=rows.filter{it.imei.isNotBlank()}.groupBy{it.imei}.mapNotNull{(imei,x)->val imsis=x.map{it.imsi}.filter{it.isNotBlank()}.distinct();if(imsis.size<2)null else MultiImsiDevice(imei,imsis,x.size)}.sortedByDescending{it.records}
        return DeviceIntelligenceResult(imeis,relationships,changes.sortedBy{parseCdrTime(it.at)?:Long.MAX_VALUE},cross,multi)
    }
    private fun luhnValid(value:String):Boolean{if(value.length!=15||value.any{!it.isDigit()})return false;var sum=0;for(i in 0 until 14){var d=value[i]-'0';if(i%2==1){d*=2;if(d>9)d=d/10+d%10};sum+=d};val check=(10-(sum%10))%10;return check==value[14]-'0'}
}
