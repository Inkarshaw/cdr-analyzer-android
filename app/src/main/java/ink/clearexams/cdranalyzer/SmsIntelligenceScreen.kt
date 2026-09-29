package ink.clearexams.cdranalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SmsIntelligenceScreen(rows:List<CdrRecord>) {
    val context=LocalContext.current
    val store=remember(context){SmsSenderStore(context)}
    var mappings by remember{mutableStateOf(store.all())}
    var search by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("")}
    var unusualOnly by remember{mutableStateOf(false)}
    var firstOnly by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<SmsIntelEvent?>(null)}
    var editSender by remember{mutableStateOf<String?>(null)}
    val summary=remember(rows,mappings){SmsIntelligence.build(rows,mappings)}
    val categories=summary.categories.map{it.first}
    val filtered=summary.events.filter{e->
        (search.isBlank()||listOf(e.sender,e.label,e.category,e.record.number).any{it.contains(search,true)})&&
        (category.isBlank()||e.category==category)&&(!unusualOnly||e.unusualTime)&&(!firstOnly||e.firstObserved)
    }
    editSender?.let{sender->
        SmsSenderEditDialog(sender,mappings[sender.uppercase()],onDismiss={editSender=null}){label,cat->
            store.save(sender,label,cat);mappings=store.all();editSender=null
        }
    }
    selected?.let{event->SmsContextDialog(rows,event){selected=null}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("SMS Sender Intelligence",style=MaterialTheme.typography.titleLarge)}
        item{Text("Sender-ID and timing metadata only. A sender label does not prove an order, payment, login, booking or other underlying action.",style=MaterialTheme.typography.bodySmall)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            SmsMetric("Events",summary.events.size.toString(),Modifier.weight(1f));SmsMetric("Senders",summary.senders.size.toString(),Modifier.weight(1f));SmsMetric("Bursts",summary.bursts.size.toString(),Modifier.weight(1f))
        }}
        item{OutlinedTextField(search,{search=it},label={Text("Search sender / brand / subject")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            item{FilterChip(category.isBlank(),{category=""},{Text("All categories")})}
            items(categories){c->FilterChip(category==c,{category=c},{Text(c)})}
        }}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(unusualOnly,{unusualOnly=!unusualOnly},{Text("Unusual time")});FilterChip(firstOnly,{firstOnly=!firstOnly},{Text("First observed")})
        }}
        if(summary.unknownSenders.isNotEmpty()){
            item{Text("Unknown / unclassified sender review",style=MaterialTheme.typography.titleMedium)}
            items(summary.unknownSenders.take(20)){u->
                ListItem(headlineContent={Text(u.first)},supportingContent={Text("${u.second} event(s) • tap to classify")},modifier=Modifier.clickable{editSender=u.first});HorizontalDivider()
            }
        }
        if(summary.bursts.isNotEmpty()){
            item{Text("SMS bursts",style=MaterialTheme.typography.titleMedium)}
            items(summary.bursts.take(20)){b->
                ListItem(headlineContent={Text("${b.events} events • ${b.senders} sender(s)")},supportingContent={Text("${b.start} → ${b.end}\n${b.categories.joinToString()}")});HorizontalDivider()
            }
        }
        item{Text("Chronological SMS metadata",style=MaterialTheme.typography.titleMedium)}
        if(filtered.isEmpty())item{Text("No SMS metadata rows match the selected review filters.")}
        else items(filtered.take(1000)){e->
            ListItem(
                headlineContent={Text(e.label)},
                supportingContent={Text("${e.record.dateTime} • ${e.sender} • ${e.category} • ${e.recognition}${if(e.unusualTime)" • unusual time" else ""}")},
                modifier=Modifier.clickable{selected=e}
            );HorizontalDivider()
        }
    }
}

@Composable private fun SmsMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,tonalElevation=1.dp,shape=MaterialTheme.shapes.small){Column(Modifier.padding(8.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}}

@Composable private fun SmsSenderEditDialog(sender:String,current:SmsSenderMapping?,onDismiss:()->Unit,onSave:(String,String)->Unit){
    var label by remember(sender){mutableStateOf(current?.label.orEmpty())};var category by remember(sender){mutableStateOf(current?.category?:"Other / Unclassified")};var expanded by remember{mutableStateOf(false)}
    val categories=listOf("Banking","Payments / Wallet","Food Delivery","Ride-hailing / Transport","SIM / Telecom","E-commerce","Logistics / Courier","Travel / Booking","Healthcare","Insurance","Education","Utilities","Internet / Social","Government / Public Service","Other / Unclassified")
    AlertDialog(onDismissRequest=onDismiss,title={Text("Sender mapping: $sender")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(label,{label=it},label={Text("Display label")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Box{OutlinedButton({expanded=true},Modifier.fillMaxWidth()){Text(category)};DropdownMenu(expanded,{expanded=false}){categories.forEach{c->DropdownMenuItem(text={Text(c)},onClick={category=c;expanded=false})}}}
    }},confirmButton={Button({onSave(label.ifBlank{sender},category)}){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

@Composable private fun SmsContextDialog(rows:List<CdrRecord>,event:SmsIntelEvent,onDismiss:()->Unit){
    var minutes by remember{mutableIntStateOf(30)}
    val contextRows=remember(rows,event,minutes){SmsIntelligence.nearbyContext(rows,event,minutes)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${event.label} context")},text={Column{
        Text("${event.record.dateTime} • ${event.sender}",style=MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(vertical=6.dp)){items(listOf(5,15,30,60,360)){m->FilterChip(minutes==m,{minutes=m},{Text(if(m<60)"±${m}m" else "±${m/60}h")})}}
        Text("${contextRows.size} nearby CDR event(s)",style=MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.heightIn(max=450.dp)){items(contextRows.take(500)){r->ListItem(headlineContent={Text(r.dateTime.ifBlank{"Time unavailable"})},supportingContent={Text("${r.number} ↔ ${r.otherParty} • ${r.direction} • ${if(r.cellId.isBlank())"tower unavailable" else "${r.lac}/${r.cellId}"}")});HorizontalDivider()}}
    }},confirmButton={TextButton(onDismiss){Text("Back")}})
}
