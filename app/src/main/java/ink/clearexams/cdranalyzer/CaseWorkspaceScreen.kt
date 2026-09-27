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
fun CaseWorkspaceScreen(store:CaseWorkspaceStore,currentRows:List<CdrRecord>,currentFileName:String,onLoadDataset:(List<CdrRecord>,String)->Unit){
 var cases by remember{mutableStateOf(store.list())};var selected by remember{mutableStateOf<CaseWorkspace?>(null)};var showNew by remember{mutableStateOf(false)};var showCommon by remember{mutableStateOf(false)};var showLinks by remember{mutableStateOf(false)};var showShared by remember{mutableStateOf(false)};var showTimeline by remember{mutableStateOf(false)};var showGraph by remember{mutableStateOf(false)}
 fun refresh(id:String?=selected?.id){cases=store.list();selected=id?.let(store::load)}
 if(showNew)NewCaseDialog({showNew=false}){title,crime->selected=store.create(title,crime);refresh(selected?.id);showNew=false}
 selected?.let{w->
  if(showCommon)CommonContactsDialog(store.commonContacts(w)){showCommon=false}
  if(showLinks)LinkAnalysisDialog(store,w){showLinks=false}
  if(showShared)SharedTowerDialog(store,w){showShared=false}
  if(showTimeline)InvestigationTimelineDialog(w){showTimeline=false}
  if(showGraph)RelationshipGraphDialog(w){showGraph=false}
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text(w.title,style=MaterialTheme.typography.headlineSmall);if(w.crimeNumber.isNotBlank())Text("Crime No.: ${w.crimeNumber}");Text("${w.datasets.size} CDR dataset(s)",style=MaterialTheme.typography.bodySmall)}
   item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({selected=null},Modifier.weight(1f)){Text("All Cases")};Button(onClick={if(currentRows.isNotEmpty()){selected=store.addDataset(w,currentFileName,currentRows);refresh(selected?.id)}},enabled=currentRows.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Add Current CDR")}}}
   if(w.datasets.isNotEmpty()){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showTimeline=true},Modifier.weight(1f)){Text("Timeline")};Button({showGraph=true},Modifier.weight(1f)){Text("Relationship Graph")}}}}
   if(w.datasets.size>=2){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showCommon=true},Modifier.weight(1f)){Text("Common Contacts")};Button({showLinks=true},Modifier.weight(1f)){Text("Link Analysis")}}};item{Button({showShared=true},Modifier.fillMaxWidth()){Text("Shared Towers / Co-location")}}}
   item{Text("Saved CDRs",style=MaterialTheme.typography.titleMedium)}
   if(w.datasets.isEmpty())item{Text("No CDR saved in this case yet. Import a CDR, then tap Add Current CDR.")}else items(w.datasets){d->Card(Modifier.fillMaxWidth().clickable{onLoadDataset(d.records,d.name)}){Column(Modifier.padding(12.dp)){Text(d.name,style=MaterialTheme.typography.titleSmall);Text("${d.records.size} records",style=MaterialTheme.typography.bodySmall);Text("Tap to load for analysis",style=MaterialTheme.typography.labelSmall)}}}
   item{Text("Case Notes",style=MaterialTheme.typography.titleMedium)}
   item{var notes by remember(w.id,w.updatedAt){mutableStateOf(w.notes)};OutlinedTextField(notes,{notes=it},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("Notes")});Button({selected=store.updateNotes(w,notes);refresh(selected?.id)},Modifier.padding(top=8.dp)){Text("Save Notes")}}
  };return
 }
 Column(Modifier.fillMaxSize().padding(12.dp)){Button({showNew=true},Modifier.fillMaxWidth()){Text("New Case")};Spacer(Modifier.height(8.dp));Text("Saved Cases",style=MaterialTheme.typography.titleMedium);if(cases.isEmpty())Text("No saved cases yet.",modifier=Modifier.padding(top=16.dp))else LazyColumn{items(cases){w->ListItem(headlineContent={Text(w.title)},supportingContent={Text(listOf(w.crimeNumber,"${w.datasets.size} CDR(s)").filter{it.isNotBlank()}.joinToString(" • "))},modifier=Modifier.clickable{selected=w});HorizontalDivider()}}}
}
