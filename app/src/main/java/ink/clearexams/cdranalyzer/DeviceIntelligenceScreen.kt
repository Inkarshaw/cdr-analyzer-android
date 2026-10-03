package ink.clearexams.cdranalyzer

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun DeviceIntelligenceScreen(rows:List<CdrRecord>){
    val context=LocalContext.current
    val store=remember(context){TacDatabaseStore(context)}
    var tacCache by remember{mutableStateOf(store.all())}
    var status by remember{mutableStateOf("")}
    val result=remember(rows,tacCache){DeviceIntelligence.build(rows,tacCache)}
    val remoteLookupKey=remember(rows){rows.map{it.imei.filter(Char::isDigit).take(8)}.filter{it.length==8}.distinct().sorted().joinToString(",")}
    LaunchedEffect(remoteLookupKey){
        val missing=rows.map{it.imei.filter(Char::isDigit).take(8)}.filter{it.length==8&&!tacCache.containsKey(it)}.distinct()
        if(missing.isNotEmpty()){
            status="Checking ${missing.size} TAC${if(missing.size==1)"" else "s"}…"
            runCatching{TacDatabaseImporter.fetchRemoteMatches(rows,tacCache)}
                .onSuccess{matches->
                    if(matches.isNotEmpty()){store.save(matches);tacCache=store.all();status="Remote TAC matches: ${matches.size}"}
                    else status="No remote match for ${missing.size} TAC${if(missing.size==1)"" else "s"}"
                }
                .onFailure{status="Remote TAC source unavailable"}
        }
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)runCatching{TacDatabaseImporter.parse(context.contentResolver,uri)}.onSuccess{entries->store.save(entries);tacCache=store.all();status="Imported ${entries.size} TAC mapping(s) • cache ${tacCache.size}"}.onFailure{status="TAC import failed: ${it.message?:"Unknown error"}"}
    }
    fun exportCache(){
        runCatching{
            val dir=File(context.cacheDir,"reports").apply{mkdirs()};val file=File(dir,"tac_cache.json");val arr=JSONArray();tacCache.values.sortedBy{it.tac}.forEach{e->arr.put(JSONObject().apply{put("tac",e.tac);put("manufacturer",e.manufacturer);put("model",e.model);put("deviceType",e.deviceType);put("os",e.os)})};file.writeText(arr.toString(2));val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);val intent=Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};context.startActivity(Intent.createChooser(intent,"Export TAC cache"));status="TAC cache exported: ${tacCache.size} entries"
        }.onFailure{status="TAC export failed: ${it.message?:"Unknown error"}"}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("SIM / Subscriber / Device Identity",style=MaterialTheme.typography.titleLarge)}
        item{Text("Identifier relationships are review indicators. SIM replacement, handset replacement/repair, dual-SIM use and data quality can also explain changes.",style=MaterialTheme.typography.bodySmall)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Button({launcher.launch(arrayOf("text/csv","application/json","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","*/*"))},Modifier.weight(1f)){Text("Import TAC DB")};OutlinedButton(::exportCache,Modifier.weight(1f),enabled=tacCache.isNotEmpty()){Text("Export TAC")};OutlinedButton({store.clear();tacCache=emptyMap();status="TAC cache cleared"},Modifier.weight(1f),enabled=tacCache.isNotEmpty()){Text("Clear")}}}
        item{Text("TAC cache: ${tacCache.size} entries${if(status.isBlank())"" else " • $status"}",style=MaterialTheme.typography.bodySmall)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DeviceMetric("IMEIs",result.imeis.size.toString(),Modifier.weight(1f));DeviceMetric("Changes",result.changes.size.toString(),Modifier.weight(1f));DeviceMetric("Cross IMSI",result.crossImsi.size.toString(),Modifier.weight(1f))}}
        item{Text("Resolved device models",style=MaterialTheme.typography.titleMedium)}
        if(result.imeis.none{!it.model.isNullOrBlank()||!it.manufacturer.isNullOrBlank()})item{Text("No imported TAC mapping matches the loaded IMEIs.",style=MaterialTheme.typography.bodySmall)}else items(result.imeis.filter{!it.model.isNullOrBlank()||!it.manufacturer.isNullOrBlank()}.take(200)){i->ListItem(headlineContent={Text(listOfNotNull(i.manufacturer,i.model).filter{!it.isNullOrBlank()}.joinToString(" ").ifBlank{"Resolved device"})},supportingContent={Text("IMEI ${i.imei} • TAC ${i.tac}")});HorizontalDivider()}
        item{Text("TAC / IMEI structure",style=MaterialTheme.typography.titleMedium)}
        items(result.imeis.take(300)){i->ListItem(headlineContent={Text(i.imei)},supportingContent={Text("TAC ${i.tac} • serial ${i.serial.ifBlank{"—"}} • check ${i.check.ifBlank{"—"}} • ${i.validCheckDigit?.let{if(it)"valid check digit" else "invalid check digit"}?:"check not evaluated"}")});HorizontalDivider()}
        item{Text("MSISDN ↔ IMSI ↔ IMEI relationships",style=MaterialTheme.typography.titleMedium)}
        items(result.relationships.take(300)){r->ListItem(headlineContent={Text(r.msisdn)},supportingContent={Text("${r.records} record(s)\nIMSI: ${r.imsis.joinToString().ifBlank{"—"}}\nIMEI: ${r.imeis.joinToString().ifBlank{"—"}}")});HorizontalDivider()}
        item{Text("Identifier change timeline",style=MaterialTheme.typography.titleMedium)}
        if(result.changes.isEmpty())item{Text("No sequential IMEI/IMSI change detected in the current rows.")}else items(result.changes.take(500)){e->ListItem(headlineContent={Text(e.at.ifBlank{"Time unavailable"})},supportingContent={Text("${e.msisdn}\nIMSI ${e.oldImsi.ifBlank{"—"}} → ${e.newImsi.ifBlank{"—"}}\nIMEI ${e.oldImei.ifBlank{"—"}} → ${e.newImei.ifBlank{"—"}}")});HorizontalDivider()}
        item{Text("IMSI cross-CDR linkage",style=MaterialTheme.typography.titleMedium)}
        if(result.crossImsi.isEmpty())item{Text("No IMSI is associated with multiple loaded subjects.")}else items(result.crossImsi){e->ListItem(headlineContent={Text(e.imsi)},supportingContent={Text("${e.records} record(s) • subjects: ${e.subjects.joinToString()}")});HorizontalDivider()}
        item{Text("IMEI with multiple IMSIs",style=MaterialTheme.typography.titleMedium)}
        if(result.multiImsi.isEmpty())item{Text("No IMEI is associated with multiple IMSIs.")}else items(result.multiImsi){e->ListItem(headlineContent={Text(e.imei)},supportingContent={Text("${e.records} record(s) • IMSI: ${e.imsis.joinToString()}")});HorizontalDivider()}
        item{Text("IMEI ↔ IMSI relationship matrix",style=MaterialTheme.typography.titleMedium)}
        val matrix=rows.filter{it.imei.isNotBlank()&&it.imsi.isNotBlank()}.groupBy{it.imei}.map{(imei,x)->Triple(imei,x.map{it.imsi}.distinct().sorted(),x.size)}.sortedByDescending{it.third}
        if(matrix.isEmpty())item{Text("No row contains both IMEI and IMSI.")}else items(matrix.take(300)){m->ListItem(headlineContent={Text(m.first)},supportingContent={Text("${m.third} record(s) • IMSI: ${m.second.joinToString()}")});HorizontalDivider()}
    }
}
@Composable private fun DeviceMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}
