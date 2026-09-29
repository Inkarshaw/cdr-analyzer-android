package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CaseReviewDialog(workspace:CaseWorkspace,onDismiss:()->Unit){
    val context=LocalContext.current
    val store=remember(workspace.id){CaseReviewStore(context,workspace.id)}
    var state by remember(workspace.id){mutableStateOf(store.load())}
    var generalNote by remember(workspace.id){mutableStateOf(state.generalNote)}
    var manualDt by remember{mutableStateOf("")};var manualText by remember{mutableStateOf("")}
    var recordQuery by remember{mutableStateOf("")};var selectedFlag by remember{mutableStateOf<FlaggedCaseRecord?>(null)}
    selectedFlag?.let{flag->FlagNoteDialog(flag,{selectedFlag=null}){note->state=store.updateFlagNote(flag.key,note);selectedFlag=null}}
    val allRecords=remember(workspace){workspace.datasets.flatMap{d->d.records.map{d.name to it}}}
    val matching=allRecords.filter{(dataset,r)->recordQuery.isBlank()||listOf(dataset,r.number,r.otherParty,r.dateTime,r.direction,r.imei,r.imsi,r.cellId,r.lac).any{it.contains(recordQuery,true)}}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Case Review")},
        text={
            LazyColumn(Modifier.heightIn(max=650.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                item{Text("Curated chronology, flagged records, notes and local audit history.",style=MaterialTheme.typography.bodySmall)}
                item{Text("General case note",style=MaterialTheme.typography.titleSmall)}
                item{OutlinedTextField(generalNote,{generalNote=it},modifier=Modifier.fillMaxWidth().heightIn(min=100.dp),label={Text("Investigation observations / follow-up")})}
                item{Button({state=store.saveGeneralNote(generalNote)},Modifier.fillMaxWidth()){Text("Save general note")}}
                item{HorizontalDivider();Text("Manual chronology",style=MaterialTheme.typography.titleSmall)}
                item{OutlinedTextField(manualDt,{manualDt=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Date/time")})}
                item{OutlinedTextField(manualText,{manualText=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Manual event")})}
                item{Button(onClick={if(manualText.isNotBlank()){state=store.addManual(manualDt,manualText);manualDt="";manualText=""}},enabled=manualText.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Add chronology event")}}
                if(state.manualEvents.isEmpty())item{Text("No manual chronology events yet.",style=MaterialTheme.typography.bodySmall)}else items(state.manualEvents){e->
                    ListItem(headlineContent={Text(e.dateTime.ifBlank{"Time not set"})},supportingContent={Text(e.text)},trailingContent={TextButton({state=store.removeManual(e.id)}){Text("Remove")}});HorizontalDivider()
                }
                item{HorizontalDivider();Text("Flagged records & notes",style=MaterialTheme.typography.titleSmall)}
                if(state.flags.isEmpty())item{Text("No flagged records yet. Search the case records below and tap one to flag it.",style=MaterialTheme.typography.bodySmall)}else items(state.flags){f->
                    ListItem(headlineContent={Text("${f.dataset} • ${f.dateTime.ifBlank{"Time unavailable"}}")},supportingContent={Text("${f.number} ↔ ${f.otherParty} • ${f.direction}${if(f.note.isBlank())"" else "\nNote: ${f.note}"}")},modifier=Modifier.clickable{selectedFlag=f},trailingContent={TextButton({val pair=allRecords.firstOrNull{(d,r)->d==f.dataset&&listOf(d,r.dateTime,r.number,r.otherParty,r.direction,r.imei,r.imsi,r.lac,r.cellId).joinToString("|")==f.key};if(pair!=null)state=store.toggleFlag(pair.first,pair.second)}){Text("Unflag")}});HorizontalDivider()
                }
                item{OutlinedTextField(recordQuery,{recordQuery=it},label={Text("Find a record to flag")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                items(matching.take(100)){(dataset,r)->
                    val flagged=state.flags.any{it.key==listOf(dataset,r.dateTime,r.number,r.otherParty,r.direction,r.imei,r.imsi,r.lac,r.cellId).joinToString("|")}
                    ListItem(headlineContent={Text("${r.dateTime.ifBlank{"Time unavailable"}} • ${r.otherParty.ifBlank{r.number}}")},supportingContent={Text("$dataset • ${r.direction} • ${if(r.cellId.isBlank())"tower unavailable" else "${r.lac}/${r.cellId}"}")},trailingContent={Text(if(flagged)"Flagged" else "Flag")},modifier=Modifier.clickable{state=store.toggleFlag(dataset,r)});HorizontalDivider()
                }
                item{HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Local audit trail",style=MaterialTheme.typography.titleSmall);TextButton({state=store.clearAudit()}){Text("Clear")}}}
                if(state.audit.isEmpty())item{Text("No audit entries yet.",style=MaterialTheme.typography.bodySmall)}else items(state.audit.asReversed().take(200)){a->ListItem(headlineContent={Text(a.action)},supportingContent={Text(a.at)});HorizontalDivider()}
            }
        },
        confirmButton={TextButton(onDismiss){Text("Close")}}
    )
}

@Composable private fun FlagNoteDialog(flag:FlaggedCaseRecord,onDismiss:()->Unit,onSave:(String)->Unit){
    var note by remember(flag.key){mutableStateOf(flag.note)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Flag note")},text={Column{Text("${flag.dataset} • ${flag.dateTime}",style=MaterialTheme.typography.bodySmall);OutlinedTextField(note,{note=it},label={Text("Verification / follow-up note")},modifier=Modifier.fillMaxWidth().heightIn(min=100.dp))}},confirmButton={Button({onSave(note)}){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
