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
 var cases by remember{mutableStateOf(store.list())};var selected by remember{mutableStateOf<CaseWorkspace?>(null)};var showNew by remember{mutableStateOf(false)};var showCommon by remember{mutableStateOf(false)};var showLinks by remember{mutableStateOf(false)}
 fun refresh(id:String?=selected?.id){cases=store.list();selected=id?.let(store::load)}
 if(showNew)NewCaseDialog({showNew=false}){title,crime->selected=store.create(title,crime);refresh(selected?.id);showNew=false}
 selected?.let{w->
  if(showCommon)CommonContactsDialog(store.commonContacts(w)){showCommon=false}
  if(showLinks)LinkAnalysisDialog(store.linkProfiles(w)){showLinks=false}
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text(w.title,style=MaterialTheme.typography.headlineSmall);if(w.crimeNumber.isNotBlank())Text("Crime No.: ${w.crimeNumber}");Text("${w.datasets.size} CDR dataset(s)",style=MaterialTheme.typography.bodySmall)}
   item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({selected=null},Modifier.weight(1f)){Text("All Cases")};Button(onClick={if(currentRows.isNotEmpty()){selected=store.addDataset(w,currentFileName,currentRows);refresh(selected?.id)}},enabled=currentRows.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Add Current CDR")}}}
   if(w.datasets.size>=2)item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({showCommon=true},Modifier.weight(1f)){Text("Common Contacts")};Button({showLinks=true},Modifier.weight(1f)){Text("Link Analysis")}}}
   item{Text("Saved CDRs",style=MaterialTheme.typography.titleMedium)}
   if(w.datasets.isEmpty())item{Text("No CDR saved in this case yet. Import a CDR, then tap Add Current CDR.")}else items(w.datasets){d->Card(Modifier.fillMaxWidth().clickable{onLoadDataset(d.records,d.name)}){Column(Modifier.padding(12.dp)){Text(d.name,style=MaterialTheme.typography.titleSmall);Text("${d.records.size} records",style=MaterialTheme.typography.bodySmall);Text("Tap to load for analysis",style=MaterialTheme.typography.labelSmall)}}}
   item{Text("Case Notes",style=MaterialTheme.typography.titleMedium)}
   item{var notes by remember(w.id,w.updatedAt){mutableStateOf(w.notes)};OutlinedTextField(notes,{notes=it},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("Notes")});Button({selected=store.updateNotes(w,notes);refresh(selected?.id)},Modifier.padding(top=8.dp)){Text("Save Notes")}}
  };return
 }
 Column(Modifier.fillMaxSize().padding(12.dp)){Button({showNew=true},Modifier.fillMaxWidth()){Text("New Case")};Spacer(Modifier.height(8.dp));Text("Saved Cases",style=MaterialTheme.typography.titleMedium);if(cases.isEmpty())Text("No saved cases yet.",modifier=Modifier.padding(top=16.dp))else LazyColumn{items(cases){w->ListItem(headlineContent={Text(w.title)},supportingContent={Text(listOf(w.crimeNumber,"${w.datasets.size} CDR(s)").filter{it.isNotBlank()}.joinToString(" • "))},modifier=Modifier.clickable{selected=w});HorizontalDivider()}}}
}

@Composable private fun NewCaseDialog(onDismiss:()->Unit,onCreate:(String,String)->Unit){var title by remember{mutableStateOf("")};var crime by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("New Case")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(title,{title=it},label={Text("Case name")},singleLine=true);OutlinedTextField(crime,{crime=it},label={Text("Crime No. (optional)")},singleLine=true)}},confirmButton={Button({onCreate(title,crime)},enabled=title.isNotBlank()){Text("Create")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})}

@Composable private fun CommonContactsDialog(contacts:List<CommonContact>,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Common Contacts")},text={if(contacts.isEmpty())Text("No number appears in two or more saved CDRs.")else LazyColumn(Modifier.heightIn(max=480.dp)){items(contacts.take(200)){c->ListItem(headlineContent={Text(c.number)},supportingContent={Text("${c.datasetCount} CDRs • ${c.totalInteractions} interactions\n${c.datasetNames.joinToString()}")});HorizontalDivider()}}},confirmButton={TextButton(onDismiss){Text("Close")}})}

@Composable private fun LinkAnalysisDialog(profiles:List<LinkProfile>,onDismiss:()->Unit){var query by remember{mutableStateOf("")};var chosen by remember{mutableStateOf<LinkProfile?>(null)};val filtered=remember(profiles,query){if(query.isBlank())profiles else profiles.filter{it.number.contains(query,true)||it.imeis.any{x->x.contains(query,true)}||it.imsis.any{x->x.contains(query,true)}||it.towers.any{x->x.contains(query,true)}}};AlertDialog(onDismissRequest=onDismiss,title={Text("Cross-CDR Link Analysis")},text={Column{OutlinedTextField(query,{query=it},label={Text("Search number / IMEI / IMSI / tower")},singleLine=true,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));chosen?.let{p->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(p.number,style=MaterialTheme.typography.titleMedium);Text("${p.totalInteractions} interactions • ${p.datasetNames.size} CDR(s)");if(p.datasetNames.isNotEmpty())Text("CDRs: ${p.datasetNames.joinToString()}");if(p.imeis.isNotEmpty())Text("IMEI: ${p.imeis.joinToString()}");if(p.imsis.isNotEmpty())Text("IMSI: ${p.imsis.joinToString()}");if(p.towers.isNotEmpty())Text("Towers: ${p.towers.joinToString()}")}};Spacer(Modifier.height(8.dp))};LazyColumn(Modifier.heightIn(max=380.dp)){items(filtered.take(300)){p->ListItem(headlineContent={Text(p.number)},supportingContent={Text("${p.totalInteractions} interactions • ${p.datasetNames.size} CDR(s) • ${p.towers.size} tower(s)")},modifier=Modifier.clickable{chosen=p});HorizontalDivider()}}}},confirmButton={TextButton(onDismiss){Text("Close")}})}
