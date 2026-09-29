package ink.clearexams.cdranalyzer

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object CdrPrintReport {
    fun print(context: Context, rows: List<CdrRecord>, summary: Summary) {
        val topContacts = rows.map { it.otherParty }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20)
        val topTowers = rows.filter { it.cellId.isNotBlank() }.groupingBy { listOf(it.lac,it.cellId).filter(String::isNotBlank).joinToString("/") }.eachCount().entries.sortedByDescending { it.value }.take(20)
        fun esc(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace(""","&quot;")
        val html = buildString {
            append("<html><head><meta charset=\"utf-8\"><style>body{font-family:sans-serif;padding:24px;color:#111}h1{font-size:22px}h2{margin-top:22px;font-size:16px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:6px;text-align:left;font-size:11px}.k{display:inline-block;margin:4px 12px 4px 0;font-weight:700}</style></head><body>")
            append("<h1>CDR Case Analyzer</h1>")
            append("<div><span class=\"k\">Records: ${summary.records}</span><span class=\"k\">Contacts: ${summary.contacts}</span><span class=\"k\">Incoming: ${summary.incoming}</span><span class=\"k\">Outgoing: ${summary.outgoing}</span></div>")
            append("<h2>Top contacts</h2><table><tr><th>Contact</th><th>Records</th></tr>")
            topContacts.forEach { append("<tr><td>${esc(it.key)}</td><td>${it.value}</td></tr>") }
            append("</table><h2>Top towers</h2><table><tr><th>LAC / Cell</th><th>Records</th></tr>")
            topTowers.forEach { append("<tr><td>${esc(it.key)}</td><td>${it.value}</td></tr>") }
            append("</table><h2>Filtered records</h2><table><tr><th>Date / Time</th><th>A Party</th><th>B Party</th><th>Type</th><th>Duration</th><th>IMEI</th><th>IMSI</th><th>Tower</th></tr>")
            rows.take(2500).forEach { r ->
                val tower = if (r.cellId.isBlank()) "" else listOf(r.lac,r.cellId).filter(String::isNotBlank).joinToString("/")
                append("<tr><td>${esc(r.dateTime)}</td><td>${esc(r.number)}</td><td>${esc(r.otherParty)}</td><td>${esc(r.direction)}</td><td>${esc(r.duration)}</td><td>${esc(r.imei)}</td><td>${esc(r.imsi)}</td><td>${esc(tower)}</td></tr>")
            }
            append("</table><p style=\"font-size:10px;color:#555\">Automated summaries describe CDR metadata and should be verified against source records and other evidence.</p></body></html>")
        }
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter("CDR Case Analyzer")
                manager.print("CDR Case Analyzer", adapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
