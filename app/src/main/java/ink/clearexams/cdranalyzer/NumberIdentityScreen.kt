package ink.clearexams.cdranalyzer

import android.content.Intent
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
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun NumberIdentityDialog(caseId:String,workspace:CaseWorkspace,store:NumberIdentityStore,onDismiss:()->Unit){
 val context=LocalContext.current
 var scope by remember{mutableStateOf("Case")}
 var identities by remember{mutableStateOf(store.list(caseId))}
 var query by remember{mutableStateOf("")}
 var editing by remember{mutableStateOf<NumberIdentity?>(null)}
 var showEditor by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 val observed=remember(workspace){workspace.datasets.flatMap{d->d.records.flatMap{listOf(it.number,it.otherParty)}}.filter{it.isNotBlank()}.distinct().sorted()}
 fun refresh(){identities=if(scope=="Global")store.listGlobal() else store.list(caseId)}
 val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:""}.onSuccess{text->runCatching{store.importGlobalJson(text)}.onSuccess{count->status="Imported $count global identities";refresh()}.onFailure{status="Import failed: ${it.message?:"Invalid JSON"}"}}}
 fun exportGlobal(){runCatching{val dir=File(context.cacheDir,"reports").apply{mkdirs()};val file=File(dir,"global_number_directory.json");file.writeText(store.exportGlobalJson());val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);val intent=Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};context.startActivity(Intent.createChooser(intent,"Export Global Directory"));status="Global directory exported"}.onFailure{status="Export failed: ${it.message?:"Unknown error"}"}}
 LaunchedEffect(scope){refresh()}
 val selectedMap=identities.associateBy{NumberIdentityStore.normalize(it.number)}
 val rows=(if(scope=="Case") observed.map{n->store.find(caseId,n)?:NumberIdentity(n)} else identities).filter{r->query.isBlank()||r.number.contains(query,true)||r.name.contains(query,true)||r.effectiveRole.contains(query,true)}
 if(showEditor)IdentityEditorDialog(editing?:NumberIdentity(""),observed,{showEditor=false}){saved->if(scope=="Global")store.saveGlobal(saved) else store.save(caseId,saved);refresh();showEditor=false}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Number Identities")},text={Column{
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(scope=="Case",{scope="Case"},{Text("Case identity")});FilterChip(scope=="Global",{scope="Global"},{Text("Global directory")})}
  OutlinedTextField(query,{query=it},label={Text("Search number / name / role")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=6.dp))
  Button({editing=NumberIdentity("");showEditor=true},Modifier.fillMaxWidth().padding(vertical=8.dp)){Text(if(scope=="Global")"Add Global Identity" else "Add Case Identity")}
  if(scope=="Global"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton({importLauncher.launch(arrayOf("application/json","*/*"))},Modifier.weight(1f)){Text("Import")};OutlinedButton(::exportGlobal,Modifier.weight(1f),enabled=store.listGlobal().isNotEmpty()){Text("Export")}}}
  if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
  Text(if(scope=="Global")"${identities.size} global identity/identities" else "${store.list(caseId).size} case override(s) • ${store.listGlobal().size} global fallback(s) • ${observed.size} observed number(s)",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp))
  LazyColumn(Modifier.heightIn(max=500.dp)){items(rows.take(1500)){r->val tagged=if(scope=="Global")selectedMap.containsKey(NumberIdentityStore.normalize(r.number)) else store.find(caseId,r.number)!=null;ListItem(headlineContent={Text(if(tagged)r.displayName else r.number)},supportingContent={Text(if(tagged)listOf(r.number,r.effectiveRole,r.notes).filter{it.isNotBlank()}.joinToString(" • ") else "Untagged — tap to identify")},modifier=Modifier.clickable{editing=r;showEditor=true});HorizontalDivider()}}
 }},confirmButton={TextButton(onDismiss){Text("Close")}})
}

@Composable
private fun IdentityEditorDialog(initial:NumberIdentity,observed:List<String>,onDismiss:()->Unit,onSave:(NumberIdentity)->Unit){
 var number by remember(initial){mutableStateOf(initial.number)};var name by remember(initial){mutableStateOf(initial.name)};var role by remember(initial){mutableStateOf(initial.role)};var custom by remember(initial){mutableStateOf(initial.customRole)};var notes by remember(initial){mutableStateOf(initial.notes)};var roleMenu by remember{mutableStateOf(false)};var numberMenu by remember{mutableStateOf(false)}
 AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial.number.isBlank())"Add Identity" else "Edit Identity")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  Box{OutlinedTextField(number,{number=it},label={Text("Phone number")},singleLine=true,modifier=Modifier.fillMaxWidth());if(initial.number.isBlank()&&observed.isNotEmpty()){TextButton({numberMenu=true},Modifier.padding(top=48.dp)){Text("Choose observed number")};DropdownMenu(numberMenu,{numberMenu=false}){observed.take(500).forEach{n->DropdownMenuItem({Text(n)},{number=n;numberMenu=false})}}}}
  OutlinedTextField(name,{name=it},label={Text("Known name")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Box{OutlinedButton({roleMenu=true},Modifier.fillMaxWidth()){Text("Role: $role")};DropdownMenu(roleMenu,{roleMenu=false}){NumberIdentityStore.roles.forEach{r->DropdownMenuItem({Text(r)},{role=r;roleMenu=false})}}}
  if(role=="Other")OutlinedTextField(custom,{custom=it},label={Text("Custom role")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(notes,{notes=it},label={Text("Identity notes")},modifier=Modifier.fillMaxWidth().heightIn(min=80.dp))
 }},confirmButton={Button({onSave(NumberIdentity(number.trim(),name.trim(),role,custom.trim(),notes.trim()))},enabled=number.isNotBlank()){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
