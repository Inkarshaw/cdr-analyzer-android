package ink.clearexams.cdranalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ManualCaseEvent(val id:Long,val dateTime:String,val text:String)
data class FlaggedCaseRecord(val key:String,val dataset:String,val dateTime:String,val number:String,val otherParty:String,val direction:String,val imei:String,val imsi:String,val tower:String,val note:String="")
data class CaseAuditEntry(val id:Long,val at:String,val action:String)
data class CaseReviewState(val generalNote:String="",val manualEvents:List<ManualCaseEvent> = emptyList(),val flags:List<FlaggedCaseRecord> = emptyList(),val audit:List<CaseAuditEntry> = emptyList())

class CaseReviewStore(context:Context,private val caseId:String){
    private val root=File(context.filesDir,"cdr_case_review").apply{mkdirs()}
    private val file=File(root,"$caseId.json")
    fun load():CaseReviewState{if(!file.exists())return CaseReviewState();return runCatching{decode(JSONObject(file.readText()))}.getOrDefault(CaseReviewState())}
    fun saveGeneralNote(note:String):CaseReviewState{val s=load().copy(generalNote=note);return persist(withAudit(s,"General case note saved"))}
    fun addManual(dateTime:String,text:String):CaseReviewState{val e=ManualCaseEvent(System.currentTimeMillis(),dateTime.trim(),text.trim());val s=load().copy(manualEvents=(load().manualEvents+e).sortedBy{parseCdrTime(it.dateTime)?:it.id});return persist(withAudit(s,"Manual chronology event added"))}
    fun removeManual(id:Long):CaseReviewState{val s=load().copy(manualEvents=load().manualEvents.filterNot{it.id==id});return persist(withAudit(s,"Manual chronology event removed"))}
    fun toggleFlag(dataset:String,record:CdrRecord):CaseReviewState{val current=load();val key=recordKey(dataset,record);val exists=current.flags.any{it.key==key};val flags=if(exists)current.flags.filterNot{it.key==key}else current.flags+FlaggedCaseRecord(key,dataset,record.dateTime,record.number,record.otherParty,record.direction,record.imei,record.imsi,if(record.cellId.isBlank())"" else listOf(record.lac,record.cellId).filter{it.isNotBlank()}.joinToString("/"));val action=if(exists)"Record flag removed" else "Record flagged";return persist(withAudit(current.copy(flags=flags),action))}
    fun updateFlagNote(key:String,note:String):CaseReviewState{val current=load();val s=current.copy(flags=current.flags.map{if(it.key==key)it.copy(note=note) else it});return persist(withAudit(s,"Flag note updated"))}
    fun clearAudit():CaseReviewState{val current=load().copy(audit=emptyList());return persist(current)}
    fun isFlagged(dataset:String,record:CdrRecord):Boolean{val key=recordKey(dataset,record);return load().flags.any{it.key==key}}
    private fun withAudit(state:CaseReviewState,action:String):CaseReviewState{val now=System.currentTimeMillis();val at=SimpleDateFormat("dd-MM-yyyy HH:mm:ss",Locale.getDefault()).format(Date(now));return state.copy(audit=(state.audit+CaseAuditEntry(now,at,action)).takeLast(1000))}
    private fun persist(state:CaseReviewState):CaseReviewState{file.writeText(encode(state).toString());return state}
    private fun recordKey(dataset:String,r:CdrRecord)=listOf(dataset,r.dateTime,r.number,r.otherParty,r.direction,r.imei,r.imsi,r.lac,r.cellId).joinToString("|")
    private fun encode(s:CaseReviewState)=JSONObject().apply{
        put("generalNote",s.generalNote)
        put("manualEvents",JSONArray().apply{s.manualEvents.forEach{e->put(JSONObject().apply{put("id",e.id);put("dateTime",e.dateTime);put("text",e.text)})}})
        put("flags",JSONArray().apply{s.flags.forEach{f->put(JSONObject().apply{put("key",f.key);put("dataset",f.dataset);put("dateTime",f.dateTime);put("number",f.number);put("otherParty",f.otherParty);put("direction",f.direction);put("imei",f.imei);put("imsi",f.imsi);put("tower",f.tower);put("note",f.note)})}})
        put("audit",JSONArray().apply{s.audit.forEach{a->put(JSONObject().apply{put("id",a.id);put("at",a.at);put("action",a.action)})}})
    }
    private fun decode(o:JSONObject):CaseReviewState{
        val me=o.optJSONArray("manualEvents")?:JSONArray();val fl=o.optJSONArray("flags")?:JSONArray();val au=o.optJSONArray("audit")?:JSONArray()
        return CaseReviewState(
            o.optString("generalNote"),
            (0 until me.length()).map{i->val e=me.getJSONObject(i);ManualCaseEvent(e.optLong("id"),e.optString("dateTime"),e.optString("text"))},
            (0 until fl.length()).map{i->val f=fl.getJSONObject(i);FlaggedCaseRecord(f.optString("key"),f.optString("dataset"),f.optString("dateTime"),f.optString("number"),f.optString("otherParty"),f.optString("direction"),f.optString("imei"),f.optString("imsi"),f.optString("tower"),f.optString("note"))},
            (0 until au.length()).map{i->val a=au.getJSONObject(i);CaseAuditEntry(a.optLong("id"),a.optString("at"),a.optString("action"))}
        )
    }
}
