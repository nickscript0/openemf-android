package com.openemf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openemf.ui.model.PlotBounds
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PlotSample(val key: String, val time: Instant, val value: Double)
fun formatSignalValue(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

@Composable
fun TimeScatterPlot(
    samples: List<PlotSample>,
    bounds: PlotBounds,
    unit: String,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    timeAxisLabel: String = "Observation time",
    autoScaled: Boolean = true
) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisFormat = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault())
    Column(modifier.fillMaxWidth()) {
        Text("${if (autoScaled) "Auto-scaled" else "Fixed scale"} · $unit", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            Column(Modifier.width(60.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(bounds.high, (bounds.high + bounds.low) / 2, bounds.low).forEach {
                    Text(formatSignalValue(it), style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
                contentDescription = "${samples.size} samples. Vertical axis ${bounds.low} to ${bounds.high} $unit. " +
                    "Horizontal axis ${axisFormat.format(bounds.start)} to ${axisFormat.format(bounds.end)}. Use Older and Newer to inspect samples."
            }.pointerInput(samples, bounds) {
                detectTapGestures { tap ->
                    val pad = 8.dp.toPx()
                    val x = ((tap.x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val y = ((tap.y - pad) / (size.height - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    samples.minByOrNull {
                        val dx = bounds.x(it.time) - x
                        val dy = bounds.y(it.value) - y
                        dx * dx + dy * dy
                    }?.let { onSelect(it.key) }
                }
            }) {
                val pad = 8.dp.toPx()
                val w = (size.width - pad * 2).coerceAtLeast(1f)
                val h = (size.height - pad * 2).coerceAtLeast(1f)
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    drawLine(grid, Offset(pad, pad + h * fraction), Offset(pad + w, pad + h * fraction), 1.dp.toPx())
                }
                samples.forEach {
                    val position = Offset(pad + w * bounds.x(it.time), pad + h * bounds.y(it.value))
                    drawCircle(color.copy(alpha = if (it.key == selectedKey) 1f else 0.65f),
                        if (it.key == selectedKey) 5.dp.toPx() else 3.dp.toPx(), position)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 60.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(axisFormat.format(bounds.start), style = MaterialTheme.typography.labelSmall)
            Text(axisFormat.format(bounds.end), style = MaterialTheme.typography.labelSmall)
        }
        Text("$timeAxisLabel · ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.labelSmall)
    }
}
