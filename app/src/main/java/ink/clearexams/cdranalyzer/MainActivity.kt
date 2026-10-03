package ink.clearexams.cdranalyzer

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

data class CdrRecord(val number:String="",val otherParty:String="",val direction:String="",val dateTime:String="",val duration:String="",val imei:String="",val imsi:String="",val cellId:String="",val lac:String="",val latitude:String="",val longitude:String="",val sourceFile:String="",val sourceSheet:String="",val rawRow:Map<String,String> = emptyMap(),val towerAddress:String="",val mainCity:String="",val subCity:String="",val roaming:String="",val provider:String="",val operator:String="")
data class Summary(val records:Int=0,val contacts:Int=0,val incoming:Int=0,val outgoing:Int=0)
data class ColumnMap(val number:Int=-1,val other:Int=-1,val direction:Int=-1,val dateTime:Int=-1,val duration:Int=-1,val imei:Int=-1,val imsi:Int=-1,val cell:Int=-1,val lac:Int=-1,val latitude:Int=-1,val longitude:Int=-1)
data class DeviceChange(val at:String,val oldImei:String,val newImei:String,val oldImsi:String,val newImsi:String)
data class ContactTag(val name:String="",val relation:String="")
data class TowerVisit(val tower:String,val first:String,val last:String,val records:Int)
data class TowerTransition(val at:String,val from:String,val to:String)
data class GeoPoint(val at:String,val tower:String,val latitude:Double,val longitude:Double)

