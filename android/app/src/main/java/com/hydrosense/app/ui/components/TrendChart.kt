package com.hydrosense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrosense.app.data.AnomalyPoint
import com.hydrosense.app.data.SeriesPoint
import com.hydrosense.app.ui.theme.LocalStatusColors
import com.hydrosense.app.ui.theme.Manrope
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * TDS over time. Citizen view shades the site's usual range; the analytics view
 * draws the baseline and marks points outside the usual range. Drag to read values.
 */
@Composable
fun TrendChart(
    points: List<SeriesPoint>, range: String, baseline: Double?, modifier: Modifier = Modifier,
    usualBand: Boolean = true, anomalies: List<AnomalyPoint> = emptyList(), height: Dp = 210.dp,
) {
    val status = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Manrope, fontSize = 10.sp, color = muted)
    val data = remember(points) { points.mapNotNull { p -> parseInstant(p.t)?.toEpochMilli()?.let { it to p.tds } } }
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    if (data.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Not enough readings in this period yet.", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        return
    }
    val values = data.map { it.second } + listOfNotNull(baseline?.times(0.9), baseline?.times(1.1))
    val lo0 = values.min(); val hi0 = values.max()
    val pad = maxOf((hi0 - lo0) * 0.15, 8.0)
    val lo = maxOf(0.0, floor((lo0 - pad) / 10) * 10); val hi = ceil((hi0 + pad) / 10) * 10
    val t0 = data.first().first; val t1 = data.last().first
    val fmt = DateTimeFormatter.ofPattern(if (range == "24h") "h a" else "d MMM").withZone(ZoneId.systemDefault())
    val tipFmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a").withZone(ZoneId.systemDefault())
    val description = "Chart of dissolved solids. Latest ${data.last().second.roundToInt()} ppm" + (baseline?.let { ", usual level ${it.roundToInt()} ppm." } ?: ".")

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { o -> selected = nearest(o.x, size.width.toFloat(), 40.dp.toPx(), data, t0, t1) }
                }
                .pointerInput(points) {
                    // Horizontal drags scrub the chart; vertical drags are left to the page so it still scrolls.
                    detectHorizontalDragGestures { change, _ -> selected = nearest(change.position.x, size.width.toFloat(), 40.dp.toPx(), data, t0, t1) }
                },
        ) {
            val left = 40.dp.toPx(); val bottom = 22.dp.toPx(); val top = 8.dp.toPx()
            val w = size.width - left - 6.dp.toPx(); val h = size.height - bottom - top
            fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0).toFloat() * w
            fun y(v: Double) = top + ((hi - v) / (hi - lo)).toFloat() * h

            // gridlines + y labels
            for (i in 0..3) {
                val v = lo + (hi - lo) * i / 3
                drawLine(status.chartGrid, Offset(left, y(v)), Offset(left + w, y(v)), 1.dp.toPx())
                val text = measurer.measure(v.roundToInt().toString(), label)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y(v) - text.size.height / 2))
            }
            // usual range / baseline
            if (baseline != null) {
                if (usualBand) {
                    drawRect(status.ok.copy(alpha = 0.13f), Offset(left, y(baseline * 1.1)), Size(w, y(baseline * 0.9) - y(baseline * 1.1)))
                } else {
                    drawLine(muted, Offset(left, y(baseline)), Offset(left + w, y(baseline)), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
            // area + line
            val line = Path().apply { data.forEachIndexed { i, (t, v) -> if (i == 0) moveTo(x(t), y(v)) else lineTo(x(t), y(v)) } }
            val area = Path().apply { addPath(line); lineTo(x(t1), top + h); lineTo(x(t0), top + h); close() }
            drawPath(area, status.chartLine.copy(alpha = 0.12f))
            drawPath(line, status.chartLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // points outside the usual range (analytics view)
            anomalies.takeLast(60).forEach { a ->
                val t = parseInstant(a.t)?.toEpochMilli() ?: return@forEach
                if (t in t0..t1) { drawCircle(surface, 5.dp.toPx(), Offset(x(t), y(a.tds))); drawCircle(status.change, 3.5.dp.toPx(), Offset(x(t), y(a.tds))) }
            }
            // x labels
            listOf(t0, (t0 + t1) / 2, t1).forEachIndexed { i, t ->
                val text = measurer.measure(fmt.format(java.time.Instant.ofEpochMilli(t)), label)
                val tx = when (i) { 0 -> left; 1 -> left + w / 2 - text.size.width / 2; else -> left + w - text.size.width }
                drawText(text, topLeft = Offset(tx, top + h + 6.dp.toPx()))
            }
            // crosshair
            selected?.let { i ->
                val (t, v) = data[i]
                drawLine(muted.copy(alpha = 0.6f), Offset(x(t), top), Offset(x(t), top + h), 1.dp.toPx())
                drawCircle(surface, 7.dp.toPx(), Offset(x(t), y(v))); drawCircle(status.chartLine, 5.dp.toPx(), Offset(x(t), y(v)))
            }
            if (selected == null) { // end marker
                drawCircle(surface, 6.dp.toPx(), Offset(x(t1), y(data.last().second))); drawCircle(status.chartLine, 4.dp.toPx(), Offset(x(t1), y(data.last().second)))
            }
        }
        val i = selected
        val caption = if (i != null) {
            val (t, v) = data[i]
            val diff = baseline?.let { (v - it) / it * 100 }
            "${v.roundToInt()} ppm" + (diff?.takeIf { kotlin.math.abs(it) >= 3 }?.let { " · ${kotlin.math.abs(it).roundToInt()}% ${if (it > 0) "above" else "below"} usual" } ?: "") +
                " · " + tipFmt.format(java.time.Instant.ofEpochMilli(t))
        } else if (usualBand && baseline != null) "Green band: what's usual here. Touch the chart to read values."
        else if (baseline != null) "Dashed line: historical baseline (${baseline.roundToInt()} ppm). Orange dots: outside usual range."
        else "Touch the chart to read values."
        Text(caption, style = MaterialTheme.typography.bodySmall, color = if (i != null) onSurface else muted)
    }
}

private fun nearest(px: Float, width: Float, left: Float, data: List<Pair<Long, Double>>, t0: Long, t1: Long): Int {
    val frac = ((px - left) / (width - left)).coerceIn(0f, 1f)
    val target = t0 + ((t1 - t0) * frac).toLong()
    return data.indices.minByOrNull { kotlin.math.abs(data[it].first - target) } ?: 0
}

/** Tiny trend line for cards: shape only. */
@Composable
fun Sparkline(points: List<SeriesPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val ys = points.map { it.tds }; val lo = ys.min(); val span = (ys.max() - lo).takeIf { it > 0 } ?: 1.0
        val path = Path()
        ys.forEachIndexed { i, v ->
            val x = i.toFloat() / (ys.size - 1) * (size.width - 8f) + 4f
            val y = size.height - 4f - ((v - lo) / span).toFloat() * (size.height - 8f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
