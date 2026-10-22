package com.openemf.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*
import com.openemf.data.database.MeasurementEntity
import com.openemf.ui.theme.BluetoothColor
import com.openemf.ui.theme.CellularColor
import com.openemf.ui.theme.WiFiColor
import com.openemf.ui.theme.getEScoreColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Time period for filtering statistics.
 */
enum class TimePeriod(val label: String, val days: Int) {
    DAY("Day", 1),
    WEEK("Week", 7),
    MONTH("Month", 30)
}

/**
 * Statistics screen showing measurement history and trends with time period tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    measurementHistory: List<MeasurementEntity>,
    historyError: String?,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf(TimePeriod.DAY) }
    var expandedDay by remember { mutableStateOf<LocalDate?>(null) }

    // Filter measurements based on selected period
    val now = Instant.now()
    val cutoff = now.minus(selectedPeriod.days.toLong(), ChronoUnit.DAYS)
    val filteredMeasurements = remember(measurementHistory, selectedPeriod) {
        measurementHistory.filter { it.timestamp.isAfter(cutoff) && it.indexAvailable == true }
    }

    // Group measurements by day for drill-down
    val measurementsByDay = remember(filteredMeasurements) {
        filteredMeasurements.groupBy { measurement ->
            measurement.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "Stored captures and signal histories")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            historyError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            // Time period tabs
            TimePeriodTabs(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = {
                    selectedPeriod = it
                    expandedDay = null
                }
            )

            // Content
            if (filteredMeasurements.isEmpty()) {
                EmptyStatisticsState(period = selectedPeriod)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary cards
                    item {
                        SummarySection(
                            history = filteredMeasurements.drop(1),
                            current = filteredMeasurements.firstOrNull(),
                            periodLabel = selectedPeriod.label
                        )
                    }

                    // Trend chart
                    if (filteredMeasurements.size >= 2) {
                        item {
                            TrendChartCard(
                                measurements = filteredMeasurements,
                                periodLabel = selectedPeriod.label
                            )
                        }
                    }

                    // Source breakdown
                    item {
                        SourceTrendCard(
                            history = filteredMeasurements.drop(1),
                            current = filteredMeasurements.firstOrNull()
                        )
                    }

                    // Daily breakdown with drill-down
                    item {
                        Text(
                            text = "History by Day",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    measurementsByDay.forEach { (date, dayMeasurements) ->
                        item(key = date) {
                            DayCard(
                                date = date,
                                measurements = dayMeasurements,
                                isExpanded = expandedDay == date,
                                onToggle = {
                                    expandedDay = if (expandedDay == date) null else date
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePeriodTabs(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = TimePeriod.entries.indexOf(selectedPeriod)
    ) {
        TimePeriod.entries.forEach { period ->
            Tab(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                text = { Text(period.label) },
                icon = {
                    Icon(
                        imageVector = when (period) {
                            TimePeriod.DAY -> Icons.Default.Today
                            TimePeriod.WEEK -> Icons.Default.DateRange
                            TimePeriod.MONTH -> Icons.Default.CalendarMonth
                        },
                        contentDescription = period.label
                    )
                }
            )
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    measurements: List<MeasurementEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val scores = measurements.map { it.eScore }
    val avgScore = scores.average().toInt()
    val minScore = scores.minOrNull() ?: 0
    val maxScore = scores.maxOrNull() ?: 0

    val avgColor = getEScoreColor(avgScore)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${measurements.size} measurement${if (measurements.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Stats summary
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$avgScore",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = avgColor
                        )
                        Text(
                            text = "avg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$minScore–$maxScore",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "range",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    // Mini chart for the day
                    if (measurements.size >= 2) {
                        Text(
                            text = "E-Score throughout the day",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposureLineChart(
                            measurements = measurements.sortedBy { it.timestamp },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Individual measurements
                    Text(
                        text = "Readings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    measurements.sortedByDescending { it.timestamp }.forEach { measurement ->
                        MeasurementRow(
                            measurement = measurement,
                            isCurrent = false,
                            showDate = false
                        )
                        if (measurement != measurements.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySection(
    history: List<MeasurementEntity>,
    current: MeasurementEntity?,
    periodLabel: String = ""
) {
    val allMeasurements = if (current != null) history + current else history
    val scores = allMeasurements.map { it.eScore }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current score
        SummaryCard(
            title = "Latest saved",
            value = current?.eScore?.toString() ?: "—",
            subtitle = current?.exposureLevel ?: "N/A",
            color = when (current?.exposureLevel) {
                "LOW" -> Color(0xFF22C55E)
                "MODERATE" -> Color(0xFFF59E0B)
                "HIGH" -> Color(0xFFEF4444)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )

        // Average score
        SummaryCard(
            title = "Average",
            value = if (scores.isNotEmpty()) "${scores.average().toInt()}" else "—",
            subtitle = "${allMeasurements.size} readings",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        // Peak score
        SummaryCard(
            title = "Peak",
            value = if (scores.isNotEmpty()) "${scores.maxOrNull()}" else "—",
            subtitle = "highest",
            color = Color(0xFFEF4444),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendChartCard(
    measurements: List<MeasurementEntity>,
    periodLabel: String = ""
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (periodLabel.isNotEmpty()) "E-Score Trend ($periodLabel)" else "E-Score Trend",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Simple line chart
            ExposureLineChart(
                measurements = measurements,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            // Legend with 6-level scale
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = Color(0xFF10B981), label = "0-10")
                LegendItem(color = Color(0xFF22C55E), label = "11-25")
                LegendItem(color = Color(0xFFEAB308), label = "26-50")
                LegendItem(color = Color(0xFFF97316), label = "51-75")
                LegendItem(color = Color(0xFFEF4444), label = "76-90")
                LegendItem(color = Color(0xFFDC2626), label = "90+")
            }
        }
    }
}

@Composable
private fun ExposureLineChart(
    measurements: List<MeasurementEntity>,
    modifier: Modifier = Modifier
) {
    val scores = measurements.map { it.eScore.toFloat() }
    val lineColor = MaterialTheme.colorScheme.primary

    // Zone colors with 6-level scale
    val excellentZoneColor = Color(0xFF10B981).copy(alpha = 0.1f)  // 0-10
    val goodZoneColor = Color(0xFF22C55E).copy(alpha = 0.1f)       // 11-25
    val moderateZoneColor = Color(0xFFEAB308).copy(alpha = 0.1f)   // 26-50
    val elevatedZoneColor = Color(0xFFF97316).copy(alpha = 0.1f)   // 51-75
    val highZoneColor = Color(0xFFEF4444).copy(alpha = 0.1f)       // 76-90
    val veryHighZoneColor = Color(0xFFDC2626).copy(alpha = 0.1f)   // 90+

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 8.dp.toPx()

        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        // Draw zone backgrounds (6 levels)
        val threshold10 = chartHeight * (1 - 10f / 100f)
        val threshold25 = chartHeight * (1 - 25f / 100f)
        val threshold50 = chartHeight * (1 - 50f / 100f)
        val threshold75 = chartHeight * (1 - 75f / 100f)
        val threshold90 = chartHeight * (1 - 90f / 100f)

        // Very High zone (top, 90+)
        drawRect(
            color = veryHighZoneColor,
            topLeft = Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(chartWidth, threshold90)
        )

        // High zone (76-90)
        drawRect(
            color = highZoneColor,
            topLeft = Offset(padding, padding + threshold90),
            size = androidx.compose.ui.geometry.Size(chartWidth, threshold75 - threshold90)
        )

        // Elevated zone (51-75)
        drawRect(
            color = elevatedZoneColor,
            topLeft = Offset(padding, padding + threshold75),
            size = androidx.compose.ui.geometry.Size(chartWidth, threshold50 - threshold75)
        )

        // Moderate zone (26-50)
        drawRect(
            color = moderateZoneColor,
            topLeft = Offset(padding, padding + threshold50),
            size = androidx.compose.ui.geometry.Size(chartWidth, threshold25 - threshold50)
        )

        // Good zone (11-25)
        drawRect(
            color = goodZoneColor,
            topLeft = Offset(padding, padding + threshold25),
            size = androidx.compose.ui.geometry.Size(chartWidth, threshold10 - threshold25)
        )

        // Excellent zone (bottom, 0-10)
        drawRect(
            color = excellentZoneColor,
            topLeft = Offset(padding, padding + threshold10),
            size = androidx.compose.ui.geometry.Size(chartWidth, chartHeight - threshold10)
        )

        // Draw key threshold lines (at major boundaries)
        drawLine(
            color = Color(0xFFEAB308).copy(alpha = 0.5f),
            start = Offset(padding, padding + threshold50),
            end = Offset(width - padding, padding + threshold50),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFFEF4444).copy(alpha = 0.5f),
            start = Offset(padding, padding + threshold75),
            end = Offset(width - padding, padding + threshold75),
            strokeWidth = 1.dp.toPx()
        )

        if (scores.size < 2) return@Canvas

        // Draw line chart
        val path = Path()
        val stepX = chartWidth / (scores.size - 1)

        scores.forEachIndexed { index, score ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1 - score / 100f)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw points with color based on 6-level thresholds
        scores.forEachIndexed { index, score ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1 - score / 100f)
            val pointColor = getEScoreColor(score.toInt())

            drawCircle(
                color = pointColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SourceTrendCard(
    history: List<MeasurementEntity>,
    current: MeasurementEntity?
) {
    val allMeasurements = if (current != null) history + current else history

    // Calculate average contribution by source
    val avgWifi = allMeasurements
        .map { it.wifiPercent }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toInt() ?: 0

    val avgBluetooth = allMeasurements
        .map { it.bluetoothPercent }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toInt() ?: 0

    val avgCellular = allMeasurements
        .map { it.cellularPercent }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toInt() ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Average Source Contribution",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            SourceContributionBar(
                label = "Wi-Fi",
                percentage = avgWifi,
                color = WiFiColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            SourceContributionBar(
                label = "Bluetooth",
                percentage = avgBluetooth,
                color = BluetoothColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            SourceContributionBar(
                label = "Cellular",
                percentage = avgCellular,
                color = CellularColor
            )
        }
    }
}

@Composable
private fun SourceContributionBar(
    label: String,
    percentage: Int,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .background(color)
            )
        }
    }
}

@Composable
private fun RecentMeasurementsCard(
    history: List<MeasurementEntity>,
    current: MeasurementEntity?
) {
    val allMeasurements = (if (current != null) listOf(current) + history else history)
        .take(10) // Show last 10

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent Measurements",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (allMeasurements.isEmpty()) {
                Text(
                    text = "No scored captures yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                allMeasurements.forEachIndexed { index, measurement ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    MeasurementRow(measurement = measurement, isCurrent = index == 0 && current != null)
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: MeasurementEntity,
    isCurrent: Boolean,
    showDate: Boolean = true
) {
    val scoreColor = when (measurement.exposureLevel) {
        "LOW" -> Color(0xFF22C55E)
        "MODERATE" -> Color(0xFFF59E0B)
        "HIGH" -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeFormat = if (showDate) "MMM d, HH:mm" else "HH:mm"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = measurement.timestamp.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(timeFormat)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "${measurement.wifiCount + measurement.bluetoothCount + measurement.cellularCount} sources",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "${measurement.eScore}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scoreColor
        )
    }
}

@Composable
private fun EmptyStatisticsState(period: TimePeriod = TimePeriod.DAY) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when (period) {
                        TimePeriod.DAY -> Icons.Default.Today
                        TimePeriod.WEEK -> Icons.Default.DateRange
                        TimePeriod.MONTH -> Icons.Default.CalendarMonth
                    },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Data for ${period.label}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (period) {
                        TimePeriod.DAY -> "No scored captures in the past day. Raw captures remain available in History."
                        TimePeriod.WEEK -> "No scored captures in the past week. Take regular readings to see trends."
                        TimePeriod.MONTH -> "No scored captures in the past month. Start measuring to build your history."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