class MainActivity : ComponentActivity() {
 private val prefs by lazy { getSharedPreferences("contact_tags", MODE_PRIVATE) }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{NexusTheme{CdrApp()}}}
 private fun loadTags():Map<String,ContactTag>{val o=mutableMapOf<String,ContactTag>();prefs.all.forEach{(k,v)->val r=v as? String?:return@forEach;val p=r.split("|",limit=2);o[k]=ContactTag(p.getOrElse(0){""},p.getOrElse(1){""})};return o}
 private fun saveTag(n:String,t:ContactTag){prefs.edit().putString(n,"${t.name}|${t.relation}").apply()}

 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun CdrApp(){
  var rows by remember{mutableStateOf<List<CdrRecord>>(emptyList())}
  var fileName by remember{mutableStateOf("No CDR loaded")}
  var error by remember{mutableStateOf<String?>(null)}
  var tab by remember{mutableIntStateOf(0)}
  var search by remember{mutableStateOf("")}
  var tags by remember{mutableStateOf(loadTags())}
  var editNumber by remember{mutableStateOf<String?>(null)}
  var duplicateSafe by remember{mutableStateOf(true)}
  var cdrFilters by remember{mutableStateOf(CdrFilters())}
  var filtersOpen by remember{mutableStateOf(false)}
  var toolsOpen by remember{mutableStateOf(false)}
  var auxiliaryView by remember{mutableStateOf<String?>(null)}
  var draftSearch by remember{mutableStateOf("")}
  var draftFilters by remember{mutableStateOf(CdrFilters())}
  var draftDuplicateSafe by remember{mutableStateOf(true)}
  var importing by remember{mutableStateOf(false)}
  var importCurrent by remember{mutableIntStateOf(0)}
  var importTotal by remember{mutableIntStateOf(0)}
  var importFile by remember{mutableStateOf("")}
  val importScope=rememberCoroutineScope()
  val appContext=androidx.compose.ui.platform.LocalContext.current
  val caseStore=remember(appContext){CaseWorkspaceStore(appContext)}
  val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris:List<Uri>->
   if(uris.isNotEmpty()){
    importScope.launch{
     importing=true;importTotal=uris.size;importCurrent=0;importFile="Preparing CDR…"
     val imported=mutableListOf<CdrRecord>();val sourceNames=mutableListOf<String>();val warnings=mutableListOf<String>()
     uris.forEachIndexed{index,uri->
      importCurrent=index+1;importFile=uri.lastPathSegment?:"CDR file"
      runCatching{contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
      val parsed=runCatching{withContext(Dispatchers.IO){CdrImportParser.parse(contentResolver,uri)}}
      parsed.onSuccess{result->imported+=result.records;sourceNames+=result.sources.map{it.fileName}.distinct();warnings+=result.warnings}
       .onFailure{warnings+="${uri.lastPathSegment?:"CDR file"}: ${it.message?:"Import failed"}"}
     }
     rows=imported
     fileName=if(sourceNames.isEmpty())"${uris.size} selected file(s)" else "${sourceNames.distinct().size} file(s) • ${imported.size} records"
     error=warnings.takeIf{it.isNotEmpty()}?.joinToString("\n")
     cdrFilters=CdrFilters();draftFilters=CdrFilters();search="";draftSearch="";tab=0;auxiliaryView=null;filtersOpen=false
     importing=false;importFile=""
    }
   }
  }
  val analysisRows=remember(rows,duplicateSafe){if(duplicateSafe)duplicateSafeRows(rows) else rows}
  val advancedFiltered=remember(analysisRows,cdrFilters){applyCdrFilters(analysisRows,cdrFilters)}
  val filtered=remember(advancedFiltered,search,tags){if(search.isBlank())advancedFiltered else advancedFiltered.filter{r->val t=tags[r.otherParty]?:tags[r.number];listOf(r.number,r.otherParty,r.direction,r.dateTime,r.imei,r.imsi,r.cellId,r.lac,r.latitude,r.longitude,r.towerAddress,r.mainCity,r.subCity,r.provider,r.operator,r.sourceFile,t?.name.orEmpty(),t?.relation.orEmpty()).any{it.contains(search,true)}}}
  val summary=remember(filtered){Summary(filtered.size,filtered.map{it.otherParty}.filter{it.isNotBlank()}.distinct().size,filtered.count{normalizeDirection(it.direction)=="Incoming"},filtered.count{normalizeDirection(it.direction)=="Outgoing"})}
  val activeFilterCount=remember(search,cdrFilters){
   listOf(cdrFilters.aParty,cdrFilters.bParty,cdrFilters.dateFrom,cdrFilters.dateTo,cdrFilters.timeFrom,cdrFilters.timeTo,cdrFilters.eventType,cdrFilters.imei,cdrFilters.imsi,cdrFilters.cellId,cdrFilters.towerAddress,cdrFilters.city,cdrFilters.subCity,cdrFilters.roaming,cdrFilters.provider,cdrFilters.operator,cdrFilters.sourceFile,cdrFilters.minDuration,cdrFilters.maxDuration).count{it.isNotBlank()} +
    listOf(cdrFilters.callsOnly,cdrFilters.smsOnly,cdrFilters.nightOnly,cdrFilters.weekendOnly,cdrFilters.excludeServiceSenders).count{it} + if(search.isNotBlank())1 else 0
  }
  val primaryTabs=listOf("Dashboard","Records","Excel View","Contacts","Locations","Devices","SMS Intelligence","Incident Timeline","Patterns","Movement","Multi-number")

  if(filtersOpen){
   ModalBottomSheet(onDismissRequest={filtersOpen=false}){
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Filters",style=MaterialTheme.typography.titleLarge);Badge{Text(activeFilterCount.toString())}}
     Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
      Text("Upload CDR file(s)",style=MaterialTheme.typography.titleMedium)
      Text("Choose one or more .xlsx, .xls or .csv files. Likely CDR worksheets and recognized columns are mapped automatically.",style=MaterialTheme.typography.bodySmall)
      Button({picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","text/csv","text/comma-separated-values","*/*"))},Modifier.fillMaxWidth()){Text("Choose Files")}
      Text(fileName,style=MaterialTheme.typography.bodySmall)
      val loadedFiles=rows.map{it.sourceFile}.filter{it.isNotBlank()}.distinct()
      loadedFiles.take(8).forEach{source->
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text("• $source",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
        TextButton({
         rows=rows.filterNot{it.sourceFile==source}
         val remaining=rows.map{it.sourceFile}.filter{it.isNotBlank()}.distinct()
         fileName=if(remaining.isEmpty())"No CDR loaded" else "${remaining.size} file(s) • ${rows.size} records"
         search="";draftSearch="";cdrFilters=CdrFilters();draftFilters=CdrFilters()
        }){Text("Remove")}
       }
      }
      if(loadedFiles.size>8)Text("+${loadedFiles.size-8} more",style=MaterialTheme.typography.labelSmall)
      error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
      OutlinedButton({rows=emptyList();fileName="No CDR loaded";error=null;search="";draftSearch="";cdrFilters=CdrFilters();draftFilters=CdrFilters()},Modifier.fillMaxWidth(),enabled=rows.isNotEmpty()){Text("Clear Loaded CDRs")}
     }}
     if(rows.isNotEmpty()){
      Text("Quick filters",style=MaterialTheme.typography.titleMedium)
      OutlinedTextField(draftSearch,{draftSearch=it},label={Text("Any text")},placeholder={Text("Number, tower, IMEI, name…")},singleLine=true,modifier=Modifier.fillMaxWidth())
      AdvancedFiltersPanel(draftFilters,rows.map{it.sourceFile}.filter{it.isNotBlank()}.distinct().sorted()){draftFilters=it}
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
       OutlinedButton({draftSearch="";draftFilters=CdrFilters();draftDuplicateSafe=true},Modifier.weight(1f)){Text("Reset")}
       Button({search=draftSearch;cdrFilters=draftFilters;duplicateSafe=draftDuplicateSafe;filtersOpen=false},Modifier.weight(1f)){Text("Apply")}
      }
     }
     Spacer(Modifier.height(18.dp))
    }
   }
  }

  if(importing){
   AlertDialog(
    onDismissRequest={},
    confirmButton={},
    title={Text("Loading ${importCurrent.coerceAtLeast(1)} of ${importTotal.coerceAtLeast(1)}")},
    text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator();Text(importFile.ifBlank{"Preparing CDR…"},style=MaterialTheme.typography.bodySmall)}}
   )
  }

  Scaffold(
   containerColor=MaterialTheme.colorScheme.background,
   topBar={TopAppBar(
    title={Column{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("CDR Case Analyzer");Badge{Text("v60")}};Text("NEXUS intelligence console • local analysis • XLSX / XLS / CSV",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
    actions={if(filtered.isNotEmpty())TextButton({CdrPrintReport.print(appContext,filtered,summary)}){Text("Print / PDF")}}
   )}
  ){p->
   Column(Modifier.padding(p).padding(horizontal=10.dp,vertical=8.dp).fillMaxSize()){
    if(rows.isNotEmpty()){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedButton({draftSearch=search;draftFilters=cdrFilters;draftDuplicateSafe=duplicateSafe;filtersOpen=true},Modifier.weight(1f)){Text("☰ Filters");Spacer(Modifier.width(6.dp));Badge{Text(activeFilterCount.toString())}}
      Box(Modifier.weight(1f)){
       OutlinedButton({toolsOpen=true},Modifier.fillMaxWidth()){Text("Tools")}
       DropdownMenu(toolsOpen,{toolsOpen=false}){
        DropdownMenuItem({Text("Cases / Workspace")},{toolsOpen=false;auxiliaryView="Cases"})
       }
      }
     }
    }
    if(rows.isNotEmpty())Text("${filtered.size} filtered / ${analysisRows.size} analysis / ${rows.size} raw records",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=5.dp))
    if(auxiliaryView==null){
     if(rows.isEmpty()){
      Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
       Card(Modifier.fillMaxWidth().padding(horizontal=8.dp)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
         Text("Start CDR analysis",style=MaterialTheme.typography.headlineSmall)
         Text("Import one or more XLSX, XLS or CSV CDR files. Analysis stays local on this device.",style=MaterialTheme.typography.bodyMedium)
         Button({picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","text/csv","text/comma-separated-values","*/*"))},Modifier.fillMaxWidth()){Text("Import CDR Files")}
         OutlinedButton({auxiliaryView="Cases"},Modifier.fillMaxWidth()){Text("Open Cases / Restore Backup")}
         Text("No CDR is loaded yet. Dashboard statistics will appear after import.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
       }
      }
     }else{
      ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp,modifier=Modifier.fillMaxWidth()){primaryTabs.forEachIndexed{i,t->Tab(tab==i,{tab=i;auxiliaryView=null},text={Text(t)})}}
      Box(Modifier.fillMaxWidth().weight(1f).padding(top=6.dp)){
       when(tab){
        0->CdrDashboardScreen(filtered,tags){editNumber=it}
        1->SiteRecordsScreen(filtered,tags){editNumber=it}
        2->ExcelViewScreen(rows)
        3->ContactList(filtered,tags){editNumber=it}
        4->SiteLocationsScreen(filtered)
        5->DeviceIntelligenceScreen(filtered)
        6->SmsIntelligenceScreen(filtered)
        7->IncidentAnalysisScreen(filtered)
        8->PatternsScreen(filtered)
        9->MovementList(filtered)
        else->MultiNumberAnalysisScreen(filtered)
       }
      }
     }
    }else{
     Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({auxiliaryView=null}){Text("← Analyzer")};Text(auxiliaryView.orEmpty(),style=MaterialTheme.typography.titleMedium)}
     Box(Modifier.fillMaxWidth().weight(1f)){
      when(auxiliaryView){
       "Cases"->CaseWorkspaceScreen(caseStore,rows,fileName){loaded,name->rows=loaded;fileName=name;search="";cdrFilters=CdrFilters();draftFilters=CdrFilters();tab=0;auxiliaryView=null}
      }
     }
    }
   }
  }
  editNumber?.let{n->TagDialog(n,tags[n],{editNumber=null}){t->saveTag(n,t);tags=tags.toMutableMap().apply{put(n,t)};editNumber=null}}
 }
 @Composable private fun TagDialog(number:String,current:ContactTag?,onDismiss:()->Unit,onSave:(ContactTag)->Unit){var name by remember(number){mutableStateOf(current?.name.orEmpty())};var relation by remember(number){mutableStateOf(current?.relation?:"Other")};var custom by remember(number){mutableStateOf(if(current?.relation in listOf("Suspect","Victim","Witness","Associate","Family"))"" else current?.relation.orEmpty())};var expanded by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text("Tag contact")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(number);OutlinedTextField(name,{name=it},label={Text("Name / known identity")},singleLine=true,modifier=Modifier.fillMaxWidth());Box{OutlinedButton({expanded=true},Modifier.fillMaxWidth()){Text("Relation: $relation")};DropdownMenu(expanded,{expanded=false}){listOf("Suspect","Victim","Witness","Associate","Family","Other").forEach{o->DropdownMenuItem({Text(o)},{relation=o;expanded=false})}}};if(relation=="Other")OutlinedTextField(custom,{custom=it},label={Text("Custom relation / description")},singleLine=true,modifier=Modifier.fillMaxWidth())}},confirmButton={Button({onSave(ContactTag(name.trim(),if(relation=="Other")custom.trim().ifBlank{"Other"}else relation))}){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})}
 @Composable private fun Stat(l:String,v:String,m:Modifier){Card(m){Column(Modifier.padding(10.dp)){Text(v,style=MaterialTheme.typography.headlineSmall);Text(l,style=MaterialTheme.typography.labelMedium)}}}
 private fun display(n:String,t:Map<String,ContactTag>):String{val x=t[n];return if(x!=null&&x.name.isNotBlank())"${x.name} ($n)" else n}
 @Composable private fun RecordList(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onTag:(String)->Unit){if(rows.isEmpty()){EmptyFeature("Import or search CDR","No matching records.");return};LazyColumn(Modifier.fillMaxSize()){items(rows.take(1000)){r->val n=r.otherParty.ifBlank{r.number};val d=mutableListOf<String>();tags[n]?.relation?.takeIf{it.isNotBlank()}?.let(d::add);normalizeDirection(r.direction).takeIf{it.isNotBlank()}?.let(d::add);r.dateTime.takeIf{it.isNotBlank()}?.let(d::add);r.duration.takeIf{it.isNotBlank()}?.let{d.add("${it}s")};ListItem(headlineContent={Text(display(n,tags).ifBlank{"Unknown"})},supportingContent={Text(d.joinToString(" • "))},modifier=Modifier.clickable{if(n.isNotBlank())onTag(n)});HorizontalDivider()}}}
 @Composable private fun ContactList(rows:List<CdrRecord>,tags:Map<String,ContactTag>,onTag:(String)->Unit){val context=androidx.compose.ui.platform.LocalContext.current;val c=rows.filter{it.otherParty.isNotBlank()}.groupingBy{it.otherParty}.eachCount().entries.sortedByDescending{it.value};LazyColumn(Modifier.fillMaxSize()){items(c){e->val d=mutableListOf<String>();tags[e.key]?.relation?.takeIf{it.isNotBlank()}?.let(d::add);d.add("${e.value} interactions");ListItem(headlineContent={Text(display(e.key,tags))},supportingContent={Text(d.joinToString(" • "))},trailingContent={TextButton(onClick={val url="https://clearexams.ink/policedocuments/cdr_request_generator.html?from=cdr-analyzer&number=${Uri.encode(e.key)}";context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}){Text("Request")}},modifier=Modifier.clickable{onTag(e.key)});HorizontalDivider()}}}
 @Composable private fun DeviceList(rows:List<CdrRecord>){val imeis=rows.filter{it.imei.isNotBlank()}.groupBy{it.imei};if(imeis.isEmpty()){EmptyFeature("Device / SIM changes","No IMEI column was detected in this file.");return};val changes=detectDeviceChanges(rows);LazyColumn(Modifier.fillMaxSize()){item{Text("Detected changes: ${changes.size}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};items(changes){c->ListItem(headlineContent={Text(c.at.ifBlank{"Time unavailable"})},supportingContent={Text("IMEI: ${c.oldImei.ifBlank{"—"}} → ${c.newImei.ifBlank{"—"}}\nIMSI: ${c.oldImsi.ifBlank{"—"}} → ${c.newImsi.ifBlank{"—"}}")});HorizontalDivider()};item{Text("Device usage",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};items(imeis.entries.toList()){e->val o=e.value.filter{it.dateTime.isNotBlank()}.sortedBy{it.dateTime};val s=e.value.map{it.imsi}.filter{it.isNotBlank()}.distinct();ListItem(headlineContent={Text("IMEI ${e.key}")},supportingContent={Text("First use: ${o.firstOrNull()?.dateTime?:"Unknown"}\nLast use: ${o.lastOrNull()?.dateTime?:"Unknown"}\nIMSI: ${s.joinToString()}\nRecords: ${e.value.size}")});HorizontalDivider()}}}
 @Composable private fun TowerList(rows:List<CdrRecord>){val t=rows.filter{it.cellId.isNotBlank()}.groupingBy{"${it.lac}/${it.cellId}"}.eachCount().entries.sortedByDescending{it.value};if(t.isEmpty())EmptyFeature("Tower analysis","No Cell ID column was detected.")else SimpleList(t.map{"LAC/Cell ${it.key} — ${it.value} records"})}

 @Composable private fun MovementList(rows:List<CdrRecord>){
  val subjects=remember(rows){rows.map{it.number}.filter{it.isNotBlank()}.distinct().sorted()}
  var subject by remember(subjects){mutableStateOf(subjects.firstOrNull().orEmpty())}
  val scoped=remember(rows,subject){if(subject.isBlank())rows else rows.filter{it.number==subject}}
  val towerRows=scoped.filter{it.cellId.isNotBlank()}
  if(towerRows.isEmpty()){EmptyFeature("Movement","No tower records were detected for the selected CDR subject.");return}
  val visits=towerVisits(towerRows);val transitions=towerTransitions(towerRows);val points=geoPoints(towerRows)
  LazyColumn(Modifier.fillMaxSize()){
   if(subjects.isNotEmpty())item{
    Text("CDR / subject",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
    LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
     if(subjects.size>1)item{FilterChip(subject.isBlank(),{subject=""},{Text("All")})}
     items(subjects){s->FilterChip(subject==s,{subject=s},{Text(s)})}
    }
   }
   item{Text("Movement summary",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))}
   item{Text("Unique towers: ${visits.size} • Tower changes: ${transitions.size} • Mappable records: ${points.size}",modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))}
   if(points.isEmpty()){item{Text("Map unavailable: no valid latitude/longitude columns were found in this CDR.",modifier=Modifier.padding(12.dp))}}else{
    item{Text("CDR movement map",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};item{CdrMovementMap(points=points,modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp))};item{Text("Map uses only coordinates contained in the imported CDR. Tap a tower marker for tower/time details.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))};item{Text("Coordinate trail",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};items(points.take(200)){g->ListItem(headlineContent={Text(g.tower)},supportingContent={Text("${g.latitude}, ${g.longitude}\n${g.at.ifBlank{"Time unavailable"}}")});HorizontalDivider()}}
   item{Text("Frequent / repeated locations",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};items(visits.sortedByDescending{it.records}.take(20)){v->ListItem(headlineContent={Text(v.tower)},supportingContent={Text("Records: ${v.records}\nFirst: ${v.first.ifBlank{"Unknown"}}\nLast: ${v.last.ifBlank{"Unknown"}}")});HorizontalDivider()};item{Text("Chronological tower changes",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))};if(transitions.isEmpty())item{Text("No tower-to-tower change detected.",modifier=Modifier.padding(12.dp))}else items(transitions.take(500)){m->ListItem(headlineContent={Text("${m.from} → ${m.to}")},supportingContent={Text(m.at.ifBlank{"Time unavailable"})});HorizontalDivider()}
  }
 }
 @Composable private fun SimpleList(x:List<String>){LazyColumn(Modifier.fillMaxSize()){items(x){ListItem(headlineContent={Text(it)});HorizontalDivider()}}}
 @Composable private fun EmptyFeature(t:String,b:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(t,style=MaterialTheme.typography.titleMedium);Text(b,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}}}
 private fun towerKey(r:CdrRecord)=if(r.lac.isBlank())"Cell ${r.cellId}" else "LAC ${r.lac} / Cell ${r.cellId}"
 private fun geoPoints(rows:List<CdrRecord>):List<GeoPoint>{return rows.mapNotNull{r->val lat=r.latitude.trim().replace(",","").toDoubleOrNull();val lon=r.longitude.trim().replace(",","").toDoubleOrNull();if(lat!=null&&lon!=null&&lat in -90.0..90.0&&lon in -180.0..180.0)GeoPoint(r.dateTime,towerKey(r),lat,lon)else null}.sortedBy{it.at}}
 private fun towerVisits(rows:List<CdrRecord>):List<TowerVisit>{return rows.groupBy{towerKey(it)}.map{(tower,x)->val ordered=x.sortedBy{it.dateTime};TowerVisit(tower,ordered.firstOrNull()?.dateTime.orEmpty(),ordered.lastOrNull()?.dateTime.orEmpty(),x.size)}}
 private fun towerTransitions(rows:List<CdrRecord>):List<TowerTransition>{val ordered=rows.sortedBy{it.dateTime};val out=mutableListOf<TowerTransition>();var previous:String?=null;for(r in ordered){val current=towerKey(r);val old=previous;if(old!=null&&old!=current)out.add(TowerTransition(r.dateTime,old,current));previous=current};return out}
 private fun detectDeviceChanges(rows:List<CdrRecord>):List<DeviceChange>{val o=rows.filter{it.imei.isNotBlank()||it.imsi.isNotBlank()}.sortedBy{it.dateTime};val x=mutableListOf<DeviceChange>();var p:CdrRecord?=null;for(c in o){val q=p;if(q!=null&&((q.imei.isNotBlank()&&c.imei.isNotBlank()&&q.imei!=c.imei)||(q.imsi.isNotBlank()&&c.imsi.isNotBlank()&&q.imsi!=c.imsi)))x.add(DeviceChange(c.dateTime,q.imei,c.imei,q.imsi,c.imsi));p=c};return x}
 private fun normalizeDirection(v:String):String{val s=v.lowercase();return when{s.contains("incoming")||s=="in"||s.contains("mti")->"Incoming";s.contains("outgoing")||s=="out"||s.contains("moc")->"Outgoing";s.contains("sms")->"SMS";else->v}}
 private fun norm(v:String)=v.lowercase().replace(" ","").replace("_","").replace("-","").replace(".","")
 private fun find(h:List<String>,vararg n:String):Int{val x=h.map(::norm);return x.indexOfFirst{c->n.any{c.contains(norm(it))}}}

 private fun detect(h:List<String>):ColumnMap {
  return ColumnMap(
   number=find(h,"callingnumber","msisdn","anumber","subscriber"),
   other=find(h,"callednumber","otherparty","bnumber","diallednumber","connectednumber"),
   direction=find(h,"calltype","direction","type"),
   dateTime=find(h,"datetime","calltime","starttime","date"),
   duration=find(h,"duration","callduration"),
   imei=find(h,"imei"),
   imsi=find(h,"imsi"),
   cell=find(h,"cellid","celltower","cgi"),
   lac=find(h,"lac","locationareacode"),
   latitude=find(h,"towerlatitude","celllatitude","latitude","lat"),
   longitude=find(h,"towerlongitude","celllongitude","longitude","lng","lon","long")
  )
 }

 private fun readWorkbook(input:InputStream):List<CdrRecord> {
  input.use { stream ->
   WorkbookFactory.create(stream).use { workbook ->
    val sheet=workbook.getSheetAt(0)
    val formatter=DataFormatter()
    val header=sheet.getRow(0) ?: return emptyList()
    val headers=(0 until header.lastCellNum).map { index ->
     formatter.formatCellValue(header.getCell(index)).trim()
    }
    val map=detect(headers)
    val output=mutableListOf<CdrRecord>()

    fun cell(row:Row,index:Int):String {
     return if(index<0) "" else formatter.formatCellValue(row.getCell(index)).trim()
    }

    for(index in 1..sheet.lastRowNum) {
     val row=sheet.getRow(index) ?: continue
     val record=CdrRecord(
      number=cell(row,map.number),
      otherParty=cell(row,map.other),
      direction=cell(row,map.direction),
      dateTime=cell(row,map.dateTime),
      duration=cell(row,map.duration),
      imei=cell(row,map.imei),
      imsi=cell(row,map.imsi),
      cellId=cell(row,map.cell),
      lac=cell(row,map.lac),
      latitude=cell(row,map.latitude),
      longitude=cell(row,map.longitude)
     )
     val hasData=listOf(record.number,record.otherParty,record.dateTime,record.imei,record.cellId,record.latitude,record.longitude).any { it.isNotBlank() }
     if(hasData) output.add(record)
    }
    return output
   }
  }
 }
}
