package ink.clearexams.cdranalyzer

import android.content.Context

data class SmsSenderMapping(val rawSender:String,val label:String,val category:String)

class SmsSenderStore(context: Context) {
    private val prefs = context.getSharedPreferences("sms_sender_dictionary", Context.MODE_PRIVATE)
    fun all(): Map<String,SmsSenderMapping> = prefs.all.mapNotNull { (key,value) ->
        val raw=value as? String ?: return@mapNotNull null
        val parts=raw.split("|~|",limit=2)
        if(parts.size<2) null else key to SmsSenderMapping(key,parts[0],parts[1])
    }.toMap()
    fun save(rawSender:String,label:String,category:String){
        val key=rawSender.trim().uppercase()
        if(key.isNotBlank()) prefs.edit().putString(key,"${label.trim()}|~|${category.trim()}").apply()
    }
    fun remove(rawSender:String){prefs.edit().remove(rawSender.trim().uppercase()).apply()}
}

data class SmsIntelEvent(
    val record:CdrRecord,
    val sender:String,
    val label:String,
    val category:String,
    val recognition:String,
    val timestamp:Long?,
    val unusualTime:Boolean,
    val firstObserved:Boolean
)
data class SmsBurst(val start:String,val end:String,val events:Int,val senders:Int,val categories:List<String>)
data class SmsSummary(
    val events:List<SmsIntelEvent>,
    val categories:List<Pair<String,Int>>,
    val senders:List<Pair<String,Int>>,
    val unknownSenders:List<Pair<String,Int>>,
    val bursts:List<SmsBurst>
)

object SmsIntelligence {
    private val known = mapOf(
        "SWIGGY" to ("Swiggy" to "Food Delivery"),
        "ZOMATO" to ("Zomato" to "Food Delivery"),
        "AMAZON" to ("Amazon" to "E-commerce"),
        "FLPKRT" to ("Flipkart" to "E-commerce"),
        "FLIPKART" to ("Flipkart" to "E-commerce"),
        "UBER" to ("Uber" to "Ride-hailing / Transport"),
        "OLA" to ("Ola" to "Ride-hailing / Transport"),
        "IRCTC" to ("IRCTC" to "Travel / Booking"),
        "SBI" to ("State Bank of India" to "Banking"),
        "HDFCBK" to ("HDFC Bank" to "Banking"),
        "ICICIB" to ("ICICI Bank" to "Banking"),
        "AXISBK" to ("Axis Bank" to "Banking"),
        "PAYTM" to ("Paytm" to "Payments / Wallet"),
        "PHONEPE" to ("PhonePe" to "Payments / Wallet"),
        "GPAY" to ("Google Pay" to "Payments / Wallet"),
        "AIRTEL" to ("Airtel" to "SIM / Telecom"),
        "JIO" to ("Jio" to "SIM / Telecom"),
        "BSNL" to ("BSNL" to "SIM / Telecom"),
        "INDPOST" to ("India Post" to "Logistics / Courier")
    )

    fun build(rows:List<CdrRecord>,manual:Map<String,SmsSenderMapping>,burstGapMinutes:Int=15,burstMinimum:Int=3):SmsSummary{
        val candidates=rows.mapNotNull{r->
            val sender=r.otherParty.trim().ifBlank{r.number.trim()}
            val explicit=normalizedSmsType(r.direction)
            val senderLike=sender.matches(Regex("[A-Za-z]{2,3}-[A-Za-z0-9]{3,20}"))
            if(!explicit&&!senderLike)return@mapNotNull null
            val key=sender.uppercase()
            val token=brandToken(sender)
            val override=manual[key]
            val builtin=known.entries.firstOrNull{token.contains(it.key)}?.value
            val label=override?.label ?: builtin?.first ?: token.ifBlank{sender}
            val category=override?.category ?: builtin?.second ?: "Other / Unclassified"
            val recognition=when{override!=null->"Manual";builtin!=null->"Recognized";senderLike->"Probable";else->"Unclassified"}
            val ts=parseCdrTime(r.dateTime)
            SmsIntelEvent(r,sender,label,category,recognition,ts,isUnusual(ts),false)
        }.sortedWith(compareBy<SmsIntelEvent>{it.timestamp?:Long.MAX_VALUE}.thenBy{it.record.dateTime})
        val seen=mutableSetOf<String>()
        val events=candidates.map{e->val first=seen.add("${e.record.number}|${e.sender.uppercase()}");e.copy(firstObserved=first)}
        val categories=events.groupingBy{it.category}.eachCount().entries.sortedByDescending{it.value}.map{it.key to it.value}
        val senders=events.groupingBy{it.label}.eachCount().entries.sortedByDescending{it.value}.map{it.key to it.value}
        val unknown=events.filter{it.category=="Other / Unclassified"}.groupingBy{it.sender}.eachCount().entries.sortedByDescending{it.value}.map{it.key to it.value}
        val bursts=mutableListOf<SmsBurst>()
        var start=0
        while(start<events.size){
            var end=start
            while(end+1<events.size){val a=events[end].timestamp;val b=events[end+1].timestamp;if(a==null||b==null||b-a>burstGapMinutes*60000L)break;end++}
            val group=events.subList(start,end+1)
            if(group.size>=burstMinimum)bursts+=SmsBurst(group.first().record.dateTime,group.last().record.dateTime,group.size,group.map{it.sender}.distinct().size,group.map{it.category}.distinct())
            start=end+1
        }
        return SmsSummary(events,categories,senders,unknown,bursts)
    }

    fun nearbyContext(allRows:List<CdrRecord>,event:SmsIntelEvent,minutes:Int):List<CdrRecord>{
        val center=event.timestamp?:return emptyList()
        val window=minutes*60000L
        return allRows.filter{r->parseCdrTime(r.dateTime)?.let{kotlin.math.abs(it-center)<=window}==true}.sortedBy{parseCdrTime(it.dateTime)}
    }

    private fun normalizedSmsType(value:String):Boolean=value.contains("sms",true)
    private fun brandToken(sender:String):String=sender.uppercase().substringAfterLast("-").replace(Regex("[^A-Z0-9]"),"")
    private fun isUnusual(timestamp:Long?):Boolean{if(timestamp==null)return false;val h=java.util.Calendar.getInstance().apply{timeInMillis=timestamp}.get(java.util.Calendar.HOUR_OF_DAY);return h>=22||h<6}
}
