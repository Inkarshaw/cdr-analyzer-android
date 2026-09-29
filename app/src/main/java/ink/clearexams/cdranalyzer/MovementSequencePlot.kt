package ink.clearexams.cdranalyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun TowerSequencePlotCard(points:List<GeoPoint>){
    if(points.isEmpty())return
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.secondary
    val outline=MaterialTheme.colorScheme.outline
    val minLat=points.minOf{it.latitude};val maxLat=points.maxOf{it.latitude};val minLon=points.minOf{it.longitude};val maxLon=points.maxOf{it.longitude}
    Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Tower Sequence Plot",style=MaterialTheme.typography.titleSmall)
            Text("${points.size} chronological mapped tower observation(s)",style=MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(230.dp)){
                val pad=18.dp.toPx();val usableW=max(1f,size.width-2*pad);val usableH=max(1f,size.height-2*pad)
                fun pos(p:GeoPoint):Offset{val x=if(maxLon==minLon)0.5f else ((p.longitude-minLon)/(maxLon-minLon)).toFloat();val y=if(maxLat==minLat)0.5f else ((p.latitude-minLat)/(maxLat-minLat)).toFloat();return Offset(pad+x*usableW,pad+(1f-y)*usableH)}
                if(points.size>1){for(i in 1 until points.size)drawLine(outline,pos(points[i-1]),pos(points[i]),strokeWidth=3f)}
                points.forEachIndexed{i,p->drawCircle(if(i==0||i==points.lastIndex)secondary else primary,radius=if(i==0||i==points.lastIndex)7f else 4.5f,center=pos(p))}
            }
            Text("Start: ${points.first().tower} • ${points.first().at.ifBlank{"Time unavailable"}}",style=MaterialTheme.typography.labelSmall)
            if(points.size>1)Text("End: ${points.last().tower} • ${points.last().at.ifBlank{"Time unavailable"}}",style=MaterialTheme.typography.labelSmall)
            Text("Connected lines show recorded tower-coordinate order only. They are not the actual road travelled or precise handset path.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
