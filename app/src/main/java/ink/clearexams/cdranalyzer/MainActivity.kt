package ink.clearexams.cdranalyzer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.InputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

data class CdrRecord(val number:String="",val otherParty:String="",val direction:String="",val dateTime:String="",val duration:String="",val imei:String="",val imsi:String="",val cellId:String="",val lac:String="")
data class Summary(val records:Int=0,val contacts:Int=0,val incoming:Int=0,val outgoing:Int=0)
data class ColumnMap(val number:Int=-1,val other:Int=-1,val direction:Int=-1,val dateTime:Int=-1,val duration:Int=-1,val imei:Int=-1,val imsi:Int=-1,val cell:Int=-1,val lac:Int=-1)
data class DeviceChange(val at:String,val oldImei:String,val newImei:String,val oldImsi:String,val newImsi:String)
data class ContactTag(val name:String="",val relation:String="")

class MainActivity:ComponentActivity(){
 private val prefs by lazy{getSharedPreferences("contact_tags",MODE_PRIVATE)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{CdrApp()}}}
 private fun loadTags():Map<String,ContactTag>{val out=mutableMapOf<String,ContactTag>();for((key,value) in prefs.all){val raw=value as? String?:continue;val parts=raw.split("|",limit=2);out[key]=ContactTag(parts.getOrElse(0){""},parts.getOrElse(1){""})};return out}
 private fun saveTag(number:String,tag:ContactTag){prefs.edit().putString(number,"${tag.name}|${tag.relation}").apply()}

 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun CdrApp(){
  var rows by remember{mutableStateOf<List<CdrRecord>>(emptyList())};var fileName by remember{mutableStateOf("No CDR loaded")};var error by remember{mutableStateOf<String?>(null)};var tab by remember{mutableIntStateOf(0)};var search by remember{mutableStateOf("")};var tags by remember{mutableStateOf(loadTags())};var editNumber by remember{mutableStateOf<String?>(null)}
  val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->if(uri!=null){try{contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);val stream=contentResolver.openInputStream(uri);if(stream!=null){rows=readWorkbook(stream);fileName=uri.lastPathSegment?.substringAfterLast('/')?:"CDR file";error=null}else error="Unable to open file"}catch(e:Exception){error=e.message?:"Unable to read file"}}}
  val filtered=remember(rows,search,tags){if(search.isBlank())rows else rows.filter{r->val tag=tags[r.otherParty];listOf(r.number,r.otherParty,r.direction,r.dateTime,r.imei,r.imsi,r.cellId,r.lac,tag?.name.orEmpty(),tag?.relation.orEmpty()).any{it.contains(search,true)}}}
  val summary=remember(rows){Summary(rows.size,rows.map{it.otherParty}.filter{it.isNotBlank()}.distinct().size,rows.count{normalizeDirection(it.direction)=="Incoming"},rows.count{normalizeDirection(it.direction)=="Outgoing"})}
  Scaffold(topBar={TopAppBar(title={Column{Text("CDR Analyzer");Text("Native • Local analysis",style=MaterialTheme.typography.labelSmall)}})}){padding->Column(Modifier.padding(padding).padding(12.dp).fillMaxSize()){
   Button(onClick={picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","*/*"))},modifier=Modifier.fillMaxWidth()){Text("Import CDR file")};Text(fileName,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp));error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Records",summary.records.toString(),Modifier.weight(1f));Stat("Contacts",summary.contacts.toString(),Modifier.weight(1f))};Spacer(Modifier.height(6.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Incoming",summary.incoming.toString(),Modifier.weight(1f));Stat("Outgoing",summary.outgoing.toString(),Modifier.weight(1f))}
   OutlinedTextField(search,{search=it},label={Text("Search name / number / IMEI / IMSI / tower")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=8.dp));ScrollableTabRow(tab,edgePadding=0.dp,modifier=Modifier.padding(top=8.dp)){listOf("Calls","Contacts","Devices","Towers","Movement","Notes").forEachIndexed{i,t->Tab(tab==i,{tab=i},text={Text(t)})}}
   when(tab){0->RecordList(filtered,tags){editNumber=it};1->ContactList(filtered,tags){editNumber=it};2->DeviceList(filtered);3->TowerList(filtered);4->EmptyFeature("Movement","Chronological tower movement will be plotted after tower-coordinate enrichment.");else->EmptyFeature("Investigation notes","Local case notes are the next persistence module.")}
  }}
  editNumber?.let{number->TagDialog(number,tags[number],onDismiss={editNumber=null}){tag->saveTag(number,tag);tags=tags.toMutableMap().apply{put(number,tag)};editNumber=null}}
 }
 @Composable private fun TagDialog(number:String,current:ContactTag?,onDismiss:()->Unit,onSave:(ContactTag)->Unit){var name by remember(number){mutableStateOf(current?.name.orEmpty())};var relation by remember(number){mutableStateOf(current?.relation?:"Other")};var expanded by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text("Tag contact")},text={Column{Text(number);OutlinedTextField(name,{name=it},label={Text("Name / known identity")},modifier=Modifier.fillMaxWidth());Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("Relation: $relation")};DropdownMenu(expanded,{expanded=false}){listOf("Suspect","Victim","Witness","Associate","Family","Other").forEach{r->DropdownMenuItem(text={Text(r)},onClick={relation=r;expanded=false})}}};if(relation=="Other")Text("Enter any custom name above; it will be shown instead of the number.",style=MaterialTheme.typography.bodySmall)}},confirmButton={Button(onClick={onSave(ContactTag(name.trim(),relation))}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
 @Composable private fun Stat(label:String,value:String,modifier:Modifier){Card(modifier){Column(Modifier.padding(10.dp)){Text(value,style=MaterialTheme.typography.headlineSmall);Text(label,style=MaterialTheme.typography.labelMedium)}}}
 private fun display(number:String,tags:Map<String,ContactTag>):String{val t=tags[number];return if(t!=null&&t.name.isNotBlank())"${t.name} ($number)" else number}
 @Composable private fun RecordList(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onTag:(String)->Unit){if(rows.isEmpty()){EmptyFeature("Import or search CDR","No matching records.");return};LazyColumn(Modifier.fillMaxSize()){items(rows.take(1000)){r->val n=r.otherParty.ifBlank{r.number};val d=mutableListOf<String>();tags[n]?.relation?.takeIf{it.isNotBlank()}?.let(d::add);normalizeDirection(r.direction).takeIf{it.isNotBlank()}?.let(d::add);r.dateTime.takeIf{it.isNotBlank()}?.let(d::add);r.duration.takeIf{it.isNotBlank()}?.let{d.add("${it}s")};ListItem(headlineContent={Text(display(n,tags))},supportingContent={Text(d.joinToString(" • "))},modifier=Modifier.clickable{if(n.isNotBlank())onTag(n)});HorizontalDivider()}}}
 @Composable private fun ContactList(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onTag:(String)->Unit){val contacts=rows.filter{it.otherParty.isNotBlank()}.groupingBy{it.otherParty}.eachCount().entries.sortedByDescending{it.value};LazyColumn(Modifier.fillMaxSize()){items(contacts){e->val tag=tags[e.key];ListItem(headlineContent={Text(display(e.key,tags))},supportingContent={Text(listOfNotNull(tag?.relation?.takeIf{it.isNotBlank()},"${e.value} interactions").joinToString(" • "))},modifier=Modifier.clickable{onTag(e.key)});HorizontalDivider()}}}
 @Composable private fun DeviceList(rows:List<CdrRecord>){val imeis=rows.filter{it.imei.isNotBlank()}.groupBy{it.imei};if(imeis.isEmpty()){EmptyFeature("Device / SIM changes","No IMEI column was detected in this file.");return};val changes=detectDeviceChanges(rows);LazyColumn(Modifier.fillMaxSize()){item{Text("Detected changes: ${changes.size}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};if(changes.isEmpty())item{Text("No sequential IMEI/IMSI change detected.",modifier=Modifier.padding(12.dp))}else items(changes){c->ListItem(headlineContent={Text(c.at.ifBlank{"Time unavailable"})},supportingContent={Text("IMEI: ${c.oldImei.ifBlank{"—"}} → ${c.newImei.ifBlank{"—"}}\nIMSI: ${c.oldImsi.ifBlank{"—"}} → ${c.newImsi.ifBlank{"—"}}")});HorizontalDivider()};item{Text("Device usage",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};items(imeis.entries.toList()){e->val ordered=e.value.filter{it.dateTime.isNotBlank()}.sortedBy{it.dateTime};ListItem(headlineContent={Text("IMEI ${e.key}")},supportingContent={Text("First use: ${ordered.firstOrNull()?.dateTime?:"Unknown"}\nLast use: ${ordered.lastOrNull()?.dateTime?:"Unknown"}\nIMSI: ${e.value.map{it.imsi}.filter{it.isNotBlank()}.distinct().joinToString()}\nRecords: ${e.value.size}")});HorizontalDivider()}}}
 @Composable private fun TowerList(rows:List<CdrRecord>){val x=rows.filter{it.cellId.isNotBlank()}.groupingBy{"${it.lac}/${it.cellId}"}.eachCount().entries.sortedByDescending{it.value};if(x.isEmpty())EmptyFeature("Tower analysis","No Cell ID column was detected.")else SimpleList(x.map{"LAC/Cell ${it.key} — ${it.value} records"})}
 @Composable private fun SimpleList(lines:List<String>){LazyColumn(Modifier.fillMaxSize()){items(lines){ListItem(headlineContent={Text(it)});HorizontalDivider()}}}
 @Composable private fun EmptyFeature(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.titleMedium);Text(body,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}}}
 private fun detectDeviceChanges(rows:List<CdrRecord>):List<DeviceChange>{val ordered=rows.filter{it.imei.isNotBlank()||it.imsi.isNotBlank()}.sortedBy{it.dateTime};val out=mutableListOf<DeviceChange>();var p:CdrRecord?=null;for(c in ordered){val q=p;if(q!=null){val a=q.imei.isNotBlank()&&c.imei.isNotBlank()&&q.imei!=c.imei;val b=q.imsi.isNotBlank()&&c.imsi.isNotBlank()&&q.imsi!=c.imsi;if(a||b)out.add(DeviceChange(c.dateTime,q.imei,c.imei,q.imsi,c.imsi))};p=c};return out}
 private fun normalizeDirection(v:String):String{val s=v.lowercase();return when{s.contains("incoming")||s=="in"||s.contains("mti")->"Incoming";s.contains("outgoing")||s=="out"||s.contains("moc")->"Outgoing";s.contains("sms")->"SMS";else->v}}
 private fun norm(v:String)=v.lowercase().replace(" ","").replace("_","").replace("-","")
 private fun find(h:List<String>,vararg n:String):Int{val x=h.map(::norm);return x.indexOfFirst{c->n.any{c.contains(norm(it))}}}
 private fun detect(h:List<String>)=ColumnMap(find(h,"callingnumber","msisdn","anumber","subscriber"),find(h,"callednumber","otherparty","bnumber","diallednumber","connectednumber"),find(h,"calltype","direction","type"),find(h,"datetime","calltime","starttime","date"),find(h,"duration","callduration"),find(h,"imei"),find(h,"imsi"),find(h,"cellid","celltower","cgi"),find(h,"lac","locationareacode"))
 private fun readWorkbook(input:InputStream):List<CdrRecord>{input.use{s->WorkbookFactory.create(s).use{w->val sheet=w.getSheetAt(0);val f=DataFormatter();val header=sheet.getRow(0)?:return emptyList();val h=(0 until header.lastCellNum).map{f.formatCellValue(header.getCell(it)).trim()};val m=detect(h);val out=mutableListOf<CdrRecord>();fun cell(r:Row,i:Int)=if(i<0)"" else f.formatCellValue(r.getCell(i)).trim();for(i in 1..sheet.lastRowNum){val r=sheet.getRow(i)?:continue;val x=CdrRecord(cell(r,m.number),cell(r,m.other),cell(r,m.direction),cell(r,m.dateTime),cell(r,m.duration),cell(r,m.imei),cell(r,m.imsi),cell(r,m.cell),cell(r,m.lac));if(listOf(x.number,x.otherParty,x.dateTime,x.imei,x.cellId).any{it.isNotBlank()})out.add(x)};return out}}}
}
