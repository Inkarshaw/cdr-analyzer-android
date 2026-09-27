package ink.clearexams.cdranalyzer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun CaseWorkspaceScreen(store:CaseWorkspaceStore,currentRows:List<CdrRecord>,currentFileName:String,onLoadDataset:(List<CdrRecord>,String)->Unit){
 val context=LocalContext.current;val identityStore=remember{NumberIdentityStore(context)}
 var cases by remember{mutableStateOf(store.list())};var selected by remember{mutableStateOf<CaseWorkspace?>(null)};var showNew by remember{mutableStateOf(false)};var showCommon by remember{mutableStateOf(false)};var showLinks by remember{mutableStateOf(false)};var showShared by remember{mutableStateOf(false)};var showTimeline by remember{mutableStateOf(false)};var showGraph by remember{mutableStateOf(false)};var showIdentities by remember{mutableStateOf(false)};var fromText by remember{mutableStateOf("")};var toText by remember{mutableStateOf("")};var reportStatus by remember{mutableStateOf("")};var backupStatus by remember{mutableStateOf("")}
 fun refresh(id:String?=selected?.id){cases=store.list();selected=id?.let(store::load)}
 val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{CaseBackupManager.restore(context,uri,store)}.onSuccess{restored->cases=store.list();selected=restored;fromText="";toText="";backupStatus="Restored ${restored.title}"}.onFailure{backupStatus="Restore failed: ${it.message?:"Invalid backup"}"}}}
 if(showNew)NewCaseDialog({showNew=false}){title,crime->selected=store.create(title,crime);refresh(selected?.id);showNew=false}
 selected?.let{w->
  val timeFilter=CaseTimeFilter(fromText.trim(),toText.trim());val filterValid=CaseTimeFiltering.valid(timeFilter);val analysisWorkspace=if(filterValid)CaseTimeFiltering.apply(w,timeFilter) else w;val originalCount=CaseTimeFiltering.count(w);val filteredCount=CaseTimeFiltering.count(analysisWorkspace)
  if(showIdentities)NumberIdentityDialog(w.id,w,identityStore){showIdentities=false}
  if(showCommon)CommonContactsDialog(store.commonContacts(analysisWorkspace)){showCommon=false}
  if(showLinks)LinkAnalysisDialog(store,analysisWorkspace){showLinks=false}
  if(showShared)SharedTowerDialog(store,analysisWorkspace){showShared=false}
  if(showTimeline)InvestigationTimelineDialog(analysisWorkspace){showTimeline=false}
  if(showGraph)RelationshipGraphDialog(analysisWorkspace){showGraph=false}
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text(w.title,style=MaterialTheme.typography.headlineSmall);if(w.crimeNumber.isNotBlank())Text("Crime No.: ${w.crimeNumber}");Text("${w.datasets.size} CDR dataset(s) • $originalCount records",style=MaterialTheme.typography.bodySmall)}
   item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({selected=null},Modifier.weight(1f)){Text("All Cases")};Button(onClick={if(currentRows.isNotEmpty()){selected=store.addDataset(w,currentFileName,currentRows);refresh(selected?.id)}},enabled=currentRows.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Add Current CDR")}}}
   if(w.datasets.isNotEmpty())item{Button({showIdentities=true},Modifier.fillMaxWidth()){Text("Manage Number Identities / Roles")}}
   if(w.datasets.isNotEmpty())item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Analysis Date / Time Filter",style=MaterialTheme.typography.titleSmall);OutlinedTextField(fromText,{fromText=it},label={Text("From: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(toText,{toText=it},label={Text("To: DD-MM-YYYY HH:MM")},singleLine=true,modifier=Modifier.fillMaxWidth());if(!filterValid)Text("Invalid date/time range. Analysis is using all records.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)else if(timeFilter.active)Text("Filter active: $filteredCount of $originalCount records",style=MaterialTheme.typography.bodySmall)else Text("No filter: all $originalCount records",style=MaterialTheme.typography.bodySmall);if(timeFilter.active)OutlinedButton({fromText="";toText=""},Modifier.fillMaxWidth()){Text("Clear Date / Time Filter")}}}}
   if(w.datasets.isNotEmpty())item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showTimeline=true},Modifier.weight(1f),enabled=filterValid){Text("Timeline")};Button({showGraph=true},Modifier.weight(1f),enabled=filterValid){Text("Relationship Graph")}}}
   if(w.datasets.size>=2){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showCommon=true},Modifier.weight(1f),enabled=filterValid){Text("Common Contacts")};Button({showLinks=true},Modifier.weight(1f),enabled=filterValid){Text("Link Analysis")}}};item{Button({showShared=true},Modifier.fillMaxWidth(),enabled=filterValid){Text("Shared Towers / Co-location")}}}
   if(w.datasets.isNotEmpty())item{Button(onClick={runCatching{val f=CaseReportExporter.export(context,w,timeFilter);reportStatus="Report created: ${f.name}";CaseReportExporter.share(context,f)}.onFailure{reportStatus="Report export failed: ${it.message?:"Unknown error"}"}},modifier=Modifier.fillMaxWidth(),enabled=filterValid){Text("Export / Share Investigation Report")};if(reportStatus.isNotBlank())Text(reportStatus,style=MaterialTheme.typography.bodySmall)}
   item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Case Backup",style=MaterialTheme.typography.titleSmall);Text("Backup includes case details, notes and all saved CDR records.",style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={runCatching{val f=CaseBackupManager.export(context,w);backupStatus="Backup created: ${f.name}";CaseBackupManager.share(context,f)}.onFailure{backupStatus="Backup failed: ${it.message?:"Unknown error"}"}},modifier=Modifier.weight(1f)){Text("Backup Case")};Button(onClick={restoreLauncher.launch(arrayOf("application/json","application/octet-stream","*/*"))},modifier=Modifier.weight(1f)){Text("Restore Backup")}};if(backupStatus.isNotBlank())Text(backupStatus,style=MaterialTheme.typography.bodySmall)}}}
   item{Text("Saved CDRs",style=MaterialTheme.typography.titleMedium)}
   if(w.datasets.isEmpty())item{Text("No CDR saved in this case yet. Import a CDR, then tap Add Current CDR.")}else items(w.datasets){d->Card(Modifier.fillMaxWidth().clickable{onLoadDataset(d.records,d.name)}){Column(Modifier.padding(12.dp)){Text(d.name,style=MaterialTheme.typography.titleSmall);Text("${d.records.size} records",style=MaterialTheme.typography.bodySmall);Text("Tap to load for analysis",style=MaterialTheme.typography.labelSmall)}}}
   item{Text("Case Notes",style=MaterialTheme.typography.titleMedium)}
   item{var notes by remember(w.id,w.updatedAt){mutableStateOf(w.notes)};OutlinedTextField(notes,{notes=it},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("Notes")});Button({selected=store.updateNotes(w,notes);refresh(selected?.id)},Modifier.padding(top=8.dp)){Text("Save Notes")}}
  };return
 }
 Column(Modifier.fillMaxSize().padding(12.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showNew=true},Modifier.weight(1f)){Text("New Case")};OutlinedButton({restoreLauncher.launch(arrayOf("application/json","application/octet-stream","*/*"))},Modifier.weight(1f)){Text("Restore Backup")}};if(backupStatus.isNotBlank())Text(backupStatus,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp));Spacer(Modifier.height(8.dp));Text("Saved Cases",style=MaterialTheme.typography.titleMedium);if(cases.isEmpty())Text("No saved cases yet.",modifier=Modifier.padding(top=16.dp))else LazyColumn{items(cases){w->ListItem(headlineContent={Text(w.title)},supportingContent={Text(listOf(w.crimeNumber,"${w.datasets.size} CDR(s)").filter{it.isNotBlank()}.joinToString(" • "))},modifier=Modifier.clickable{selected=w});HorizontalDivider()}}}
}
