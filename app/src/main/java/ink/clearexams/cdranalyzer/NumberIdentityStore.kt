package ink.clearexams.cdranalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NumberIdentity(
    val number:String,
    val name:String="",
    val role:String="Other",
    val customRole:String="",
    val notes:String=""
){
    val effectiveRole:String get()=if(role=="Other"&&customRole.isNotBlank())customRole else role
    val displayName:String get()=name.ifBlank{number}
    val displayLabel:String get()=buildString{
        append(displayName)
        if(name.isNotBlank())append(" ($number)")
        if(effectiveRole.isNotBlank())append(" • $effectiveRole")
    }
}

class NumberIdentityStore(context:Context){
    private val prefs=context.getSharedPreferences("cdr_number_identities",Context.MODE_PRIVATE)
    private fun key(caseId:String)="case_$caseId"

    fun list(caseId:String):List<NumberIdentity>{
        val arr=runCatching{JSONArray(prefs.getString(key(caseId),"[]"))}.getOrElse{JSONArray()}
        return (0 until arr.length()).mapNotNull{i->runCatching{decode(arr.getJSONObject(i))}.getOrNull()}.sortedBy{it.number}
    }

    fun listGlobal():List<NumberIdentity>{
        val arr=runCatching{JSONArray(prefs.getString("global_directory","[]"))}.getOrElse{JSONArray()}
        return (0 until arr.length()).mapNotNull{i->runCatching{decode(arr.getJSONObject(i))}.getOrNull()}.sortedBy{it.number}
    }

    fun map(caseId:String):Map<String,NumberIdentity> = (listGlobal()+list(caseId)).associateBy{normalize(it.number)}

    fun find(caseId:String,number:String):NumberIdentity?=map(caseId)[normalize(number)]

    fun save(caseId:String,identity:NumberIdentity){
        val normalized=normalize(identity.number)
        require(normalized.isNotBlank()){ "Phone number is required" }
        val all=list(caseId).filterNot{normalize(it.number)==normalized}.toMutableList()
        all+=identity.copy(number=identity.number.trim())
        persist(caseId,all)
    }

    fun delete(caseId:String,number:String){persist(caseId,list(caseId).filterNot{normalize(it.number)==normalize(number)})}

    fun saveGlobal(identity:NumberIdentity){
        val normalized=normalize(identity.number);require(normalized.isNotBlank()){"Phone number is required"}
        val all=listGlobal().filterNot{normalize(it.number)==normalized}.toMutableList();all+=identity.copy(number=identity.number.trim());persistGlobal(all)
    }
    fun deleteGlobal(number:String){persistGlobal(listGlobal().filterNot{normalize(it.number)==normalize(number)})}
    fun exportGlobalJson():String{val arr=JSONArray();listGlobal().forEach{n->arr.put(JSONObject().apply{put("number",n.number);put("name",n.name);put("role",n.role);put("customRole",n.customRole);put("notes",n.notes)})};return arr.toString(2)}
    fun importGlobalJson(text:String):Int{val arr=JSONArray(text);val items=(0 until arr.length()).mapNotNull{i->runCatching{decode(arr.getJSONObject(i))}.getOrNull()}.filter{normalize(it.number).isNotBlank()};persistGlobal(items);return items.size}

    fun label(caseId:String,number:String):String=find(caseId,number)?.displayLabel?:number

    private fun persist(caseId:String,items:List<NumberIdentity>){
        val arr=JSONArray();items.forEach{n->arr.put(JSONObject().apply{
            put("number",n.number);put("name",n.name);put("role",n.role);put("customRole",n.customRole);put("notes",n.notes)
        })};prefs.edit().putString(key(caseId),arr.toString()).apply()
    }

    private fun persistGlobal(items:List<NumberIdentity>){
        val arr=JSONArray();items.forEach{n->arr.put(JSONObject().apply{put("number",n.number);put("name",n.name);put("role",n.role);put("customRole",n.customRole);put("notes",n.notes)})};prefs.edit().putString("global_directory",arr.toString()).apply()
    }

    private fun decode(o:JSONObject)=NumberIdentity(o.optString("number"),o.optString("name"),o.optString("role","Other"),o.optString("customRole"),o.optString("notes"))

    companion object{
        val roles=listOf("Suspect","Victim","Witness","Family","Associate","Informant","Unknown","Other")
        fun normalize(value:String)=value.filter{it.isDigit()}.let{if(it.length>10)it.takeLast(10)else it}
    }
}
