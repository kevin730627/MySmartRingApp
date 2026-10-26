package com.mysmartring.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.CardRange
import com.mysmartring.data.ReadingEntity
import com.mysmartring.data.StepsAggregation
import com.mysmartring.ring.Metric
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_DAY = DateTimeFormatter.ofPattern("M/d")
private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * 今日步數圓環：中央顯示目前步數，圓環依「今日步數 ÷ 每日目標」填滿，達標後整圈畫滿並換色。
 * 今天所有半小時區間加總；戒指只在區間結束後才回報，最新半小時內的步數要等下一次同步。
 */
@Composable
internal fun StepsTodayCard(viewModel: DashboardViewModel, onClick: () -> Unit) {
    val today by remember { viewModel.today(Metric.STEPS) }.collectAsStateWithLifecycle(emptyList())
    val goal by viewModel.stepGoal.collectAsStateWithLifecycle()
    val steps = StepsAggregation.total(today)
    val progress = StepsAggregation.progress(steps, goal)
    val animated by animateFloatAsState(progress, animationSpec = tween(durationMillis = 700), label = "stepsProgress")
    val ringColor = if (progress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("步數", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    if (animated > 0f) {
                        drawArc(ringColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%,d".format(steps), style = MaterialTheme.typography.displaySmall)
                    Text("步數", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val last = today.maxOfOrNull { it.measuredAtMillis }
            Text(
                "目標 %,d 步・%s".format(goal, if (progress >= 1f) "已達標" else "完成 ${(progress * 100).toInt()}%"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (last == null) "今天還沒有資料" else "資料到 ${CLOCK_TIME.format(Instant.ofEpochMilli(last + HALF_HOUR_MILLIS))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StepsTrendCard(viewModel: DashboardViewModel, range: CardRange, onClick: () -> Unit) {
    val readings by remember(range) { viewModel.lastDays(Metric.STEPS, range.days) }.collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("步數・${if (range == CardRange.DAY) "今日" else "近 ${range.days} 天"}", style = MaterialTheme.typography.titleMedium)
            StepsSection(readings, viewModel.rangeStart(range.days), range.days)
        }
    }
}

/** 步數長條圖與統計；沒有資料時只顯示提示 */
@Composable
internal fun StepsSection(readings: List<ReadingEntity>, startMillis: Long, days: Int) {
    if (readings.isEmpty()) {
        Text("這段期間沒有資料", style = MaterialTheme.typography.bodyMedium)
        return
    }
    StepsChart(readings, startMillis, days)
    val total = StepsAggregation.total(readings)
    Text(
        if (days == 1) "合計 %,d 步".format(total) else "合計 %,d 步　平均每天 %,d 步".format(total, total / days),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** 一天看每小時，多天看每日；沒有資料的小時或日子補 0 */
@Composable
private fun StepsChart(readings: List<ReadingEntity>, startMillis: Long, days: Int) {
    val zone = ZoneId.systemDefault()
    val firstDay = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val values = if (days == 1) StepsAggregation.byHour(readings, zone) else StepsAggregation.byDay(readings, zone, firstDay, days)
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) { producer.runTransaction { columnSeries { series(values) } } }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0)),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, x, _ ->
                    if (days == 1) "%02d".format(x.toInt()) else MONTH_DAY.format(firstDay.plusDays(x.toLong()))
                },
            ),
        ),
        modelProducer = producer,
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
        modifier = Modifier.fillMaxWidth().height(180.dp),
    )
}

private const val HALF_HOUR_MILLIS = 30 * 60 * 1000L
