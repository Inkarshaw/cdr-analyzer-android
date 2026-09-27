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
fun NumberIdentityDialog(caseId:String,workspace:CaseWorkspace,store:NumberIdentityStore,onDismiss:()->Unit){
 var identities by remember{mutableStateOf(store.list(caseId))}
 var query by remember{mutableStateOf("")}
 var editing by remember{mutableStateOf<NumberIdentity?>(null)}
 var showEditor by remember{mutableStateOf(false)}
 val observed=remember(workspace){workspace.datasets.flatMap{d->d.records.flatMap{listOf(it.number,it.otherParty)}}.filter{it.isNotBlank()}.distinct().sorted()}
 val identityMap=identities.associateBy{NumberIdentityStore.normalize(it.number)}
 val rows=observed.map{n->identityMap[NumberIdentityStore.normalize(n)]?:NumberIdentity(n)}.filter{r->query.isBlank()||r.number.contains(query,true)||r.name.contains(query,true)||r.effectiveRole.contains(query,true)}
 if(showEditor)IdentityEditorDialog(editing?:NumberIdentity(""),observed,{showEditor=false}){saved->store.save(caseId,saved);identities=store.list(caseId);showEditor=false}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Number Identities")},text={Column{
  OutlinedTextField(query,{query=it},label={Text("Search number / name / role")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Button({editing=NumberIdentity("");showEditor=true},Modifier.fillMaxWidth().padding(vertical=8.dp)){Text("Add Identity")}
  Text("${identities.size} tagged • ${observed.size} observed number(s)",style=MaterialTheme.typography.bodySmall)
  LazyColumn(Modifier.heightIn(max=500.dp)){items(rows.take(1500)){r->val tagged=identityMap.containsKey(NumberIdentityStore.normalize(r.number));ListItem(headlineContent={Text(if(tagged)r.displayName else r.number)},supportingContent={Text(if(tagged)listOf(r.number,r.effectiveRole,r.notes).filter{it.isNotBlank()}.joinToString(" • ") else "Untagged — tap to identify")},modifier=Modifier.clickable{editing=r;showEditor=true});HorizontalDivider()}}
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
