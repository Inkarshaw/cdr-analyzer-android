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
import java.time.LocalDateTime
import java.time.ZoneId
import org.apache.poi.ss.usermodel.WorkbookFactory

data class CdrRecord(val a:String,val b:String,val c:String,val d:String,val e:String)
data class Summary(val records:Int=0,val contacts:Int=0,val incoming:Int=0,val outgoing:Int=0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CdrApp() } }
    }

    @Composable
    private fun CdrApp() {
        var rows by remember { mutableStateOf<List<CdrRecord>>(emptyList()) }
        var fileName by remember { mutableStateOf("No CDR loaded") }
        var error by remember { mutableStateOf<String?>(null) }
        var tab by remember { mutableIntStateOf(0) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                rows = readWorkbook(contentResolver.openInputStream(uri)!!)
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "CDR file"
                error = null
            } catch (e: Exception) { error = e.message ?: "Unable to read file" }
        }
        val summary = remember(rows) {
            val contacts = rows.map { it.b }.filter { it.isNotBlank() }.distinct().size
            Summary(rows.size, contacts, rows.count { it.c.contains("in",true) }, rows.count { it.c.contains("out",true) })
        }
        Scaffold(topBar={ TopAppBar(title={Column{Text("CDR Analyzer"); Text("Native • Local analysis", style=MaterialTheme.typography.labelSmall)}}) }) { pad ->
            Column(Modifier.padding(pad).padding(12.dp).fillMaxSize()) {
                Button(onClick={picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","text/csv","*/*"))}, modifier=Modifier.fillMaxWidth()) { Text("Import CDR file") }
                Text(fileName, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(vertical=8.dp))
                error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Stat("Records",summary.records.toString(),Modifier.weight(1f)); Stat("Contacts",summary.contacts.toString(),Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Stat("Incoming",summary.incoming.toString(),Modifier.weight(1f)); Stat("Outgoing",summary.outgoing.toString(),Modifier.weight(1f))
                }
                ScrollableTabRow(tab, edgePadding=0.dp, modifier=Modifier.padding(top=12.dp)) {
                    listOf("Calls","Contacts","Devices","Towers","Movement","Notes").forEachIndexed { i,t -> Tab(tab==i,{tab=i},text={Text(t)}) }
                }
                when(tab){
                    0 -> RecordList(rows)
                    1 -> SimpleList(rows.groupingBy{it.b}.eachCount().entries.sortedByDescending{it.value}.map{"${it.key} — ${it.value} records"})
                    2 -> EmptyFeature("Device / SIM changes", "IMEI and IMSI change detection will appear here after column mapping.")
                    3 -> EmptyFeature("Tower analysis", "Cell ID, LAC and tower-frequency analysis.")
                    4 -> EmptyFeature("Movement", "Chronological tower movement and map-ready coordinates.")
                    else -> EmptyFeature("Investigation notes", "Case notes and tagged observations are stored locally on the device.")
                }
            }
        }
    }

    @Composable private fun Stat(label:String,value:String,m:Modifier){ Card(m){Column(Modifier.padding(12.dp)){Text(value,style=MaterialTheme.typography.headlineSmall);Text(label,style=MaterialTheme.typography.labelMedium)}} }
    @Composable private fun RecordList(rows:List<CdrRecord>){ if(rows.isEmpty()) EmptyFeature("Import a CDR", "Select XLSX/XLS from device storage. Analysis stays on the phone.") else LazyColumn(Modifier.fillMaxSize().padding(top=8.dp)){items(rows.take(500)){r-> ListItem(headlineContent={Text(r.b.ifBlank{"Unknown"})}, supportingContent={Text(listOf(r.a,r.c,r.d,r.e).filter{it.isNotBlank()}.joinToString(" • "))});HorizontalDivider()}} }
    @Composable private fun SimpleList(lines:List<String>){LazyColumn(Modifier.fillMaxSize().padding(top=8.dp)){items(lines){ListItem(headlineContent={Text(it)});HorizontalDivider()}}}
    @Composable private fun EmptyFeature(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.titleMedium);Text(body,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}}}

    private fun readWorkbook(input: InputStream): List<CdrRecord> {
        input.use { stream ->
            WorkbookFactory.create(stream).use { wb ->
                val sheet=wb.getSheetAt(0); val out=mutableListOf<CdrRecord>()
                for(i in 1..sheet.lastRowNum){ val r=sheet.getRow(i)?:continue; fun v(n:Int)=r.getCell(n)?.toString()?.trim().orEmpty(); if((0..4).all{v(it).isBlank()})continue; out += CdrRecord(v(0),v(1),v(2),v(3),v(4)) }
                return out
            }
        }
    }
}
