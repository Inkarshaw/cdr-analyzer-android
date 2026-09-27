package ink.clearexams.cdranalyzer

import java.text.SimpleDateFormat
import java.util.Locale

data class CaseTimeFilter(val fromText:String="",val toText:String="") {
    val active:Boolean get()=fromText.isNotBlank()||toText.isNotBlank()
}

object CaseTimeFiltering {
    private val patterns=listOf(
        "dd-MM-yyyy HH:mm:ss","dd/MM/yyyy HH:mm:ss","yyyy-MM-dd HH:mm:ss",
        "dd-MM-yyyy HH:mm","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm",
        "dd-MM-yyyy","dd/MM/yyyy","yyyy-MM-dd","MM/dd/yyyy HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss"
    )

    fun parse(value:String,endOfDay:Boolean=false):Long? {
        if(value.isBlank())return null
        for(pattern in patterns) try {
            val f=SimpleDateFormat(pattern,Locale.US);f.isLenient=false
            val parsed=f.parse(value)?.time ?: continue
            if(endOfDay && !pattern.contains("HH")) return parsed+86_399_999L
            return parsed
        } catch(_:Exception){}
        return value.toLongOrNull()?.let{if(it<100000000000L)it*1000 else it}
    }

    fun valid(filter:CaseTimeFilter):Boolean =
        (filter.fromText.isBlank()||parse(filter.fromText)!=null) &&
        (filter.toText.isBlank()||parse(filter.toText,true)!=null) &&
        run { val a=parse(filter.fromText); val b=parse(filter.toText,true); a==null||b==null||a<=b }

    fun apply(workspace:CaseWorkspace,filter:CaseTimeFilter):CaseWorkspace {
        if(!filter.active||!valid(filter))return workspace
        val from=parse(filter.fromText)
        val to=parse(filter.toText,true)
        return workspace.copy(datasets=workspace.datasets.map{d->d.copy(records=d.records.filter{r->
            val t=parse(r.dateTime) ?: return@filter false
            (from==null||t>=from)&&(to==null||t<=to)
        })})
    }

    fun count(workspace:CaseWorkspace)=workspace.datasets.sumOf{it.records.size}
}
