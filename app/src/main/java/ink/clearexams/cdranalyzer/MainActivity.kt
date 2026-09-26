package ink.clearexams.cdranalyzer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{CdrApp()}}}
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun CdrApp(){
  var rows by remember{mutableStateOf<List<CdrRecord>>(emptyList())};var fileName by remember{mutableStateOf("No CDR loaded")};var error by remember{mutableStateOf<String?>(null)};var tab by remember{mutableIntStateOf(0)};var search by remember{mutableStateOf("")}
  val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->if(uri!=null)try{contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);rows=readWorkbook(contentResolver.openInputStream(uri)!!);fileName=uri.lastPathSegment?.substringAfterLast('/')?:"CDR file";error=null}catch(e:Exception){error=e.message?:"Unable to read file"}}
  val filtered=remember(rows,search){if(search.isBlank())rows else rows.filter{r->listOf(r.number,r.otherParty,r.direction,r.dateTime,r.imei,r.imsi,r.cellId,r.lac).any{it.contains(search,true)}}}
  val summary=remember(rows){Summary(rows.size,rows.map{it.otherParty}.filter{it.isNotBlank()}.distinct().size,rows.count{normalizeDirection(it.direction)=="Incoming"},rows.count{normalizeDirection(it.direction)=="Outgoing"})}
  Scaffold(topBar={TopAppBar(title={Column{Text("CDR Analyzer");Text("Native • Local analysis",style=MaterialTheme.typography.labelSmall)}})}){pad->Column(Modifier.padding(pad).padding(12.dp).fillMaxSize()){
   Button(onClick={picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","*/*"))},modifier=Modifier.fillMaxWidth()){Text("Import CDR file")};Text(fileName,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp));error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Records",summary.records.toString(),Modifier.weight(1f));Stat("Contacts",summary.contacts.toString(),Modifier.weight(1f))};Spacer(Modifier.height(6.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Incoming",summary.incoming.toString(),Modifier.weight(1f));Stat("Outgoing",summary.outgoing.toString(),Modifier.weight(1f))}
   OutlinedTextField(value=search,onValueChange={search=it},label={Text("Search number / IMEI / IMSI / tower")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=8.dp));ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp,modifier=Modifier.padding(top=8.dp)){listOf("Calls","Contacts","Devices","Towers","Movement","Notes").forEachIndexed{i,t->Tab(selected=tab==i,onClick={tab=i},text={Text(t)})}}
   when(tab){0->RecordList(filtered);1->ContactList(filtered);2->DeviceList(filtered);3->TowerList(filtered);4->EmptyFeature("Movement","Chronological tower movement will be plotted after tower-coordinate enrichment.");else->EmptyFeature("Investigation notes","Local case notes and tagging are the next persistence module.")}
  }}
 }
 @Composable private fun Stat(l:String,v:String,m:Modifier){Card(m){Column(Modifier.padding(10.dp)){Text(v,style=MaterialTheme.typography.headlineSmall);Text(l,style=MaterialTheme.typography.labelMedium)}}}
 @Composable private fun RecordList(rows:List<CdrRecord>){if(rows.isEmpty()){EmptyFeature("Import or search CDR","No matching records.")}else{LazyColumn(Modifier.fillMaxSize()){items(rows.take(1000)){r->val detail=listOf(normalizeDirection(r.direction),r.dateTime,if(r.duration.isNotBlank())"${r.duration}s" else "",if(r.imei.isNotBlank())"IMEI ${r.imei}" else "").filter{it.isNotBlank()}.joinToString(" • ");ListItem(headlineContent={Text(r.otherParty.ifBlank{r.number.ifBlank{"Unknown"}})},supportingContent={Text(detail)});HorizontalDivider()}}}}
 @Composable private fun ContactList(rows:List<CdrRecord>){val x=rows.filter{it.otherParty.isNotBlank()}.groupingBy{it.otherParty}.eachCount().entries.sortedByDescending{it.value};SimpleList(x.map{"${it.key} — ${it.value} interactions"})}
 @Composable private fun DeviceList(rows:List<CdrRecord>){val imeis=rows.filter{it.imei.isNotBlank()}.groupBy{it.imei};if(imeis.isEmpty()){EmptyFeature("Device / SIM changes","No IMEI column was detected in this file.")}else{val lines=imeis.entries.map{(imei,v)->val ordered=v.filter{it.dateTime.isNotBlank()}.sortedBy{it.dateTime};val first=ordered.firstOrNull()?.dateTime?:"Unknown";val last=ordered.lastOrNull()?.dateTime?:"Unknown";val sims=v.map{it.imsi}.filter{it.isNotBlank()}.distinct();"IMEI $imei\nFirst use: $first\nLast use: $last\nIMSI: ${sims.joinToString()}\nRecords: ${v.size}"};SimpleList(lines)}}
 @Composable private fun TowerList(rows:List<CdrRecord>){val towers=rows.filter{it.cellId.isNotBlank()}.groupingBy{"${it.lac}/${it.cellId}"}.eachCount().entries.sortedByDescending{it.value};if(towers.isEmpty()){EmptyFeature("Tower analysis","No Cell ID column was detected.")}else{SimpleList(towers.map{"LAC/Cell ${it.key} — ${it.value} records"})}}
 @Composable private fun SimpleList(lines:List<String>){LazyColumn(Modifier.fillMaxSize()){items(lines){line->ListItem(headlineContent={Text(line)});HorizontalDivider()}}}
 @Composable private fun EmptyFeature(t:String,b:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(t,style=MaterialTheme.typography.titleMedium);Text(b,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}}}
 private fun normalizeDirection(v:String):String{val s=v.lowercase();return when{s.contains("incoming")||s=="in"||s.contains("mti")->"Incoming";s.contains("outgoing")||s=="out"||s.contains("moc")->"Outgoing";s.contains("sms")->"SMS";else->v}}
 private fun norm(v:String)=v.lowercase().replace(" ","").replace("_","").replace("-","")
 private fun find(headers:List<String>,vararg names:String):Int{val h=headers.map(::norm);return h.indexOfFirst{cell->names.any{cell.contains(norm(it))}}}
 private fun detect(h:List<String>)=ColumnMap(find(h,"callingnumber","msisdn","anumber","subscriber"),find(h,"callednumber","otherparty","bnumber","diallednumber","connectednumber"),find(h,"calltype","direction","type"),find(h,"datetime","calltime","starttime","date"),find(h,"duration","callduration"),find(h,"imei"),find(h,"imsi"),find(h,"cellid","celltower","cgi"),find(h,"lac","locationareacode"))
 private fun readWorkbook(input:InputStream):List<CdrRecord>{input.use{stream->WorkbookFactory.create(stream).use{wb->val sheet=wb.getSheetAt(0);val f=DataFormatter();val hr=sheet.getRow(0)?:return emptyList();val headers=(0 until hr.lastCellNum).map{f.formatCellValue(hr.getCell(it)).trim()};val m=detect(headers);val out=mutableListOf<CdrRecord>();fun get(r:Row,i:Int)=if(i<0)"" else f.formatCellValue(r.getCell(i)).trim();for(i in 1..sheet.lastRowNum){val r=sheet.getRow(i)?:continue;val x=CdrRecord(get(r,m.number),get(r,m.other),get(r,m.direction),get(r,m.dateTime),get(r,m.duration),get(r,m.imei),get(r,m.imsi),get(r,m.cell),get(r,m.lac));if(listOf(x.number,x.otherParty,x.dateTime,x.imei,x.cellId).any{it.isNotBlank()})out.add(x)};return out}}}
}
